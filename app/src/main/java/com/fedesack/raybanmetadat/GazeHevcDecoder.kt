package com.fedesack.raybanmetadat

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
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
    @Volatile private var codec: MediaCodec? = null
    @Volatile private var reader: ImageReader? = null
    @Volatile private var thread: HandlerThread? = null
    @Volatile private var configured = false
    private var width = 0
    private var height = 0

    override fun start() {
        configured = false
        latest.set(null)
        queue.clear()
    }

    override fun stop() {
        configured = false
        queue.clear()
        latest.set(null)
        try {
            codec?.stop()
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
    }

    override fun offer(frame: GazeRawFrame) {
        if (frame.width > 0 && frame.height > 0) {
            width = frame.width
            height = frame.height
        }
        ensureCodec()
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
    }

    override fun latestYuv(): GazeYuv? = latest.get()

    private fun ensureCodec() {
        if (configured) return
        if (width <= 0 || height <= 0) return
        val worker = HandlerThread("gaze-hevc", Process.THREAD_PRIORITY_VIDEO)
        worker.start()
        thread = worker
        val handler = Handler(worker.looper)
        val imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
        imageReader.setOnImageAvailableListener({ incoming ->
            val image = incoming.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                latest.set(imageToNv21(image))
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }, handler)
        reader = imageReader
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 24)
        val created =
            try {
                val decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
                decoder.configure(format, imageReader.surface, null, 0)
                decoder
            } catch (_: Exception) {
                imageReader.close()
                reader = null
                worker.quitSafely()
                thread = null
                return
            }
        created.setCallback(
            object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                ) {
                    val input = codec.getInputBuffer(index)
                    val packet = queue.poll(200, TimeUnit.MILLISECONDS)
                    if (input == null || packet == null) {
                        codec.queueInputBuffer(index, 0, 0, 0, 0)
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
                }

                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo,
                ) {
                    codec.releaseOutputBuffer(index, info.size > 0)
                }

                override fun onError(
                    codec: MediaCodec,
                    e: MediaCodec.CodecException,
                ) = Unit

                override fun onOutputFormatChanged(
                    codec: MediaCodec,
                    format: MediaFormat,
                ) = Unit
            },
            handler,
        )
        created.start()
        codec = created
        configured = true
    }

    private fun imageToNv21(image: Image): GazeYuv {
        val y = image.planes[0]
        val u = image.planes[1]
        val v = image.planes[2]
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = image.width,
                height = image.height,
                y = y.buffer,
                yRowStride = y.rowStride,
                u = u.buffer,
                uRowStride = u.rowStride,
                uPixelStride = u.pixelStride,
                v = v.buffer,
                vRowStride = v.rowStride,
                vPixelStride = v.pixelStride,
            )
        return GazeYuv(nv21, image.width, image.height, nv21 = true)
    }
}
