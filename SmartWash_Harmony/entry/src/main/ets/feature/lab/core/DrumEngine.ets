/**
 * 滚筒节拍机音频引擎（零音频资产）— 对齐安卓 DrumEngine.kt 的合成与调度思路。
 *
 * 合成：8 种鼓声在首次启动时程序化合成（正弦扫频 + 噪声 + 指数包络），预渲染为 PCM 缓冲；
 * 调度：以系统时钟为基准，调度线程每 [SCHEDULER_MS] 醒一次，把未来 [LOOKAHEAD_MS] 窗口内的
 * 步渲染进 AudioRenderer 流缓冲（lookahead 思想，避免写缓冲漂移）。
 *
 * 线程模型：ArkTS 单线程事件循环，调度经 setInterval 完成；pattern/bpm 以不可变快照
 * 发布，调度回调无锁读取。音频不可用时降级为「静默调度」（视觉播放头仍推进）。
 */

import audio from '@ohos.multimedia.audio'

/** 音序器步数（16 步） */
export const DRUM_STEP_COUNT: number = 16

/** 声部数量（8 种鼓声） */
export const DRUM_VOICE_COUNT: number = 8

/** BPM 下限 */
export const DRUM_MIN_BPM: number = 60

/** BPM 上限 */
export const DRUM_MAX_BPM: number = 180

/** 默认 BPM */
export const DRUM_DEFAULT_BPM: number = 120

/** 鼓声部枚举（顺序即网格行序，与合成器 voice 表一致） */
export enum DrumVoice {
  KICK = 0,
  SNARE = 1,
  HIHAT = 2,
  CLAP = 3,
  TOM = 4,
  COWBELL = 5,
  RIM = 6,
  CYMBAL = 7,
}

/** 各声部时长（秒） */
const VOICE_DURATION_SEC: number[] = [0.28, 0.22, 0.09, 0.28, 0.32, 0.28, 0.06, 0.95]

/** 采样率 */
const SAMPLE_RATE: number = 44100

/** 调度 lookahead（ms） */
const LOOKAHEAD_MS: number = 150

/** 调度线程唤醒间隔（ms） */
const SCHEDULER_MS: number = 50

/** 单次渲染块时长（ms） */
const CHUNK_MS: number = 50

/** 单声部归一化峰值 */
const VOICE_PEAK: number = 0.6

/** 母线增益（配合 tanh 软限幅） */
const MASTER_GAIN: number = 1.5

/**
 * 不可变节奏型：8 声部 × 16 步的位图，下标 = voice * [DRUM_STEP_COUNT] + step。
 * 拷贝写入（[withStep]）保证调度回调读到的引用永远是完整快照。
 */
export class DrumPattern {
  private bits: boolean[]

  constructor(bits?: boolean[]) {
    this.bits = bits ? bits.slice() : new Array<boolean>(DRUM_VOICE_COUNT * DRUM_STEP_COUNT).fill(false)
  }

  isEnabled(voice: number, step: number): boolean {
    return this.bits[voice * DRUM_STEP_COUNT + step]
  }

  withStep(voice: number, step: number, enabled: boolean): DrumPattern {
    const next: boolean[] = this.bits.slice()
    next[voice * DRUM_STEP_COUNT + step] = enabled
    return new DrumPattern(next)
  }

  /** 从 8 行 × 16 字符的 'x'/'.' 谱面构建（预设用） */
  static fromRows(rows: string[]): DrumPattern {
    const bits: boolean[] = new Array<boolean>(DRUM_VOICE_COUNT * DRUM_STEP_COUNT).fill(false)
    for (let voice = 0; voice < rows.length && voice < DRUM_VOICE_COUNT; voice++) {
      const row: string = rows[voice]
      for (let step = 0; step < row.length && step < DRUM_STEP_COUNT; step++) {
        bits[voice * DRUM_STEP_COUNT + step] = row.charAt(step) === 'x'
      }
    }
    return new DrumPattern(bits)
  }

  static readonly EMPTY: DrumPattern = new DrumPattern()
}

/** 活跃音符（引擎内部调度用） */
interface ScheduledNote {
  voice: number
  startMs: number
}

export class DrumEngine {
  private renderer: audio.AudioRenderer | null = null
  private voices: Float32Array[] = []
  private bpmValue: number = DRUM_DEFAULT_BPM
  private patternValue: DrumPattern = DrumPattern.EMPTY
  private playingValue: boolean = false

  private schedulerId: number = -1
  private stepIndexValue: number = 0
  private currentStepValue: number = -1
  private startEpochMs: number = 0
  private nextStepTimeMs: number = 0
  private renderedUpToMs: number = 0
  private activeNotes: ScheduledNote[] = []
  private onStepCallback: (step: number) => void = () => {
  }

  get isPlaying(): boolean {
    return this.playingValue
  }

  get currentStep(): number {
    return this.currentStepValue
  }

  setBpm(value: number): void {
    this.bpmValue = Math.min(DRUM_MAX_BPM, Math.max(DRUM_MIN_BPM, value))
  }

  setPattern(value: DrumPattern): void {
    this.patternValue = value
  }

