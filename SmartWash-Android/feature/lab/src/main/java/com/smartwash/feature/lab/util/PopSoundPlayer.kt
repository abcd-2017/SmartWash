package com.smartwash.feature.lab.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.smartwash.common.utils.AppLogger
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 泡泡纸 pop 短音合成器（零音频资产）：每次戳破现场合成一段静态 PCM——
 * 短正弦 + 指数衰减 + 随机音高（模拟大小泡泡的音色差），经 AudioTrack
 * MODE_STATIC 播完即弃。
 *
 * 取舍：合成/播放/释放在单线程执行器内串行完成（AudioTrack write 阻塞，
 * 禁止主线程调用）；整段实现约 40 行，低于 60 行降级阈值，保留声音反馈通道。
 * 声音是增强反馈（任何失败静默降级为无声），戳泡的兜底反馈是触觉。
 */
class PopSoundPlayer {

    private val executor = Executors.newSingleThreadExecutor()

    /** release 后 pop() 静默丢弃（页面退出后仍可能有在途点击事件） */
    @Volatile
    private var released = false

    /** 播一声 pop（音高随机；合成或播放失败静默——声音是增强非必需） */
    fun pop() {
        if (released) return
        executor.execute { if (!released) playPop() }
    }

    /** 页面销毁时调用（DisposableEffect onDispose）；在途任务经 finally 回收 track */
    fun release() {
        released = true
        executor.shutdownNow()
    }

    private fun playPop() {
        val sampleCount = SAMPLE_RATE * POP_DURATION_MS / 1000
        // 音高随机：连续谱近似大小泡泡混排（大泡低沉、小泡清脆）
        val frequency = 420f + Random.nextFloat() * 480f
        val pcm = FloatArray(sampleCount) { i ->
            val t = i.toFloat() / SAMPLE_RATE
            sin(2.0 * PI * frequency * t).toFloat() * exp(-t * DECAY_RATE) * AMPLITUDE
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(sampleCount * FLOAT_BYTES)
            .build()
        try {
            // write 返回负错误码（实例未初始化等）即放弃本次发声，finally 兜底 release
            val written = track.write(pcm, 0, sampleCount, AudioTrack.WRITE_BLOCKING)
            if (written < 0) return
            track.play()
            // MODE_STATIC 播完不会自动释放：睡过播放时长再回收（执行器线程内串行，不阻塞主线程）
            Thread.sleep(POP_DURATION_MS + RELEASE_LAG_MS.toLong())
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Throwable) {
            // AudioTrack 未初始化 / play() 抛 IllegalStateException 等：静默降级为无声
            // （声音是增强反馈非必需；catch Throwable 防执行器线程未捕获异常）
            // 戳泡为高频路径，失败仅 debug 留痕（release 静默）
            AppLogger.debug(TAG, "pop 音效播放失败，降级为无声", e)
        } finally {
            track.release()
        }
    }

    companion object {

        private const val TAG = "PopSoundPlayer"

        private const val SAMPLE_RATE = 44_100

        /** 单声 pop 时长（ms）：短到可以连戳不糊 */
        private const val POP_DURATION_MS = 90

        /** 指数衰减速率（1/s）：约 90ms 衰减到不可闻 */
        private const val DECAY_RATE = 45f

        /** 峰值增益（留余量防爆音） */
        private const val AMPLITUDE = 0.5f

        private const val FLOAT_BYTES = 4

        /** 播放尾部缓冲（ms）：release 前留一点余量避免削尾 */
        private const val RELEASE_LAG_MS = 40
    }
}
