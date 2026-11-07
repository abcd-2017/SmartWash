/**
 * 声景混音器音频引擎（零音频资产）— 对齐安卓 SoundscapeEngine.kt。
 *
 * 合成：十路声道逐采样程序化生成（白/粉/棕噪音为算法噪声；雨声 = 滤波噪声 +
 * 随机雨滴脉冲池；风声 = LFO 调制噪声；海浪 = 慢周期波包络；篝火 = 随机噼啪脉冲串；
 * 翻书/键盘/远处交谈为合成近似的室内纹理），母线经 tanh 软限幅防削顶。
 *
 * 线程模型：ArkTS 单线程事件循环，以系统时钟为基准的 lookahead 调度
 * （对齐 DrumEngine 的写法）：调度线程每 [SCHEDULER_MS] 醒一次，把「当前时刻 +
 * LOOKAHEAD_MS」窗口内欠账的采样渲染成 16-bit PCM 写入 AudioRenderer（写缓冲
 * 自然节流）。volumes/enabled 以不可变快照发布（copy-on-write）；Android 端的
 * 代际计数防「旧渲染循环残留」在单线程事件循环下天然不存在，stop 即清定时器。
 *
 * 生命周期：进入页面不自动播放；[release] 必须在页面销毁时调用。
 * 音频设备不可用（模拟器等）时 [start] 返回 false，页面保持停止态不崩溃。
 */

import audio from '@ohos.multimedia.audio'

/** 声道枚举（顺序即混音台展示序与合成序，分三组：底噪 / 自然 / 室内） */
export enum SoundChannel {
  WHITE_NOISE = 0,
  PINK_NOISE = 1,
  BROWN_NOISE = 2,
  RAIN = 3,
  WIND = 4,
  OCEAN = 5,
  CAMPFIRE = 6,
  PAGE_FLIP = 7,
  KEYBOARD = 8,
  MURMUR = 9,
}

/** 声道总数 */
export const SOUND_CHANNEL_COUNT: number = 10

/** 默认音量（0..100），仅用于未显式设置的声道 */
export const SOUNDSCAPE_DEFAULT_VOLUME: number = 60

/** 采样率（Hz） */
const SAMPLE_RATE: number = 44100

/** 单采样步长（秒） */
const SAMPLE_DT: number = 1 / SAMPLE_RATE

/** 调度 lookahead（ms）：始终比墙钟多渲染这么多，防欠载 */
const LOOKAHEAD_MS: number = 150

/** 调度线程唤醒间隔（ms） */
const SCHEDULER_MS: number = 40

/** 单次渲染块帧数上限（≈23ms）：stop 响应延迟上限（对齐安卓 CHUNK_FRAMES） */
const CHUNK_FRAMES: number = 1024

/** 母线增益（配合 tanh 软限幅） */
const MASTER_GAIN: number = 1.4

// ==================== 单路增益（满幅 1.0 归一化，对齐安卓） ====================
const WHITE_GAIN: number = 0.5
const PINK_GAIN: number = 0.8
const BROWN_GAIN: number = 0.7
const RAIN_GAIN: number = 0.8
const WIND_GAIN: number = 0.9
const OCEAN_GAIN: number = 0.9
const FIRE_GAIN: number = 0.8
const PAGE_GAIN: number = 0.9
const KEY_GAIN: number = 0.9
const MURMUR_GAIN: number = 0.9

// ==================== 噪声参数（对齐安卓） ====================
const PINK_SCALE: number = 0.22
const BROWN_SCALE: number = 3.5

// ==================== 雨声参数（对齐安卓） ====================
const RAIN_LP_ALPHA: number = 0.12
const RAIN_HP_ALPHA: number = 0.35
const RAIN_DROP_RATE: number = 40

// ==================== 风声参数（对齐安卓） ====================
const WIND_LP_MIN: number = 0.02
const WIND_LP_MAX: number = 0.05
const WIND_LFO_HZ: number = 0.07
const WIND_GUST_HZ: number = 0.043

// ==================== 海浪参数（对齐安卓，波峰整形为平方曲线） ====================
const OCEAN_LP_ALPHA: number = 0.02
const OCEAN_HP_ALPHA: number = 0.05
const OCEAN_WAVE_HZ: number = 1 / 12

// ==================== 篝火参数（对齐安卓） ====================
const FIRE_LP_ALPHA: number = 0.01
const FIRE_CRACKLE_RATE: number = 90
const FIRE_POP_RATE: number = 6

