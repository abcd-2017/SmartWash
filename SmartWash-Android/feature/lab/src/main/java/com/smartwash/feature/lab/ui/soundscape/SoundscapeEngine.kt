package com.smartwash.feature.lab.ui.soundscape

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/**
 * 声景声道枚举（顺序即混音台展示序，分三组：底噪 / 自然 / 室内）。
 *
 * 全部声音由 [SoundscapeEngine] 程序化合成，零音频资产：
 * 白 / 粉 / 棕噪音为算法噪声；雨声 = 滤波噪声 + 随机雨滴脉冲；
 * 风声 = 慢速调制噪声；海浪 = 更慢的周期调制噪声；篝火 = 随机噼啪脉冲串；
 * 翻书 / 键盘 / 远处交谈为合成近似的室内纹理。
 */
enum class SoundChannel {
    WHITE_NOISE,
    PINK_NOISE,
    BROWN_NOISE,
    RAIN,
    WIND,
    OCEAN,
    CAMPFIRE,
    PAGE_FLIP,
    KEYBOARD,
    MURMUR,
}

/**
 * 声景混音器音频引擎（零音频资产）。
 *
 * 合成：每路声道一个程序化生成器（噪声滤波 / 脉冲池 / 慢速 LFO 调制），
 * 在音频线程内逐采样生成，多路实时叠混；母线经 tanh 软限幅防削顶。
 *
 * 线程模型：单线程执行器内完成 AudioTrack 创建 / 渲染 / 写入 / 释放
 * （write 阻塞，禁止主线程）；[channels] / [volumes] 以不可变快照
 * （copy-on-write）发布，音频线程无锁读取；开始 / 停止用代际计数
 * （[generation]）避免旧渲染循环因新 start 而赖着不走。
 *
 * 生命周期：进入页面不自动播放；[release] 必须在页面销毁时调用。
 */
