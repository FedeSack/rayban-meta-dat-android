package com.fedesack.raybanmetadat

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class GazeHevcDecoder : GazeHevcSink {
    private data class Packet(
        val bytes: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
        val width: Int,
        val height: Int,
    )

    private val queue = LinkedBlockingQueue<Packet>(8)
    private val latest = AtomicReference<GazeYuv?>(null)
    private val inFlight = Any()
    private val setupLock = Any()
    @Volatile private var codec: MediaCodec? = null
    @Volatile private var reader: ImageReader? = null
    @Volatile private var thread: HandlerThread? = null
    @Volatile private var configured = false
    @Volatile private var released = true
    @Volatile private var failed = false
    private var width = 0
    private var height = 0

    override fun start() {
        synchronized(setupLock) {
            releaseLocked()
            released = false
            failed = false
            configured = false
            latest.set(null)
            queue.clear()
        }
    }

    override fun stop() {
        synchronized(setupLock) {
            releaseLocked()
        }
    }

    override fun offer(frame: GazeRawFrame) {
        if (released || failed) return
        try {
            if (frame.width > 0 && frame.height > 0) {
                width = frame.width
                height = frame.height
            }
            ensureCodec()
            if (released || failed || !configured) return
            val flags = if (frame.codecConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
            val packet =
                Packet(
                    bytes = frame.bytes,
                    presentationTimeUs = frame.presentationTimeUs,
                    flags = flags,
                    width = frame.width,
                    height = frame.height,
                )
            if (!queue.offer(packet)) {
                queue.poll()
                queue.offer(packet)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "hevc offer skipped: ${t.message}")
        }
    }

    override fun latestYuv(): GazeYuv? = latest.get()

    private fun ensureCodec() {
        if (configured || failed || released) return
        synchronized(setupLock) {
            if (configured || failed || released) return
            if (width <= 0 || height <= 0) return
            try {
                val worker = HandlerThread("gaze-hevc", Process.THREAD_PRIORITY_VIDEO)
                worker.start()
                thread = worker
                val handler = Handler(worker.looper)
                val imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
                imageReader.setOnImageAvailableListener({ incoming -> onDecodedImage(incoming) }, handler)
                reader = imageReader
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 24)
                val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                decoder.configure(format, imageReader.surface, null, 0)
                decoder.setCallback(codecCallback(), handler)
                decoder.start()
                codec = decoder
                configured = true
            } catch (t: Throwable) {
                Log.w(TAG, "hevc decoder unavailable: ${t.message}")
                teardownPartial()
                failed = true
            }
        }
    }

    private fun onDecodedImage(incoming: ImageReader) {
        val image =
            try {
                incoming.acquireLatestImage()
            } catch (t: Throwable) {
                Log.w(TAG, "hevc acquire skipped: ${t.message}")
                return
            } ?: return
        synchronized(inFlight) {
            if (released) {
                closeQuietly(image)
                return
            }
            try {
                val yuv = imageToNv21(image)
                if (yuv != null && !released) {
                    latest.set(yuv)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "hevc frame skipped: ${t.message}")
            } finally {
                closeQuietly(image)
            }
        }
    }

    private fun codecCallback(): MediaCodec.Callback =
        object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(
                codec: MediaCodec,
                index: Int,
            ) {
                try {
                    val input = codec.getInputBuffer(index)
                    if (input == null || released || failed) {
                        queueQuietly(codec, index)
                        return
                    }
                    val packet = queue.poll(200, TimeUnit.MILLISECONDS)
                    if (packet == null) {
                        queueQuietly(codec, index)
                        return
                    }
                    if (packet.bytes.size > input.remaining()) {
                        Log.w(TAG, "hevc packet too large (${packet.bytes.size} > ${input.remaining()})")
                        queueQuietly(codec, index)
                        return
                    }
                    input.clear()
                    input.put(packet.bytes)
                    codec.queueInputBuffer(
                        index,
                        0,
                        packet.bytes.size,
                        packet.presentationTimeUs,
                        packet.flags,
                    )
                } catch (t: Throwable) {
                    Log.w(TAG, "hevc input skipped: ${t.message}")
                    queueQuietly(codec, index)
                }
            }

            override fun onOutputBufferAvailable(
                codec: MediaCodec,
                index: Int,
                info: MediaCodec.BufferInfo,
            ) {
                try {
                    codec.releaseOutputBuffer(index, info.size > 0 && !released)
                } catch (t: Throwable) {
                    Log.w(TAG, "hevc output skipped: ${t.message}")
                }
            }

            override fun onError(
                codec: MediaCodec,
                e: MediaCodec.CodecException,
            ) {
                Log.w(TAG, "hevc codec error: ${e.diagnosticInfo ?: e.message}")
            }

            override fun onOutputFormatChanged(
                codec: MediaCodec,
                format: MediaFormat,
            ) = Unit
        }

    private fun imageToNv21(image: Image): GazeYuv? {
        val planes =
            try {
                image.planes
            } catch (t: Throwable) {
                Log.w(TAG, "hevc planes unavailable: ${t.message}")
                return null
            }
        if (planes == null || planes.size < 3) {
            Log.w(TAG, "hevc unexpected plane count ${planes?.size}")
            return null
        }
        val y = planes[0] ?: return null
        val u = planes[1] ?: return null
        val v = planes[2] ?: return null
        val yBuf = y.buffer ?: return null
        val uBuf = u.buffer ?: return null
        val vBuf = v.buffer ?: return null
        if (yBuf.remaining() <= 0 || uBuf.remaining() <= 0 || vBuf.remaining() <= 0) {
            Log.w(TAG, "hevc empty plane buffer")
            return null
        }
        val crop =
            try {
                image.cropRect
            } catch (_: Throwable) {
                null
            }
        val cropW = crop?.width() ?: 0
        val cropH = crop?.height() ?: 0
        val outW = if (cropW > 0) cropW else image.width
        val outH = if (cropH > 0) cropH else image.height
        if (outW <= 0 || outH <= 0) {
            Log.w(TAG, "hevc invalid image size ${image.width}x${image.height}")
            return null
        }
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = outW,
                height = outH,
                y = yBuf,
                yRowStride = y.rowStride,
                yPixelStride = y.pixelStride,
                u = uBuf,
                uRowStride = u.rowStride,
                uPixelStride = u.pixelStride,
                v = vBuf,
                vRowStride = v.rowStride,
                vPixelStride = v.pixelStride,
                cropLeft = crop?.left ?: 0,
                cropTop = crop?.top ?: 0,
            )
        return GazeYuv(nv21, outW and 0x7FFFFFFE, outH and 0x7FFFFFFE, nv21 = true)
    }

    private fun releaseLocked() {
        released = true
        configured = false
        queue.clear()
        latest.set(null)
        synchronized(inFlight) {
            // Wait for an in-flight Image → NV21 copy before closing the reader.
        }
        try {
            codec?.stop()
        } catch (_: Exception) {
        }
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        reader = null
        thread?.quitSafely()
        thread = null
        width = 0
        height = 0
        failed = false
    }

    private fun teardownPartial() {
        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
        try {
            reader?.close()
        } catch (_: Exception) {
        }
        reader = null
        thread?.quitSafely()
        thread = null
        configured = false
    }

    private fun queueQuietly(
        codec: MediaCodec,
        index: Int,
    ) {
        try {
            codec.queueInputBuffer(index, 0, 0, 0, 0)
        } catch (_: Throwable) {
        }
    }

    private fun closeQuietly(image: Image) {
        try {
            image.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "RaybanDat/Gaze"
    }
}