// ==================== 翻书参数（对齐安卓） ====================
const PAGE_LP_ALPHA: number = 0.15
const PAGE_SWISH_RATE: number = 18

// ==================== 键盘参数（对齐安卓） ====================
const KEY_TAP_RATE: number = 66

// ==================== 远处交谈参数（对齐安卓） ====================
const MURMUR_LP_ALPHA: number = 0.03
const MURMUR_ENV_SLEW: number = 0.002
const MURMUR_VOICE1_HZ: number = 3.2
const MURMUR_VOICE2_HZ: number = 4.1

/** 可复现随机数（xorshift32，对齐安卓 Random(0x5EED) 的确定性意图） */
class LabRandom {
  private seedValue: number

  constructor(seed: number) {
    this.seedValue = seed >>> 0
  }

  /** 返回 [0, 1) 均匀分布 */
  next(): number {
    let x: number = this.seedValue
    x = (x ^ ((x << 13) >>> 0)) >>> 0
    x = (x ^ (x >>> 17)) >>> 0
    x = (x ^ ((x << 5) >>> 0)) >>> 0
    this.seedValue = x
    return x / 4294967296
  }
}

/** 单路脉冲触发参数（区间两端 + 噪声混合比，对齐安卓的 Kotlin Range 入参） */
interface PulseSpec {
  freqMin: number
  freqMax: number
  ampMin: number
  ampMax: number
  decayMin: number
  decayMax: number
  lifeMin: number
  lifeMax: number
  noiseMix: number
}

/** 单路脉冲池：固定容量、按需触发、指数衰减包络（雨滴 / 篝火 / 翻书 / 键盘共用） */
class PulsePool {
  private readonly sizeValue: number
  private readonly active: boolean[]
  private readonly age: number[]
  private readonly life: number[]
  private readonly freq: number[]
  private readonly amp: number[]
  private readonly decay: number[]
  private readonly noiseMix: number[]

  constructor(size: number) {
    this.sizeValue = size
    this.active = new Array<boolean>(size).fill(false)
    this.age = new Array<number>(size).fill(0)
    this.life = new Array<number>(size).fill(0)
    this.freq = new Array<number>(size).fill(0)
    this.amp = new Array<number>(size).fill(0)
    this.decay = new Array<number>(size).fill(0)
    this.noiseMix = new Array<number>(size).fill(0)
  }

  isActive(index: number): boolean {
    return this.active[index]
  }

  /** 触发一个脉冲；池满时静默丢弃（自然稀疏，对齐安卓） */
  trigger(rng: LabRandom, spec: PulseSpec): void {
    for (let i: number = 0; i < this.sizeValue; i++) {
      if (this.active[i]) {
        continue
      }
      this.active[i] = true
      this.age[i] = 0
      this.freq[i] = spec.freqMin + rng.next() * (spec.freqMax - spec.freqMin)
      this.amp[i] = spec.ampMin + rng.next() * (spec.ampMax - spec.ampMin)
      this.decay[i] = spec.decayMin + rng.next() * (spec.decayMax - spec.decayMin)
      this.life[i] = spec.lifeMin + rng.next() * (spec.lifeMax - spec.lifeMin)
      this.noiseMix[i] = spec.noiseMix
      return
    }
  }

  /** 单采样：指数衰减包络 ×（正弦音头 + 噪声）按 noiseMix 混合 */
  sample(index: number, rng: LabRandom): number {
    const envelope: number = this.amp[index] * Math.exp(-this.decay[index] * this.age[index])
    const tone: number = Math.sin(2 * Math.PI * this.freq[index] * this.age[index])
    const noise: number = rng.next() * 2 - 1
    return envelope * (tone * (1 - this.noiseMix[index]) + noise * this.noiseMix[index])
  }

  advance(index: number, dt: number): void {
    this.age[index] += dt
    if (this.age[index] >= this.life[index]) {
      this.active[index] = false
    }
  }
}

/** 音频生成器私有状态（仅调度回调读写，事件循环内无并发） */
class ChannelState {
  readonly rng: LabRandom = new LabRandom(0x5EED)
  /** 全局时间（秒），驱动慢速 LFO / 周期包络 */
  t: number = 0

  // 粉噪音三阶滤波状态（Paul Kellet 经济版）
  private pinkB0: number = 0
  private pinkB1: number = 0
  private pinkB2: number = 0

  // 棕噪音漏积分状态
  private brown: number = 0

  // 雨声滤波状态 + 雨滴池
  private rainLp: number = 0
  private rainHp: number = 0
  readonly rainDrops: PulsePool = new PulsePool(8)