  /** 绑定播放头回调（页面据此高亮当前步） */
  setStepListener(callback: (step: number) => void): void {
    this.onStepCallback = callback
  }

  /**
   * 启动调度。音频设备不可用时静默降级（视觉播放头仍推进）。
   * 返回是否成功创建音频渲染器（供页面留痕，不影响交互）。
   */
  async start(): Promise<boolean> {
    if (this.playingValue) {
      return true
    }
    this.ensureVoices()
    this.playingValue = true
    this.stepIndexValue = 0
    this.currentStepValue = -1
    this.startEpochMs = Date.now() + 100
    this.nextStepTimeMs = this.startEpochMs
    this.renderedUpToMs = this.startEpochMs
    this.activeNotes = []

    let audioOk: boolean = false
    try {
      const options: audio.AudioRendererOptions = {
        streamInfo: {
          samplingRate: audio.AudioSamplingRate.SAMPLE_RATE_44100,
          channels: audio.AudioChannel.CHANNEL_1,
          sampleFormat: audio.AudioSampleFormat.SAMPLE_FORMAT_S16LE,
          encodingType: audio.AudioEncodingType.ENCODING_TYPE_RAW,
        },
        rendererInfo: {
          usage: audio.StreamUsage.STREAM_USAGE_GAME,
          rendererFlags: 0,
        },
      }
      this.renderer = await audio.createAudioRenderer(options)
      await this.renderer.start()
      audioOk = true
    } catch (err) {
      // 音频不可用：静默降级，调度仍推进播放头
      this.renderer = null
    }

    this.schedulerId = setInterval(() => {
      this.schedulerTick()
    }, SCHEDULER_MS)
    return audioOk
  }

