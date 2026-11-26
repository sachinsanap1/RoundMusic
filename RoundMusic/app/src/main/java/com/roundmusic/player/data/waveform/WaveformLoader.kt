package com.roundmusic.player.data.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.roundmusic.player.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Builds the REAL waveform shown on the Now Playing seek bar.
 * It decodes the song's audio once, measures how loud each small slice is, and keeps [BARS] values
 * between 0 and 1. Nothing is random: quiet parts of the song give short bars, loud parts tall bars.
 *
 * Honest limits: the first time a song is opened the bars appear after a moment (the song is decoded
 * in the background), very long files (over 20 minutes) and formats Android cannot decode show flat
 * bars instead. Results are cached in memory only.
 */
class WaveformLoader(context: Context) {

    private val appContext = context.applicationContext
    private val cache = LruCache<Long, FloatArray>(48)
    private val failed = ConcurrentHashMap.newKeySet<Long>()

    // Decoding is heavy, so only one song at a time.
    private val limiter = Semaphore(1)

    fun cached(song: Song): FloatArray? = cache.get(song.id)

    /** Returns [BARS] values in 0..1, or null if this song's waveform cannot be made. */
    suspend fun load(song: Song): FloatArray? {
        cache.get(song.id)?.let { return it }
        if (song.id in failed) return null
        if (song.durationMs > MAX_DURATION_MS) return null

        return limiter.withPermit {
            cache.get(song.id)?.let { return@withPermit it }
            withContext(Dispatchers.IO) {
                val result = try {
                    decode(song.uri) { ensureActive() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Waveform failed for song ${song.id}", e)
                    null
                }
                if (result != null) cache.put(song.id, result) else failed.add(song.id)
                result
            }
        }
    }

    fun clear() {
        cache.evictAll()
    }

    private fun decode(uri: Uri, checkActive: () -> Unit): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(appContext, uri, null)

            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }
            if (trackIndex < 0) return null

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            if (!format.containsKey(MediaFormat.KEY_DURATION)) return null
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            if (durationUs <= 0) return null

            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            val sums = DoubleArray(BARS)
            val counts = LongArray(BARS)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var unsupportedPcm = false

            while (!outputDone) {
                checkActive()

                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex) ?: return null
                        val size = extractor.readSampleData(inBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = decoder.outputFormat
                        if (out.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            out.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT
                        ) {
                            unsupportedPcm = true
                        }
                    }

                    outIndex >= 0 -> {
                        if (info.size > 0 && !unsupportedPcm) {
                            val outBuffer = decoder.getOutputBuffer(outIndex)
                            if (outBuffer != null) {
                                outBuffer.position(info.offset)
                                outBuffer.limit(info.offset + info.size)
                                val samples = outBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()

                                val bucket = ((info.presentationTimeUs.toDouble() / durationUs) * BARS)
                                    .toInt().coerceIn(0, BARS - 1)
                                var sum = 0.0
                                var counted = 0L
                                var i = 0
                                val n = samples.remaining()
                                while (i < n) {
                                    sum += abs(samples.get(i).toInt())
                                    counted++
                                    i += SAMPLE_STEP
                                }
                                sums[bucket] += sum
                                counts[bucket] += counted
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                if (unsupportedPcm) return null
            }

            // Average loudness per slice, then scale so the loudest slice is 1.0.
            val levels = FloatArray(BARS) { i ->
                if (counts[i] > 0) (sums[i] / counts[i]).toFloat() else 0f
            }
            val max = levels.maxOrNull() ?: 0f
            if (max <= 0f) return null
            // The square root makes quiet parts visible without changing which parts are louder.
            return FloatArray(BARS) { i -> sqrt((levels[i] / max).toDouble()).toFloat() }
        } finally {
            try {
                codec?.stop()
            } catch (e: Exception) {
                // already stopped
            }
            try {
                codec?.release()
            } catch (e: Exception) {
                // nothing else to do
            }
            extractor.release()
        }
    }

    companion object {
        /** How many bars the seek bar shows. */
        const val BARS = 48

        private const val TAG = "WaveformLoader"
        private const val TIMEOUT_US = 10_000L
        private const val SAMPLE_STEP = 4
        private const val MAX_DURATION_MS = 20L * 60 * 1000
    }
}