  // 风声滤波状态
  private windLp: number = 0

  // 海浪滤波状态
  private oceanLp: number = 0
  private oceanHp: number = 0

  // 篝火滤波状态 + 脉冲池
  private fireLp: number = 0
  readonly fireCrackles: PulsePool = new PulsePool(10)

  // 翻书滤波状态 + 脉冲池
  private pageLp: number = 0
  readonly pageSwishes: PulsePool = new PulsePool(4)

  // 键盘脉冲池
  readonly keyTaps: PulsePool = new PulsePool(8)

  // 远处交谈：两路低通 + 音节包络
  private murmurLp1: number = 0
  private murmurLp2: number = 0
  private murmurEnv1: number = 0
  private murmurEnv2: number = 0
  private murmurTarget1: number = 0
  private murmurTarget2: number = 0
  private murmurNextChange1: number = 0
  private murmurNextChange2: number = 0

  renderWhite(volume: number, rng: LabRandom): number {
    return (rng.next() * 2 - 1) * volume * WHITE_GAIN
  }

  renderPink(volume: number, rng: LabRandom): number {
    const white: number = rng.next() * 2 - 1
    this.pinkB0 = 0.99765 * this.pinkB0 + white * 0.0990460
    this.pinkB1 = 0.96300 * this.pinkB1 + white * 0.2965164
    this.pinkB2 = 0.57000 * this.pinkB2 + white * 1.0526913
    const pink: number = (this.pinkB0 + this.pinkB1 + this.pinkB2 + white * 0.1848) * PINK_SCALE
    return pink * volume * PINK_GAIN
  }

  renderBrown(volume: number, rng: LabRandom): number {
    const white: number = rng.next() * 2 - 1
    this.brown = (this.brown + 0.02 * white) / 1.02
    return this.brown * BROWN_SCALE * volume * BROWN_GAIN
  }

  /** 雨声：低通 + 高通噪声做「雨幕底」，叠加随机雨滴脉冲（正弦 ping + 噪声，指数衰减） */
  renderRain(volume: number, rng: LabRandom, dt: number): number {
    const white: number = rng.next() * 2 - 1
    this.rainLp += RAIN_LP_ALPHA * (white - this.rainLp)
    this.rainHp += RAIN_HP_ALPHA * (white - this.rainHp)
    const base: number = this.rainLp * 0.5 + (white - this.rainHp) * 0.15
    if (rng.next() < RAIN_DROP_RATE / SAMPLE_RATE) {
      this.rainDrops.trigger(rng, {
        freqMin: 900, freqMax: 3200,
        ampMin: 0.04, ampMax: 0.16,
        decayMin: 60, decayMax: 140,
        lifeMin: 0.02, lifeMax: 0.06,
        noiseMix: 0.5,
      })
    }
    let drops: number = 0
    for (let i: number = 0; i < 8; i++) {
      if (this.rainDrops.isActive(i)) {
        drops += this.rainDrops.sample(i, rng)
        this.rainDrops.advance(i, dt)
      }
    }
    return (base + drops) * volume * RAIN_GAIN
  }

  /** 风声：一阶低通截止频率由慢速 LFO 调制，叠加更低频的阵风包络 */
  renderWind(volume: number, rng: LabRandom): number {
    const white: number = rng.next() * 2 - 1
    const lfo: number = 0.5 + 0.5 * Math.sin(2 * Math.PI * WIND_LFO_HZ * this.t)
    const alpha: number = WIND_LP_MIN + (WIND_LP_MAX - WIND_LP_MIN) * lfo
    this.windLp += alpha * (white - this.windLp)
    const gust: number = 0.5 + 0.5 * Math.sin(2 * Math.PI * WIND_GUST_HZ * this.t + 1.3)
    const amp: number = 0.35 + 0.5 * gust
    return this.windLp * amp * volume * WIND_GAIN
  }

  /** 海浪：低通噪声 + 慢速周期波包络（平方整形），波峰叠泡沫高频 */
  renderOcean(volume: number, rng: LabRandom): number {
    const white: number = rng.next() * 2 - 1
    this.oceanLp += OCEAN_LP_ALPHA * (white - this.oceanLp)
    this.oceanHp += OCEAN_HP_ALPHA * (white - this.oceanHp)
    const wave: number = Math.max(0, Math.sin(2 * Math.PI * OCEAN_WAVE_HZ * this.t))
    const shaped: number = wave * wave
    const foam: number = (white - this.oceanHp) * shaped * 0.25
    const body: number = this.oceanLp * (0.25 + 0.75 * shaped)
    return (body + foam) * volume * OCEAN_GAIN
  }