class SoundscapeEngine {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "soundscape-engine").apply { isDaemon = true }
    }

    private val generation = AtomicInteger(0)

    @Volatile private var isPlaying = false
    @Volatile private var released = false

    /** 每路音量快照（0..1），copy-on-write 发布 */
    @Volatile private var volumes: FloatArray = FloatArray(CHANNEL_COUNT) { DEFAULT_VOLUME / 100f }

    /** 每路开关快照，copy-on-write 发布 */
    @Volatile private var enabled: BooleanArray = BooleanArray(CHANNEL_COUNT)

    /** 淡出时长（ms）；0 表示无淡出 */
    @Volatile private var fadeDurationMs = 0

    /** 淡出起点（nanoTime），与 [fadeDurationMs] 配对 */
    @Volatile private var fadeStartNanos = 0L

    private var future: Future<*>? = null

    private val _playing = MutableStateFlow(false)

    /** 引擎是否在播（UI 状态以此为准，避免主线程轮询） */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    /** 设置某路音量（0..100，越界自动夹取） */
    fun setVolume(channel: SoundChannel, volume: Int) {
        val next = volumes.copyOf()
        next[channel.ordinal] = volume.coerceIn(0, 100) / 100f
        volumes = next
    }

    /** 开关某路声道 */
    fun setChannelEnabled(channel: SoundChannel, isEnabled: Boolean) {
        val next = enabled.copyOf()
        next[channel.ordinal] = isEnabled
        this.enabled = next
    }

    /** 开始混音（已在播 / 已释放时忽略） */
    fun start() {
        if (isPlaying || released) return
        // 清掉可能残留的淡出（如睡眠定时到点停止后再手动播放），避免静音启动
        fadeDurationMs = 0
        isPlaying = true
        _playing.value = true
        val gen = generation.incrementAndGet()
        future = executor.submit { runEngine(gen) }
    }

    /** 停止混音（释放 AudioTrack；未在播时忽略） */
    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        generation.incrementAndGet()
        future?.cancel(true)
        future = null
        _playing.value = false
    }

    /** 页面销毁时调用：停止渲染并释放执行器与 AudioTrack */
    fun release() {
        released = true
        isPlaying = false
        generation.incrementAndGet()
        future?.cancel(true)
        future = null
        executor.shutdownNow()
        _playing.value = false
    }

    /** 触发线性淡出（到点后由调用方停止；[ durationMs ] 越长越缓） */
    fun fadeOut(durationMs: Int) {
        fadeDurationMs = durationMs.coerceAtLeast(0)
        fadeStartNanos = System.nanoTime()
    }

    /** 取消进行中的淡出（恢复满增益） */
    fun cancelFadeOut() {
        fadeDurationMs = 0
    }

    // ==================== 渲染内核 ====================

    private fun runEngine(gen: Int) {
        val track = createTrack() ?: run {
            isPlaying = false
            _playing.value = false
            return
        }
        val state = ChannelState()
        val chunk = ShortArray(CHUNK_FRAMES)
        try {
            track.play()
            while (isPlaying && !released && generation.get() == gen) {
                renderChunk(chunk, state)
                // MODE_STREAM 阻塞写：缓冲写满时自然节流，无需 sleep
                track.write(chunk, 0, CHUNK_FRAMES)
            }
            track.stop()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Throwable) {
            // 音频设备异常：静默停止（玩具页不崩溃）
        } finally {
            track.release()
            if (generation.get() == gen) {
                isPlaying = false
                _playing.value = false
            }
        }
    }

    /** 渲染 [CHUNK_FRAMES] 帧：逐采样叠加各路声道，母线 tanh 软限幅后转 16-bit */
    private fun renderChunk(out: ShortArray, state: ChannelState) {
        val vols = volumes
        val enabled = enabled
        val master = MASTER_GAIN * fadeFactor()
        val rng = state.rng
        for (i in out.indices) {
            state.t += SAMPLE_DT
            var sample = 0f
            for (c in 0 until CHANNEL_COUNT) {
                if (enabled[c]) {
                    sample += renderChannel(SoundChannel.entries[c], state, vols[c], rng)
                }
            }
            out[i] = (tanh(sample * master) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** 单路声道的单采样生成（全部在音频线程内调用） */
    private fun renderChannel(
        channel: SoundChannel,
        state: ChannelState,
        volume: Float,
        rng: Random,
    ): Float = when (channel) {
        SoundChannel.WHITE_NOISE -> renderWhite(state, volume, rng)
        SoundChannel.PINK_NOISE -> renderPink(state, volume, rng)
        SoundChannel.BROWN_NOISE -> renderBrown(state, volume, rng)
        SoundChannel.RAIN -> renderRain(state, volume, rng)
        SoundChannel.WIND -> renderWind(state, volume, rng)
        SoundChannel.OCEAN -> renderOcean(state, volume, rng)
        SoundChannel.CAMPFIRE -> renderCampfire(state, volume, rng)
        SoundChannel.PAGE_FLIP -> renderPageFlip(state, volume, rng)
        SoundChannel.KEYBOARD -> renderKeyboard(state, volume, rng)
        SoundChannel.MURMUR -> renderMurmur(state, volume, rng)
    }

    // ==================== 噪声合成 ====================

    /** 白噪音：均匀分布随机样本 */
    private fun renderWhite(state: ChannelState, volume: Float, rng: Random): Float {
        return (rng.nextFloat() * 2f - 1f) * volume * WHITE_GAIN
    }

    /** 粉噪音：Paul Kellet 经济版三阶滤波（b0/b1/b2 + 残余白） */
    private fun renderPink(state: ChannelState, volume: Float, rng: Random): Float {
        val white = rng.nextFloat() * 2f - 1f
        state.pinkB0 = 0.99765f * state.pinkB0 + white * 0.0990460f
        state.pinkB1 = 0.96300f * state.pinkB1 + white * 0.2965164f
        state.pinkB2 = 0.57000f * state.pinkB2 + white * 1.0526913f
        val pink = (state.pinkB0 + state.pinkB1 + state.pinkB2 + white * 0.1848f) * PINK_SCALE
        return pink * volume * PINK_GAIN
    }

    /** 棕噪音：漏积分器（低通随机游走），低频厚重 */
    private fun renderBrown(state: ChannelState, volume: Float, rng: Random): Float {
        val white = rng.nextFloat() * 2f - 1f
        state.brown = (state.brown + 0.02f * white) / 1.02f
        return state.brown * BROWN_SCALE * volume * BROWN_GAIN
    }

    // ==================== 自然声 ====================

    /**
     * 雨声：低通 + 高通滤波噪声做"雨幕底"，叠加随机雨滴脉冲
     * （短正弦 ping + 噪声点击，指数衰减）。
     */
    private fun renderRain(state: ChannelState, volume: Float, rng: Random): Float {
        val white = rng.nextFloat() * 2f - 1f
        state.rainLp += RAIN_LP_ALPHA * (white - state.rainLp)
        state.rainHp += RAIN_HP_ALPHA * (white - state.rainHp)
        val base = state.rainLp * 0.5f + (white - state.rainHp) * 0.15f
        if (rng.nextFloat() < RAIN_DROP_RATE / SAMPLE_RATE) {
            state.rainDrops.trigger(
                rng = rng,
                freqRange = 900f..3_200f,
                ampRange = 0.04f..0.16f,
                decayRange = 60f..140f,
                lifeRange = 0.02f..0.06f,
                noiseMix = 0.5f,
            )
        }
        var drops = 0f
        for (i in 0 until state.rainDrops.size) {
            if (state.rainDrops.isActive(i)) {
                drops += state.rainDrops.sample(i, rng)
                state.rainDrops.advance(i, SAMPLE_DT)
            }
        }
        return (base + drops) * volume * RAIN_GAIN
    }

    /** 风声：一阶低通的截止频率由慢速 LFO 调制，叠加更低频的阵风包络 */
    private fun renderWind(state: ChannelState, volume: Float, rng: Random): Float {
        val white = rng.nextFloat() * 2f - 1f
        val t = state.t
        val lfo = 0.5f + 0.5f * sin(2f * PI.toFloat() * WIND_LFO_HZ * t)
        val alpha = WIND_LP_MIN + (WIND_LP_MAX - WIND_LP_MIN) * lfo
        state.windLp += alpha * (white - state.windLp)
        val gust = 0.5f + 0.5f * sin(2f * PI.toFloat() * WIND_GUST_HZ * t + 1.3f)
        val amp = 0.35f + 0.5f * gust
        return state.windLp * amp * volume * WIND_GAIN
    }

    /** 海浪：低通噪声 + 慢速周期波包络（波峰整形为平方曲线），波峰叠泡沫高频 */
    private fun renderOcean(state: ChannelState, volume: Float, rng: Random): Float {
        val white = rng.nextFloat() * 2f - 1f
        state.oceanLp += OCEAN_LP_ALPHA * (white - state.oceanLp)
        state.oceanHp += OCEAN_HP_ALPHA * (white - state.oceanHp)
        val wave = maxOf(0f, sin(2f * PI.toFloat() * OCEAN_WAVE_HZ * state.t))
        val shaped = wave * wave
        val foam = (white - state.oceanHp) * shaped * 0.25f
        val body = state.oceanLp * (0.25f + 0.75f * shaped)
        return (body + foam) * volume * OCEAN_GAIN
    }

    // ==================== 室内纹理 ====================

    /** 篝火：低通噪声做底，叠加随机噼啪脉冲串（高频短促）与偶发低频次爆裂 */
    private fun renderCampfire(state: ChannelState, volume: Float, rng: Random): Float {
        val white = rng.nextFloat() * 2f - 1f
        state.fireLp += FIRE_LP_ALPHA * (white - state.fireLp)
        if (rng.nextFloat() < FIRE_CRACKLE_RATE / SAMPLE_RATE) {
            state.fireCrackles.trigger(
                rng = rng,
                freqRange = 1_200f..4_200f,
                ampRange = 0.05f..0.22f,
                decayRange = 200f..500f,
                lifeRange = 0.004f..0.02f,
                noiseMix = 0.7f,
            )
        }
        if (rng.nextFloat() < FIRE_POP_RATE / SAMPLE_RATE) {
            state.fireCrackles.trigger(
                rng = rng,
                freqRange = 180f..500f,
                ampRange = 0.2f..0.4f,
                decayRange = 60f..120f,
                lifeRange = 0.03f..0.08f,
                noiseMix = 0.9f,
            )
        }
        var crackles = 0f
        for (i in 0 until state.fireCrackles.size) {
            if (state.fireCrackles.isActive(i)) {
                crackles += state.fireCrackles.sample(i, rng)
                state.fireCrackles.advance(i, SAMPLE_DT)
            }
        }
        return (state.fireLp * 0.5f + crackles) * volume * FIRE_GAIN
    }

    /** 翻书：稀疏的带通噪声"哗啦"短脉冲（指数衰减，模拟纸页摩擦） */
    private fun renderPageFlip(state: ChannelState, volume: Float, rng: Random): Float {
        if (rng.nextFloat() < PAGE_SWISH_RATE / SAMPLE_RATE) {
            state.pageSwishes.trigger(
                rng = rng,
                freqRange = 0f..0f,
                ampRange = 0.1f..0.25f,
                decayRange = 20f..40f,
                lifeRange = 0.08f..0.2f,
                noiseMix = 1f,
            )
        }
        var sum = 0f
        for (i in 0 until state.pageSwishes.size) {
            if (state.pageSwishes.isActive(i)) {
                // 纸页摩擦：脉冲包络再叠一层低通噪声
                val white = rng.nextFloat() * 2f - 1f
                state.pageLp += PAGE_LP_ALPHA * (white - state.pageLp)
                sum += state.pageLp * state.pageSwishes.sample(i, rng)
                state.pageSwishes.advance(i, SAMPLE_DT)
            }
        }
        return sum * volume * PAGE_GAIN
    }

    /** 键盘：随机短促敲击脉冲（高频正弦 + 噪声，极短衰减） */
    private fun renderKeyboard(state: ChannelState, volume: Float, rng: Random): Float {
        if (rng.nextFloat() < KEY_TAP_RATE / SAMPLE_RATE) {
            state.keyTaps.trigger(
                rng = rng,
                freqRange = 1_600f..4_200f,
                ampRange = 0.08f..0.2f,
                decayRange = 350f..800f,
                lifeRange = 0.004f..0.012f,
                noiseMix = 0.6f,
            )
        }
        var sum = 0f
        for (i in 0 until state.keyTaps.size) {
            if (state.keyTaps.isActive(i)) {
                sum += state.keyTaps.sample(i, rng)
                state.keyTaps.advance(i, SAMPLE_DT)
            }
        }
        return sum * volume * KEY_GAIN
    }

    /** 远处交谈：两路低通噪声 + 随机音节包络（电报式幅度 + 基频颤动） */
    private fun renderMurmur(state: ChannelState, volume: Float, rng: Random): Float {
        val white = rng.nextFloat() * 2f - 1f
        state.murmurLp1 += MURMUR_LP_ALPHA * (white - state.murmurLp1)
        state.murmurLp2 += MURMUR_LP_ALPHA * (white - state.murmurLp2)

        if (state.t >= state.murmurNextChange1) {
            state.murmurTarget1 = rng.nextFloat() * 0.35f
            state.murmurNextChange1 = state.t + 0.12f + rng.nextFloat() * 0.3f
        }
        if (state.t >= state.murmurNextChange2) {
            state.murmurTarget2 = rng.nextFloat() * 0.3f
            state.murmurNextChange2 = state.t + 0.15f + rng.nextFloat() * 0.35f
        }
        state.murmurEnv1 += (state.murmurTarget1 - state.murmurEnv1) * MURMUR_ENV_SLEW
        state.murmurEnv2 += (state.murmurTarget2 - state.murmurEnv2) * MURMUR_ENV_SLEW

        val voice1 = 0.5f + 0.5f * sin(2f * PI.toFloat() * MURMUR_VOICE1_HZ * state.t)
        val voice2 = 0.5f + 0.5f * sin(2f * PI.toFloat() * MURMUR_VOICE2_HZ * state.t + 1.7f)
        val out = state.murmurLp1 * state.murmurEnv1 * voice1 +
            state.murmurLp2 * state.murmurEnv2 * voice2
        return out * volume * MURMUR_GAIN
    }

    // ==================== 基础设施 ====================

    private fun fadeFactor(): Float {
        val duration = fadeDurationMs
        if (duration <= 0) return 1f
        val elapsedMs = (System.nanoTime() - fadeStartNanos) / 1_000_000f
        return (1f - elapsedMs / duration).coerceIn(0f, 1f)
    }

    private fun createTrack(): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        // 1 秒缓冲（16-bit 单声道）：阻塞写自然节流，stop 响应延迟 ≤ 一个 chunk
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE * 2)
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(bufferSize)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (_: Throwable) {
            null
        }
    }

    // ==================== 状态容器 ====================

    /** 单路脉冲池：固定容量、按需触发、指数衰减包络（雨滴 / 篝火 / 翻书 / 键盘共用） */
    private class PulsePool(val size: Int) {
        private val active = BooleanArray(size)
        private val age = FloatArray(size)
        private val life = FloatArray(size)
        private val freq = FloatArray(size)
        private val amp = FloatArray(size)
        private val decay = FloatArray(size)
        private val noiseMix = FloatArray(size)

        fun isActive(index: Int): Boolean = active[index]

        /** 触发一个脉冲；池满时静默丢弃（自然稀疏） */
        fun trigger(
            rng: Random,
            freqRange: ClosedFloatingPointRange<Float>,
            ampRange: ClosedFloatingPointRange<Float>,
            decayRange: ClosedFloatingPointRange<Float>,
            lifeRange: ClosedFloatingPointRange<Float>,
            noiseMix: Float,
        ) {
            for (i in 0 until size) {
                if (active[i]) continue
                active[i] = true
                age[i] = 0f
                freq[i] = freqRange.start + rng.nextFloat() *
                    (freqRange.endInclusive - freqRange.start)
                amp[i] = ampRange.start + rng.nextFloat() *
                    (ampRange.endInclusive - ampRange.start)
                decay[i] = decayRange.start + rng.nextFloat() *
                    (decayRange.endInclusive - decayRange.start)
                life[i] = lifeRange.start + rng.nextFloat() *
                    (lifeRange.endInclusive - lifeRange.start)
                this.noiseMix[i] = noiseMix
                return
            }
        }

        /** 单采样：指数衰减包络 ×（正弦音头 + 噪声）按 [noiseMix] 混合 */
        fun sample(index: Int, rng: Random): Float {
            val envelope = amp[index] * exp(-decay[index] * age[index])
            val tone = sin(2f * PI.toFloat() * freq[index] * age[index])
            val noise = rng.nextFloat() * 2f - 1f
            return envelope * (tone * (1f - noiseMix[index]) + noise * noiseMix[index])
        }

        fun advance(index: Int, dt: Float) {
            age[index] += dt
            if (age[index] >= life[index]) active[index] = false
        }
    }

    /** 音频线程私有生成器状态（无锁，仅渲染线程读写） */
    private class ChannelState {
        val rng = Random(0x5EED)

        /** 全局时间（秒），驱动慢速 LFO / 周期包络 */
        var t = 0f

        // 粉噪音三阶滤波状态
        var pinkB0 = 0f
        var pinkB1 = 0f
        var pinkB2 = 0f

        // 棕噪音漏积分状态
        var brown = 0f

        // 雨声滤波状态 + 雨滴池
        var rainLp = 0f
        var rainHp = 0f
        val rainDrops = PulsePool(8)

        // 风声滤波状态
        var windLp = 0f

        // 海浪滤波状态
        var oceanLp = 0f
        var oceanHp = 0f

        // 篝火滤波状态 + 脉冲池
        var fireLp = 0f
        val fireCrackles = PulsePool(10)

        // 翻书滤波状态 + 脉冲池
        var pageLp = 0f
        val pageSwishes = PulsePool(4)

        // 键盘脉冲池
        val keyTaps = PulsePool(8)

        // 远处交谈：两路低通 + 音节包络
        var murmurLp1 = 0f
        var murmurLp2 = 0f
        var murmurEnv1 = 0f
        var murmurEnv2 = 0f
        var murmurTarget1 = 0f
        var murmurTarget2 = 0f
        var murmurNextChange1 = 0f
        var murmurNextChange2 = 0f
    }

    companion object {
        /** 声道总数（随 [SoundChannel] 枚举自动推导，避免增删声道时漏改） */
        val CHANNEL_COUNT: Int = SoundChannel.entries.size

        /** 默认音量（0..100），仅用于未显式设置的声道 */
        const val DEFAULT_VOLUME = 60

        private const val SAMPLE_RATE = 44_100
        private const val SAMPLE_DT = 1f / SAMPLE_RATE

        /** 单帧写缓冲上限（帧）：≈23ms，stop 响应延迟上限 */
        private const val CHUNK_FRAMES = 1_024

        /** 母线增益（配合 tanh 软限幅） */
        private const val MASTER_GAIN = 1.4f

        // ==================== 单路增益（满幅 1.0 归一化） ====================
        private const val WHITE_GAIN = 0.5f
        private const val PINK_GAIN = 0.8f
        private const val BROWN_GAIN = 0.7f
        private const val RAIN_GAIN = 0.8f
        private const val WIND_GAIN = 0.9f
        private const val OCEAN_GAIN = 0.9f
        private const val FIRE_GAIN = 0.8f
        private const val PAGE_GAIN = 0.9f
        private const val KEY_GAIN = 0.9f
        private const val MURMUR_GAIN = 0.9f

        // ==================== 噪声参数 ====================
        private const val PINK_SCALE = 0.22f
        private const val BROWN_SCALE = 3.5f

        // ==================== 雨声参数 ====================
        private const val RAIN_LP_ALPHA = 0.12f
        private const val RAIN_HP_ALPHA = 0.35f

        /** 雨滴触发率（个 / 秒） */
        private const val RAIN_DROP_RATE = 40f

        // ==================== 风声参数 ====================
        private const val WIND_LP_MIN = 0.02f
        private const val WIND_LP_MAX = 0.05f
        private const val WIND_LFO_HZ = 0.07f
        private const val WIND_GUST_HZ = 0.043f

        // ==================== 海浪参数 ====================
        private const val OCEAN_LP_ALPHA = 0.02f
        private const val OCEAN_HP_ALPHA = 0.05f

        /** 海浪周期 ≈12 秒 */
        private const val OCEAN_WAVE_HZ = 1f / 12f

        // ==================== 篝火参数 ====================
        private const val FIRE_LP_ALPHA = 0.01f

        /** 噼啪触发率（个 / 秒） */
        private const val FIRE_CRACKLE_RATE = 90f

        /** 低频次爆裂触发率（个 / 秒） */
        private const val FIRE_POP_RATE = 6f

        // ==================== 翻书参数 ====================
        private const val PAGE_LP_ALPHA = 0.15f

        /** 翻书哗啦触发率（次 / 秒） */
        private const val PAGE_SWISH_RATE = 18f

        // ==================== 键盘参数 ====================

        /** 敲击触发率（个 / 秒） */
        private const val KEY_TAP_RATE = 66f

        // ==================== 远处交谈参数 ====================
        private const val MURMUR_LP_ALPHA = 0.03f
        private const val MURMUR_ENV_SLEW = 0.002f
        private const val MURMUR_VOICE1_HZ = 3.2f
        private const val MURMUR_VOICE2_HZ = 4.1f
    }
}