  /** 停止调度并释放音频资源 */
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
    if (renderer !== null) {
      try {
        renderer.stop()
        renderer.release()
      } catch (err) {
        // 释放异常忽略（玩具页不崩溃）
      }
    }
    this.currentStepValue = -1
    this.onStepCallback(-1)
  }

  /** 释放引擎（页面销毁时调用） */
  release(): void {
    this.stop()
    this.voices = []
  }

  // ==================== 调度内核 ====================

  private schedulerTick(): void {
    if (!this.playingValue) {
      return
    }
    const now: number = Date.now()
    const target: number = now + LOOKAHEAD_MS
    // 调度目标窗口内的步
    while (this.nextStepTimeMs < target) {
      this.scheduleStep(this.stepIndexValue, this.nextStepTimeMs)
      this.stepIndexValue = (this.stepIndexValue + 1) % DRUM_STEP_COUNT
      this.nextStepTimeMs += this.stepDurationMs()
    }
    // 渲染音频块
    while (this.renderedUpToMs < target) {
      const chunkEnd: number = this.renderedUpToMs + CHUNK_MS
      this.renderChunk(this.renderedUpToMs, chunkEnd)
      this.renderedUpToMs = chunkEnd
    }
    // 更新播放头（视觉）
    if (now >= this.startEpochMs) {
      const step: number = Math.floor((now - this.startEpochMs) / this.stepDurationMs()) % DRUM_STEP_COUNT
      if (step !== this.currentStepValue) {
        this.currentStepValue = step
        this.onStepCallback(step)
      }
    }
  }

  /** 把某一步触发的声部登记为活跃音符 */
  private scheduleStep(step: number, timeMs: number): void {
    for (let voice = 0; voice < DRUM_VOICE_COUNT; voice++) {
      if (this.patternValue.isEnabled(voice, step)) {
        this.activeNotes.push({ voice: voice, startMs: timeMs })
      }
    }
  }

  /** 混合 [startMs, endMs) 区间的活跃音符并写入 AudioRenderer */
  private renderChunk(startMs: number, endMs: number): void {
    const renderer: audio.AudioRenderer | null = this.renderer
    if (renderer === null) {
      return
    }
    const frames: number = Math.floor(CHUNK_MS * SAMPLE_RATE / 1000)
    const mix: Float32Array = new Float32Array(frames)
    const kept: ScheduledNote[] = []
    for (const note of this.activeNotes) {
      const voice: Float32Array | undefined = this.voices[note.voice]
      if (voice === undefined) {
        continue
      }
      const noteEndMs: number = note.startMs + voice.length / SAMPLE_RATE * 1000
      if (noteEndMs <= startMs) {
        continue
      }
      kept.push(note)
      const overlapStartMs: number = Math.max(startMs, note.startMs)
      const overlapEndMs: number = Math.min(endMs, noteEndMs)
      if (overlapStartMs >= overlapEndMs) {
        continue
      }
      const dstStart: number = Math.floor((overlapStartMs - startMs) / 1000 * SAMPLE_RATE)
      const srcStart: number = Math.floor((overlapStartMs - note.startMs) / 1000 * SAMPLE_RATE)
      const length: number = Math.floor((overlapEndMs - overlapStartMs) / 1000 * SAMPLE_RATE)
      for (let j = 0; j < length; j++) {
        const srcIdx: number = srcStart + j
        const dstIdx: number = dstStart + j
        if (srcIdx < 0 || srcIdx >= voice.length || dstIdx < 0 || dstIdx >= frames) {
          continue
        }
        mix[dstIdx] += voice[srcIdx]
      }
    }
    this.activeNotes = kept

    const pcm: ArrayBuffer = new ArrayBuffer(frames * 2)
    const samples: Int16Array = new Int16Array(pcm)
    for (let i = 0; i < frames; i++) {
      const v: number = Math.tanh(mix[i] * MASTER_GAIN)
      samples[i] = Math.round(v * 32767)
    }
    try {
      renderer.write(pcm).then(() => {
      }, () => {
      })
    } catch (err) {
      // 写入异常忽略（音频设备断开时玩具页不崩溃）
    }
  }

  private stepDurationMs(): number {
    return 60000 / this.bpmValue / 4
  }

  // ==================== 合成 ====================

  private ensureVoices(): void {
    if (this.voices.length > 0) {
      return
    }
    const rendered: Float32Array[] = []
    for (let voice = 0; voice < DRUM_VOICE_COUNT; voice++) {
      rendered.push(this.renderVoice(voice))
    }
    this.voices = rendered
  }

  /** 预渲染单个鼓声到 PCM 浮点缓冲，并按峰值归一化到 [VOICE_PEAK] */
  private renderVoice(voice: number): Float32Array {
    const sampleCount: number = Math.floor(VOICE_DURATION_SEC[voice] * SAMPLE_RATE)
    const out: Float32Array = new Float32Array(sampleCount)
    let lastNoise: number = 0
    let peak: number = 0
    for (let i = 0; i < sampleCount; i++) {
      const t: number = i / SAMPLE_RATE
      const sample: SampleResult = this.renderSample(voice, t, lastNoise)
      lastNoise = sample.lastNoise
      out[i] = sample.value
      const magnitude: number = Math.abs(sample.value)
      if (magnitude > peak) {
        peak = magnitude
      }
    }
    const normalize: number = peak > 0 ? VOICE_PEAK / peak : 1
    for (let i = 0; i < sampleCount; i++) {
      out[i] *= normalize
    }
    return out
  }

  /** 单采样渲染结果（值 + 一阶高通上一采样噪声） */
  private renderSample(voice: number, t: number, lastNoise: number): SampleResult {
    const noise: number = Math.random() * 2 - 1
    const highpassed: number = noise - lastNoise
    const twoPi: number = 2 * Math.PI
    let value: number = 0
    switch (voice) {
      case DrumVoice.KICK: {
        const f0: number = 120
        const f1: number = 45
        const k: number = 16
        const phase: number = twoPi * (f0 * (1 - Math.exp(-k * t)) / k + f1 * t)
        const env: number = Math.exp(-t * 14)
        const click: number = t < 0.004 ? highpassed * (1 - t / 0.004) * 0.6 : 0
        value = Math.sin(phase) * env * 0.9 + click
        break
      }
      case DrumVoice.SNARE: {
        const body: number = Math.sin(twoPi * 185 * t) * Math.exp(-t * 20) * 0.5
        const snap: number = highpassed * Math.exp(-t * 26) * 0.6
        value = body + snap
        break
      }
      case DrumVoice.HIHAT: {
        const env: number = Math.exp(-t * 70)
        value = highpassed * env * 0.7 + Math.sin(twoPi * 8200 * t) * env * 0.1
        break
      }
      case DrumVoice.CLAP: {
        let burst: number
        if (t < 0.012) {
          burst = 1
        } else if (t < 0.024) {
          burst = 0.2
        } else if (t < 0.036) {
          burst = 0.85
        } else if (t < 0.048) {
          burst = 0.2
        } else if (t < 0.062) {
          burst = 0.6
        } else {
          burst = Math.exp(-(t - 0.062) * 22)
        }
        value = highpassed * burst * 0.75
        break
      }
      case DrumVoice.TOM: {
        const f0: number = 170
        const f1: number = 85
        const k: number = 9
        const phase: number = twoPi * (f0 * (1 - Math.exp(-k * t)) / k + f1 * t)
        value = Math.sin(phase) * Math.exp(-t * 11) * 0.85
        break
      }
      case DrumVoice.COWBELL: {
        const env: number = Math.exp(-t * 15)
        value = (Math.sin(twoPi * 560 * t) * 0.5 + Math.sin(twoPi * 845 * t) * 0.5) * env * 0.7
        break
      }
      case DrumVoice.RIM: {
        const env: number = Math.exp(-t * 95)
        value = (Math.sin(twoPi * 1720 * t) * 0.45 + highpassed * 0.35) * env * 0.8
        break
      }
      default: {
        // CYMBAL
        const env: number = Math.exp(-t * 4.5)
        const metallic: number = Math.sin(twoPi * 5200 * t) * 0.12 + Math.sin(twoPi * 7350 * t) * 0.08
        value = (highpassed * 0.55 + metallic) * env * 0.6
        break
      }
    }
    return { value: value, lastNoise: noise }
  }
}

/** 单采样渲染中间结果 */
interface SampleResult {
  value: number
  lastNoise: number
}