  /** 篝火：低通噪声做底，叠加随机噼啪脉冲串（高频短促）与偶发低频次爆裂 */
  renderCampfire(volume: number, rng: LabRandom, dt: number): number {
    const white: number = rng.next() * 2 - 1
    this.fireLp += FIRE_LP_ALPHA * (white - this.fireLp)
    if (rng.next() < FIRE_CRACKLE_RATE / SAMPLE_RATE) {
      this.fireCrackles.trigger(rng, {
        freqMin: 1200, freqMax: 4200,
        ampMin: 0.05, ampMax: 0.22,
        decayMin: 200, decayMax: 500,
        lifeMin: 0.004, lifeMax: 0.02,
        noiseMix: 0.7,
      })
    }
    if (rng.next() < FIRE_POP_RATE / SAMPLE_RATE) {
      this.fireCrackles.trigger(rng, {
        freqMin: 180, freqMax: 500,
        ampMin: 0.2, ampMax: 0.4,
        decayMin: 60, decayMax: 120,
        lifeMin: 0.03, lifeMax: 0.08,
        noiseMix: 0.9,
      })
    }
    let crackles: number = 0
    for (let i: number = 0; i < 10; i++) {
      if (this.fireCrackles.isActive(i)) {
        crackles += this.fireCrackles.sample(i, rng)
        this.fireCrackles.advance(i, dt)
      }
    }
    return (this.fireLp * 0.5 + crackles) * volume * FIRE_GAIN
  }

  /** 翻书：稀疏的带通噪声「哗啦」短脉冲（指数衰减，脉冲包络再叠一层低通噪声） */
  renderPageFlip(volume: number, rng: LabRandom, dt: number): number {
    if (rng.next() < PAGE_SWISH_RATE / SAMPLE_RATE) {
      this.pageSwishes.trigger(rng, {
        freqMin: 0, freqMax: 0,
        ampMin: 0.1, ampMax: 0.25,
        decayMin: 20, decayMax: 40,
        lifeMin: 0.08, lifeMax: 0.2,
        noiseMix: 1,
      })
    }
    let sum: number = 0
    for (let i: number = 0; i < 4; i++) {
      if (this.pageSwishes.isActive(i)) {
        const white: number = rng.next() * 2 - 1
        this.pageLp += PAGE_LP_ALPHA * (white - this.pageLp)
        sum += this.pageLp * this.pageSwishes.sample(i, rng)
        this.pageSwishes.advance(i, dt)
      }
    }
    return sum * volume * PAGE_GAIN
  }

  /** 键盘：随机短促敲击脉冲（高频正弦 + 噪声，极短衰减） */
  renderKeyboard(volume: number, rng: LabRandom, dt: number): number {
    if (rng.next() < KEY_TAP_RATE / SAMPLE_RATE) {
      this.keyTaps.trigger(rng, {
        freqMin: 1600, freqMax: 4200,
        ampMin: 0.08, ampMax: 0.2,
        decayMin: 350, decayMax: 800,
        lifeMin: 0.004, lifeMax: 0.012,
        noiseMix: 0.6,
      })
    }
    let sum: number = 0
    for (let i: number = 0; i < 8; i++) {
      if (this.keyTaps.isActive(i)) {
        sum += this.keyTaps.sample(i, rng)
        this.keyTaps.advance(i, dt)
      }
    }
    return sum * volume * KEY_GAIN
  }

  /** 远处交谈：两路低通噪声 + 随机音节包络（电报式幅度 + 基频颤动） */
  renderMurmur(volume: number, rng: LabRandom): number {
    const white: number = rng.next() * 2 - 1
    this.murmurLp1 += MURMUR_LP_ALPHA * (white - this.murmurLp1)
    this.murmurLp2 += MURMUR_LP_ALPHA * (white - this.murmurLp2)

    if (this.t >= this.murmurNextChange1) {
      this.murmurTarget1 = rng.next() * 0.35
      this.murmurNextChange1 = this.t + 0.12 + rng.next() * 0.3
    }
    if (this.t >= this.murmurNextChange2) {
      this.murmurTarget2 = rng.next() * 0.3
      this.murmurNextChange2 = this.t + 0.15 + rng.next() * 0.35
    }
    this.murmurEnv1 += (this.murmurTarget1 - this.murmurEnv1) * MURMUR_ENV_SLEW
    this.murmurEnv2 += (this.murmurTarget2 - this.murmurEnv2) * MURMUR_ENV_SLEW

    const voice1: number = 0.5 + 0.5 * Math.sin(2 * Math.PI * MURMUR_VOICE1_HZ * this.t)
    const voice2: number = 0.5 + 0.5 * Math.sin(2 * Math.PI * MURMUR_VOICE2_HZ * this.t + 1.7)
    const out: number = this.murmurLp1 * this.murmurEnv1 * voice1 +
      this.murmurLp2 * this.murmurEnv2 * voice2
    return out * volume * MURMUR_GAIN
  }
}

