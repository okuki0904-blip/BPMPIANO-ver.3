package com.example.bpmpiano

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 电贝斯音色合成器：低次谐波为主、基频延音较长、高次谐波衰减更快（模拟拨弦后由亮转暗的音色变化），
 * 并叠加一个极短的"拨弦"瞬态噪声成分增加颗粒感。
 * 通过 MODE_STATIC 的 AudioTrack 预先渲染波形，调用 trigger() 即可从头重新播放，
 * 从而实现快速重复触发（连续音符）。
 */
class BassVoice(frequency: Float, sampleRate: Int = 44100, duration: Double = 2.2) {

    private val track: AudioTrack

    init {
        val totalSamples = (sampleRate * duration).toInt()
        val samples = ShortArray(totalSamples)

        // 电贝斯谐波幅度权重：基频最强，往上逐渐减弱
        val harmonicAmps = doubleArrayOf(1.0, 0.7, 0.45, 0.25, 0.15, 0.08)
        val baseDecay = 0.9   // 基频衰减速度，数值越小延音越长
        val decayStep = 0.55  // 每往上一个泛音衰减速度增加量，让高频更快消失、低频更持久

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            var sample = 0.0
            for ((h, amp) in harmonicAmps.withIndex()) {
                val harmonicFreq = frequency * (h + 1)
                val decayRate = baseDecay + h * decayStep
                val envelope = exp(-t * decayRate)
                sample += amp * envelope * sin(2.0 * PI * harmonicFreq * t)
            }
            // 拨弦瞬态：极短促的高频冲击，模拟拨片/手指拨弦瞬间的颗粒感
            val pluckTransient = 0.25 * exp(-t * 60.0) * sin(2.0 * PI * frequency * 3.3 * t)
            sample += pluckTransient

            // 极短的起振淡入，避免触发瞬间产生咔哒声
            val attack = if (t < 0.003) t / 0.003 else 1.0
            val value = sample * attack * 0.55 // 防止削波
            samples[i] = (value * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }

        val bufferSizeBytes = totalSamples * 2

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        track = AudioTrack(
            audioAttributes,
            audioFormat,
            bufferSizeBytes,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(samples, 0, samples.size)
    }

    /** 从头重新触发播放，实现连续快速的音符 */
    fun trigger() {
        try {
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        track.release()
    }
}
