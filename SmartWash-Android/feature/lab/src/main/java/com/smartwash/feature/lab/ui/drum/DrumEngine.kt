package com.smartwash.feature.lab.ui.drum

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** 音序器步数（16 步） */
const val DRUM_STEP_COUNT = 16

/** 声部数量（8 种鼓声） */
const val DRUM_VOICE_COUNT = 8

/** 鼓声部枚举（顺序即网格行序，与合成器 voice 表一致） */
enum class DrumVoice(val durationSec: Float) {
    KICK(0.28f),
    SNARE(0.22f),
    HIHAT(0.09f),
    CLAP(0.28f),
    TOM(0.32f),
    COWBELL(0.28f),
    RIM(0.06f),
    CYMBAL(0.95f),
}

/**
 * 不可变节奏型：8 声部 × 16 步的位图，下标 = voice * [DRUM_STEP_COUNT] + step。
 * 拷贝写入（[withStep]）保证引擎线程读到的引用永远是完整快照，无需加锁。
 */
class DrumPattern private constructor(private val bits: BooleanArray) {

    fun isEnabled(voice: Int, step: Int): Boolean = bits[voice * DRUM_STEP_COUNT + step]

    fun withStep(voice: Int, step: Int, enabled: Boolean): DrumPattern {
        val next = bits.copyOf()
        next[voice * DRUM_STEP_COUNT + step] = enabled
        return DrumPattern(next)
    }

    companion object {
        val EMPTY = DrumPattern(BooleanArray(DRUM_VOICE_COUNT * DRUM_STEP_COUNT))

        /** 从 8 行 × 16 字符的 'x'/'.' 谱面构建（预设用） */
        fun fromRows(rows: List<String>): DrumPattern {
            require(rows.size == DRUM_VOICE_COUNT) { "需要 $DRUM_VOICE_COUNT 行" }
            val bits = BooleanArray(DRUM_VOICE_COUNT * DRUM_STEP_COUNT)
            rows.forEachIndexed { voice, row ->
                require(row.length == DRUM_STEP_COUNT) { "第 $voice 行长度需为 $DRUM_STEP_COUNT" }
                for (step in 0 until DRUM_STEP_COUNT) {
                    bits[voice * DRUM_STEP_COUNT + step] = row[step] == 'x'
                }
            }
            return DrumPattern(bits)
        }
    }
}

/**
 * 滚筒节拍机音频引擎（零音频资产）。
 *
 * 合成：8 种鼓声在首次启动时程序化合成（正弦扫频 + 噪声 + 指数包络），预渲染为 PCM 缓冲；
 * 调度：以 AudioTrack 时间戳为音频时钟，调度线程每 [SCHEDULER_TICK_MS] 醒一次，把未来
 * [LOOKAHEAD_MS] 窗口内的步渲染进流缓冲——学 Tone.js 的 lookahead 思想，避免 setInterval
 * 漂移；写缓冲始终维持 [LOOKAHEAD_MS] 的余量，head 读数抖动由缓冲自愈。
 *
 * 线程模型：所有 AudioTrack 操作在单线程执行器内完成（write 阻塞，禁止主线程）；
 * [pattern]/[bpm] 以 @Volatile 发布不可变快照，调度线程无锁读取；开始/停止用代际计数
 * （[generation]）避免旧调度线程因新 start 而赖着不走。
 */