export class SoundscapeEngine {
  private renderer: audio.AudioRenderer | null = null
  private state: ChannelState | null = null
  private schedulerId: number = -1
  private playingValue: boolean = false
  private releasedValue: boolean = false
  private startEpochMs: number = 0
  private renderedFrames: number = 0

  /** 每路音量快照（0..1），copy-on-write 发布 */
  private volumes: number[] = SoundscapeEngine.initialVolumes()

  /** 每路开关快照，copy-on-write 发布 */
  private enabled: boolean[] = new Array<boolean>(SOUND_CHANNEL_COUNT).fill(false)

  /** 淡出时长（ms）；0 表示无淡出 */
  private fadeDurationMs: number = 0
  /** 淡出起点（epoch ms） */
  private fadeStartMs: number = 0

  get isPlaying(): boolean {
    return this.playingValue
  }

  private static initialVolumes(): number[] {
    const result: number[] = new Array<number>(SOUND_CHANNEL_COUNT)
      .fill(SOUNDSCAPE_DEFAULT_VOLUME / 100)
    return result
  }

  /** 设置某路音量（0..100，越界自动夹取） */
  setVolume(channel: SoundChannel, volume: number): void {
    const coerced: number = Math.min(100, Math.max(0, volume)) / 100
    const next: number[] = this.volumes.slice()
    next[channel] = coerced
    this.volumes = next
  }

  /** 开关某路声道 */
  setChannelEnabled(channel: SoundChannel, isEnabled: boolean): void {
    const next: boolean[] = this.enabled.slice()
    next[channel] = isEnabled
    this.enabled = next
  }

  /**
   * 开始混音。已在播 / 已释放时忽略（返回当前播放态）；
   * 音频设备不可用时降级返回 false（页面保持停止态）。
   */
  async start(): Promise<boolean> {
    if (this.playingValue || this.releasedValue) {
      return this.playingValue
    }
    // 清掉可能残留的淡出（如睡眠定时到点停止后再手动播放），避免静音启动
    this.fadeDurationMs = 0
    this.playingValue = true

    let renderer: audio.AudioRenderer | null = null
    try {
      const options: audio.AudioRendererOptions = {
        streamInfo: {
          samplingRate: audio.AudioSamplingRate.SAMPLE_RATE_44100,
          channels: audio.AudioChannel.CHANNEL_1,
          sampleFormat: audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,
          encodingType: audio.AudioEncodingType.ENCODING_TYPE_RAW,
        },
        rendererInfo: {
          usage: audio.StreamUsage.STREAM_USAGE_MUSIC,
          rendererFlags: 0,
        },
      }
      renderer = await audio.createAudioRenderer(options)
      await renderer.start()
    } catch (err) {
      renderer = null
    }

    // 创建期间被 stop/release：立即释放新建的渲染器（对齐安卓代际计数语义）
    if (!this.playingValue) {
      SoundscapeEngine.releaseRenderer(renderer)
      return false
    }
    if (renderer === null) {
      this.playingValue = false
      return false
    }

    this.renderer = renderer
    this.state = new ChannelState()
    this.startEpochMs = Date.now() + 80
    this.renderedFrames = 0
    this.schedulerId = setInterval(() => {
      this.schedulerTick()
    }, SCHEDULER_MS)
    return true
  }

  /** 停止混音（释放渲染器；未在播时忽略） */
  stop(): void {
    if (!this.playingValue && this.schedulerId === -1) {
      return
    }
    this.playingValue = false
    if (this.schedulerId !== -1) {
      clearInterval(this.schedulerId)
      this.schedulerId = -1
    }
    const renderer: audio.AudioRenderer | null = this.renderer
    this.renderer = null
    SoundscapeEngine.releaseRenderer(renderer)
  }

  /** 页面销毁时调用：停止渲染并禁止后续 start */
  release(): void {
    this.releasedValue = true
    this.stop()
  }

