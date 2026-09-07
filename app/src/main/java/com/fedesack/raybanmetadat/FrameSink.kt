package com.fedesack.raybanmetadat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.Surface
import com.meta.wearable.dat.camera.types.VideoFrame
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.ArrayDeque
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class PresentedFrame(
    val presentationTimeUs: Long,
    val receivedElapsedMs: Long,
    val width: Int,
    val height: Int,
    val decodePath: DecodePath,
)

class FrameSink(
    private val onPresented: (PresentedFrame) -> Unit,
    private val onFirstFrame: () -> Unit,
    private val onQueueDrop: () -> Unit = {},
    private val onDecodeError: (String) -> Unit = {},
    private val onDecoderFormat: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    private data class Packet(
        val bytes: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int,
        val receivedElapsedMs: Long,
    )

    private val queue = LinkedBlockingQueue<Packet>(8)
    private val arrivals = ArrayDeque<Long>()
    private val first = AtomicBoolean(true)
    @Volatile private var surface: Surface? = null
    @Volatile private var codec: MediaCodec? = null
    @Volatile private var decoderThread: HandlerThread? = null
    @Volatile private var configured = false
    @Volatile private var decodePath = DecodePath.HEVC
    private var width = 0
    private var height = 0

    fun attach(surface: Surface) {
        this.surface = surface
    }

    fun detach() {
        queue.clear()
        synchronized(arrivals) { arrivals.clear() }
        configured = false
        first.set(true)
        try {
            codec?.stop()
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null
        decoderThread?.quitSafely()
        decoderThread = null
        surface = null
    }

    fun render(
        frame: VideoFrame,
        receivedElapsedMs: Long,
    ) {
        val dest = surface ?: return
        width = frame.width
        height = frame.height
        if (frame.isCompressed) {
            decodePath = DecodePath.HEVC
            renderHevc(frame, receivedElapsedMs)
        } else {
            decodePath = DecodePath.YUV
            renderYuv(frame, dest, receivedElapsedMs)
        }
    }

    private fun renderHevc(
        frame: VideoFrame,
        receivedElapsedMs: Long,
    ) {
        ensureCodec()
        val flags =
            if (frame.isCodecConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG
            else 0
        val packet =
            Packet(
                bytes = copyBuffer(frame.buffer),
                presentationTimeUs = frame.presentationTimeUs,
                flags = flags,
                receivedElapsedMs = receivedElapsedMs,
            )
        if (!queue.offer(packet)) {
            queue.poll()
            queue.offer(packet)
            onQueueDrop()
        }
    }

    private fun ensureCodec() {
        if (configured) return
        val dest = surface ?: return
        if (width <= 0 || height <= 0) return
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, width, height)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, 24)
        val thread = HandlerThread("dat-hevc", Process.THREAD_PRIORITY_VIDEO)
        thread.start()
        decoderThread = thread
        val created = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
        created.configure(format, dest, null, 0)
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
                    if (packet.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        synchronized(arrivals) { arrivals.addLast(packet.receivedElapsedMs) }
                    }
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
                    val render = info.size > 0
                    codec.releaseOutputBuffer(index, render)
                    if (render) {
                        val received =
                            synchronized(arrivals) {
                                arrivals.pollFirst() ?: SystemClock.elapsedRealtime()
                            }
                        markPresented(info.presentationTimeUs, received)
                    }
                }

                override fun onError(
                    codec: MediaCodec,
                    e: MediaCodec.CodecException,
                ) {
                    onDecodeError(e.diagnosticInfo ?: e.message ?: "codec")
                }

                override fun onOutputFormatChanged(
                    codec: MediaCodec,
                    format: MediaFormat,
                ) {
                    val outWidth = format.getInteger(MediaFormat.KEY_WIDTH)
                    val outHeight = format.getInteger(MediaFormat.KEY_HEIGHT)
                    if (outWidth > 0 && outHeight > 0) {
                        onDecoderFormat(outWidth, outHeight)
                    }
                }
            },
            Handler(thread.looper),
        )
        created.start()
        codec = created
        configured = true
    }

    private fun renderYuv(
        frame: VideoFrame,
        dest: Surface,
        receivedElapsedMs: Long,
    ) {
        val nv21 = i420ToNv21(frame.buffer, frame.width, frame.height)
        val yuv = YuvImage(nv21, ImageFormat.NV21, frame.width, frame.height, null)
        val jpeg = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, frame.width, frame.height), 80, jpeg)
        val bitmap = BitmapFactory.decodeByteArray(jpeg.toByteArray(), 0, jpeg.size()) ?: return
        drawBitmap(dest, bitmap)
        markPresented(frame.presentationTimeUs, receivedElapsedMs)
    }

    private fun drawBitmap(
        dest: Surface,
        bitmap: Bitmap,
    ) {
        val canvas = dest.lockHardwareCanvas()
        try {
            val sx = canvas.width.toFloat() / bitmap.width.toFloat()
            val sy = canvas.height.toFloat() / bitmap.height.toFloat()
            val scale = maxOf(sx, sy)
            val dx = (canvas.width - bitmap.width * scale) / 2f
            val dy = (canvas.height - bitmap.height * scale) / 2f
            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.restore()
        } finally {
            dest.unlockCanvasAndPost(canvas)
        }
        bitmap.recycle()
    }

    private fun markPresented(
        presentationTimeUs: Long,
        receivedElapsedMs: Long,
    ) {
        if (first.compareAndSet(true, false)) {
            onFirstFrame()
        }
        onPresented(
            PresentedFrame(
                presentationTimeUs = presentationTimeUs,
                receivedElapsedMs = receivedElapsedMs,
                width = width,
                height = height,
                decodePath = decodePath,
            ),
        )
    }

    private fun copyBuffer(buffer: ByteBuffer): ByteArray {
        val duplicate = buffer.duplicate()
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    private fun i420ToNv21(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
    ): ByteArray {
        val ySize = width * height
        val cSize = ySize / 4
        val src = buffer.duplicate()
        val nv21 = ByteArray(ySize + cSize * 2)
        src.get(nv21, 0, ySize)
        val u = ByteArray(cSize)
        val v = ByteArray(cSize)
        if (src.remaining() >= cSize * 2) {
            src.get(u)
            src.get(v)
        }
        var o = ySize
        for (i in 0 until cSize) {
            nv21[o++] = v[i]
            nv21[o++] = u[i]
        }
        return nv21
    }
}