class DrumEngine {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "drum-engine").apply { isDaemon = true }
    }

    private val generation = AtomicInteger(0)
    private val voicesLock = Any()

    @Volatile private var bpm: Int = DEFAULT_BPM
    @Volatile private var pattern: DrumPattern = DrumPattern.EMPTY
    @Volatile private var playing: Boolean = false
    @Volatile private var released: Boolean = false
    @Volatile private var voices: Array<FloatArray> = emptyArray()

    private var future: Future<*>? = null

    private val _currentStep = MutableStateFlow(-1)

    /** 当前发声步（0..15；停止时 -1），驱动网格播放头 */
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    fun setBpm(value: Int) {
        bpm = value.coerceIn(MIN_BPM, MAX_BPM)
    }

    fun setPattern(value: DrumPattern) {
        pattern = value
    }

    fun start() {
        if (playing || released) return
        playing = true
        val gen = generation.incrementAndGet()
        future = executor.submit { runScheduler(gen) }
    }

    fun stop() {
        if (!playing) return
        playing = false
        generation.incrementAndGet()
        future?.cancel(true)
        future = null
        _currentStep.value = -1
    }

    fun release() {
        released = true
        playing = false
        generation.incrementAndGet()
        future?.cancel(true)
        future = null
        executor.shutdownNow()
    }

    // ==================== 调度内核 ====================

    private fun runScheduler(gen: Int) {
        val track = createTrack() ?: run {
            playing = false
            _currentStep.value = -1
            return
        }
        ensureVoices()
        val activeNotes = mutableListOf<ScheduledNote>()
        val chunkFloats = FloatArray(MAX_CHUNK_FRAMES)
        val chunkShorts = ShortArray(MAX_CHUNK_FRAMES)
        var writeFrame = 0L
        var nextStepFrame = 0L
        var nextStepIndex = 0
        var anchored = false
        try {
            track.play()
            while (playing && !released && generation.get() == gen) {
                val head = headFrame(track)
                if (!anchored) {
                    // 首帧对齐：第一步落在 lookahead 之后，避免起始被削
                    nextStepFrame = head + lookaheadFrames()
                    anchored = true
                }
                val target = head + lookaheadFrames()
                val stepFrames = stepFrames()
                while (nextStepFrame < target) {
                    scheduleStep(activeNotes, nextStepIndex, nextStepFrame)
                    nextStepIndex = (nextStepIndex + 1) % DRUM_STEP_COUNT
                    nextStepFrame += stepFrames
                }
                var remaining = (target - writeFrame).toInt()
                while (remaining > 0) {
                    val chunk = min(remaining, MAX_CHUNK_FRAMES)
                    writeChunk(track, activeNotes, writeFrame, chunk, chunkFloats, chunkShorts)
                    writeFrame += chunk
                    remaining -= chunk
                }
                updateCurrentStep(head, nextStepFrame, nextStepIndex)
                Thread.sleep(SCHEDULER_TICK_MS)
            }
            track.stop()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: Throwable) {
            // 音频设备异常：静默停止（玩具页不崩溃）
        } finally {
            track.release()
            if (generation.get() == gen) _currentStep.value = -1
        }
    }

    /** 把某一步触发的声部登记为活跃音符（引擎线程内调用） */
    private fun scheduleStep(activeNotes: MutableList<ScheduledNote>, step: Int, frame: Long) {
        val pat = pattern
        for (voice in 0 until DRUM_VOICE_COUNT) {
            if (pat.isEnabled(voice, step)) {
                activeNotes.add(ScheduledNote(voice, frame))
            }
        }
    }

    /** 混合 [startFrame, startFrame + frames) 区间的活跃音符并写入 AudioTrack */
    private fun writeChunk(
        track: AudioTrack,
        activeNotes: MutableList<ScheduledNote>,
        startFrame: Long,
        frames: Int,
        floats: FloatArray,
        shorts: ShortArray,
    ) {
        floats.fill(0f, 0, frames)
        var index = 0
        while (index < activeNotes.size) {
            val note = activeNotes[index]
            val voice = voices[note.voice]
            val noteStart = note.frame
            val noteEnd = noteStart + voice.size
            val overlapStart = maxOf(startFrame, noteStart)
            val overlapEnd = minOf(startFrame + frames.toLong(), noteEnd)
            if (overlapStart < overlapEnd) {
                val dst = (overlapStart - startFrame).toInt()
                val src = (overlapStart - noteStart).toInt()
                val length = (overlapEnd - overlapStart).toInt()
                for (i in 0 until length) {
                    floats[dst + i] += voice[src + i]
                }
            }
            if (noteEnd <= startFrame + frames) {
                activeNotes.removeAt(index)
            } else {
                index++
            }
        }
        for (i in 0 until frames) {
            // tanh 软限幅：多声部叠加不削顶，保留鼓声的冲击感
            val v = tanh(floats[i] * MASTER_GAIN)
            shorts[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        track.write(shorts, 0, frames)
    }

    /**
     * 由音频时钟反推当前发声步：[nextStepFrame] 是下一个待调度步的帧，
     * [head] 落在它前方 [stepsAhead] 步处，故发声步 = 下一待调度步 - [stepsAhead]。
     */
    private fun updateCurrentStep(head: Long, nextStepFrame: Long, nextStepIndex: Int) {
        val stepFrames = stepFrames()
        val delta = nextStepFrame - head
        val stepsAhead = ceil(delta.toDouble() / stepFrames).toInt().coerceAtLeast(1)
        val idx = (nextStepIndex - stepsAhead).mod(DRUM_STEP_COUNT)
        if (idx != _currentStep.value) _currentStep.value = idx
    }

    private fun headFrame(track: AudioTrack): Long {
        val timestamp = AudioTimestamp()
        if (track.getTimestamp(timestamp)) return timestamp.framePosition
        return track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
    }

    private fun stepFrames(): Long = (60.0 / bpm / 4.0 * SAMPLE_RATE).toLong()

    private fun lookaheadFrames(): Long = SAMPLE_RATE * LOOKAHEAD_MS / 1000L

    // ==================== 合成 ====================

    private fun createTrack(): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) return null
        // 1 秒缓冲（16-bit 单声道）：lookahead 100ms 的写余量，write 不阻塞
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE * 2)
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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

    private fun ensureVoices() {
        if (voices.isNotEmpty()) return
        synchronized(voicesLock) {
            if (voices.isNotEmpty()) return
            voices = DrumVoice.entries.map { renderVoice(it) }.toTypedArray()
        }
    }

    /** 预渲染单个鼓声到 PCM 浮点缓冲，并按峰值归一化到 [VOICE_PEAK] */
    private fun renderVoice(voice: DrumVoice): FloatArray {
        val sampleCount = (voice.durationSec * SAMPLE_RATE).toInt()
        val out = FloatArray(sampleCount)
        val random = Random(voice.ordinal * 1000 + 17)
        val state = VoiceRenderState()
        var peak = 0f
        for (i in 0 until sampleCount) {
            val t = i.toFloat() / SAMPLE_RATE
            val sample = renderSample(voice, t, random, state)
            out[i] = sample
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
        }
        val normalize = if (peak > 0f) VOICE_PEAK / peak else 1f
        for (i in 0 until sampleCount) out[i] *= normalize
        return out
    }

    /**
     * 程序化鼓声：正弦扫频（kick/tom）+ 高通噪声（snare/hihat/clap/cymbal）+ 指数包络。
     * [state.lastNoise] 保存上一采样噪声，用于一阶高通（n[i] - n[i-1]）。
     */
    private fun renderSample(
        voice: DrumVoice,
        t: Float,
        random: Random,
        state: VoiceRenderState,
    ): Float {
        val noise = random.nextFloat() * 2f - 1f
        val highpassed = noise - state.lastNoise
        state.lastNoise = noise
        val twoPi = 2f * PI.toFloat()
        return when (voice) {
            DrumVoice.KICK -> {
                val f0 = 120f
                val f1 = 45f
                val k = 16f
                val phase = twoPi * (f0 * (1f - exp(-k * t)) / k + f1 * t)
                val env = exp(-t * 14f)
                val click = if (t < 0.004f) highpassed * (1f - t / 0.004f) * 0.6f else 0f
                sin(phase) * env * 0.9f + click
            }
            DrumVoice.SNARE -> {
                val body = sin(twoPi * 185f * t) * exp(-t * 20f) * 0.5f
                val snap = highpassed * exp(-t * 26f) * 0.6f
                body + snap
            }
            DrumVoice.HIHAT -> {
                val env = exp(-t * 70f)
                highpassed * env * 0.7f + sin(twoPi * 8200f * t) * env * 0.1f
            }
            DrumVoice.CLAP -> {
                val burst = when {
                    t < 0.012f -> 1f
                    t < 0.024f -> 0.2f
                    t < 0.036f -> 0.85f
                    t < 0.048f -> 0.2f
                    t < 0.062f -> 0.6f
                    else -> exp(-(t - 0.062f) * 22f)
                }
                highpassed * burst * 0.75f
            }
            DrumVoice.TOM -> {
                val f0 = 170f
                val f1 = 85f
                val k = 9f
                val phase = twoPi * (f0 * (1f - exp(-k * t)) / k + f1 * t)
                sin(phase) * exp(-t * 11f) * 0.85f
            }
            DrumVoice.COWBELL -> {
                val env = exp(-t * 15f)
                (sin(twoPi * 560f * t) * 0.5f + sin(twoPi * 845f * t) * 0.5f) * env * 0.7f
            }
            DrumVoice.RIM -> {
                val env = exp(-t * 95f)
                (sin(twoPi * 1720f * t) * 0.45f + highpassed * 0.35f) * env * 0.8f
            }
            DrumVoice.CYMBAL -> {
                val env = exp(-t * 4.5f)
                val metallic = sin(twoPi * 5200f * t) * 0.12f + sin(twoPi * 7350f * t) * 0.08f
                (highpassed * 0.55f + metallic) * env * 0.6f
            }
        }
    }

    private class VoiceRenderState {
        var lastNoise: Float = 0f
    }

    private data class ScheduledNote(val voice: Int, val frame: Long)

    companion object {
        const val MIN_BPM = 60
        const val MAX_BPM = 180

        private const val DEFAULT_BPM = 120
        private const val SAMPLE_RATE = 44_100

        /** 调度 lookahead（ms）：调度线程提前写这么多音频，头部读数抖动由缓冲自愈 */
        private const val LOOKAHEAD_MS = 100L

        /** 调度线程唤醒间隔（ms）：必须小于 [LOOKAHEAD_MS]，留出重试余量 */
        private const val SCHEDULER_TICK_MS = 25L

        /** 单次写缓冲上限（帧）：正常每 tick 只写 ~1102 帧，超预算分块写 */
        private const val MAX_CHUNK_FRAMES = 16_384

        /** 单声部归一化峰值（满幅 1.0） */
        private const val VOICE_PEAK = 0.6f

        /** 母线增益（配合 tanh 软限幅） */
        private const val MASTER_GAIN = 1.5f
    }
}