  /** 触发线性淡出（到点后由调用方停止；durationMs 越长越缓） */
  fadeOut(durationMs: number): void {
    this.fadeDurationMs = Math.max(0, durationMs)
    this.fadeStartMs = Date.now()
  }

  /** 取消进行中的淡出（恢复满增益） */
  cancelFadeOut(): void {
    this.fadeDurationMs = 0
  }

  private static releaseRenderer(renderer: audio.AudioRenderer | null): void {
    if (renderer === null) {
      return
    }
    try {
      renderer.stop()
      renderer.release()
    } catch (err) {
      // 释放异常忽略（玩具页不崩溃）
    }
  }

  // ==================== 调度内核 ====================

  private schedulerTick(): void {
    if (!this.playingValue) {
      return
    }
    const nowMs: number = Date.now()
    const targetMs: number = nowMs - this.startEpochMs + LOOKAHEAD_MS
    let dueFrames: number = Math.floor(targetMs * SAMPLE_RATE / 1000) - this.renderedFrames
    if (dueFrames > SAMPLE_RATE) {
      // 停顿超过 1 秒（页面被冻结 / 线程饿死）：直接跳到当前时刻，丢弃欠账音频，
      // 避免一次性补渲染数百块造成卡顿突发（安卓阻塞写天然无此问题）
      this.renderedFrames = Math.floor(targetMs * SAMPLE_RATE / 1000) - CHUNK_FRAMES
      dueFrames = CHUNK_FRAMES
    }
    while (dueFrames > 0) {
      const frames: number = Math.min(CHUNK_FRAMES, dueFrames)
      this.renderAndWrite(frames)
      this.renderedFrames += frames
      dueFrames -= frames
    }
  }

  /** 渲染一帧块：逐采样叠加各路声道，母线 tanh 软限幅后转 16-bit 写入渲染器 */
  private renderAndWrite(frames: number): void {
    const renderer: audio.AudioRenderer | null = this.renderer
    const state: ChannelState | null = this.state
    if (renderer === null || state === null) {
      return
    }
    const pcm: ArrayBuffer = new ArrayBuffer(frames * 2)
    const out: Int16Array = new Int16Array(pcm)
    const vols: number[] = this.volumes
    const enabled: boolean[] = this.enabled
    // 淡出系数按块计算（23ms 一档，3 秒淡出约 130 档，听感平滑；安卓为逐采样）
    const master: number = MASTER_GAIN * this.fadeFactor()
    const rng: LabRandom = state.rng
    for (let i: number = 0; i < frames; i++) {
      state.t += SAMPLE_DT
      let sample: number = 0
      for (let c: number = 0; c < SOUND_CHANNEL_COUNT; c++) {
        if (enabled[c]) {
          sample += SoundscapeEngine.renderChannel(state, c, vols[c], rng)
        }
      }
      out[i] = Math.round(Math.tanh(sample * master) * 32767)
    }
    try {
      renderer.write(pcm).then(() => {
      }, () => {
      })
    } catch (err) {
      // 写入异常忽略（音频设备断开时玩具页不崩溃）
    }
  }

  /** 单路声道的单采样生成（按声道序号分发） */
  private static renderChannel(state: ChannelState, channel: number, volume: number,
    rng: LabRandom): number {
    const dt: number = SAMPLE_DT
    switch (channel) {
      case SoundChannel.WHITE_NOISE:
        return state.renderWhite(volume, rng)
      case SoundChannel.PINK_NOISE:
        return state.renderPink(volume, rng)
      case SoundChannel.BROWN_NOISE:
        return state.renderBrown(volume, rng)
      case SoundChannel.RAIN:
        return state.renderRain(volume, rng, dt)
      case SoundChannel.WIND:
        return state.renderWind(volume, rng)
      case SoundChannel.OCEAN:
        return state.renderOcean(volume, rng)
      case SoundChannel.CAMPFIRE:
        return state.renderCampfire(volume, rng, dt)
      case SoundChannel.PAGE_FLIP:
        return state.renderPageFlip(volume, rng, dt)
      case SoundChannel.KEYBOARD:
        return state.renderKeyboard(volume, rng, dt)
      default:
        return state.renderMurmur(volume, rng)
    }
  }

  private fadeFactor(): number {
    const duration: number = this.fadeDurationMs
    if (duration <= 0) {
      return 1
    }
    const elapsedMs: number = Date.now() - this.fadeStartMs
    return Math.min(1, Math.max(0, 1 - elapsedMs / duration))
  }
}
