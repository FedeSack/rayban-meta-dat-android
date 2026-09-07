package com.fedesack.raybanmetadat

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class YuvJpegNv21Test {
    @Test
    fun packedI420PlanesBecomeVuInterleavedNv21() {
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = 4,
                height = 2,
                y = bytes(10, 11, 12, 13, 20, 21, 22, 23),
                yRowStride = 4,
                u = bytes(0x80, 0x81),
                uRowStride = 2,
                uPixelStride = 1,
                v = bytes(0xC0, 0xC1),
                vRowStride = 2,
                vPixelStride = 1,
            )
        assertArrayEquals(
            byteArrayOf(
                10, 11, 12, 13, 20, 21, 22, 23,
                0xC0.toByte(), 0x80.toByte(), 0xC1.toByte(), 0x81.toByte(),
            ),
            nv21,
        )
    }

    @Test
    fun lastRowHasNoStridePaddingAndPaddingBytesAreIgnored() {
        val yStride = 8
        val y = ByteArray(YuvJpeg.planeAccessibleBytes(yStride, 1, 4, 2)) { 0 }
        // row 0: 4 samples + 4 pad; row 1: 4 samples, no pad
        y[0] = 10
        y[1] = 11
        y[2] = 12
        y[3] = 13
        y[4] = 99
        y[5] = 99
        y[6] = 99
        y[7] = 99
        y[8] = 20
        y[9] = 21
        y[10] = 22
        y[11] = 23
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = 4,
                height = 2,
                y = ByteBuffer.wrap(y),
                yRowStride = yStride,
                u = bytes(0x80, 0x81),
                uRowStride = 4,
                uPixelStride = 1,
                v = bytes(0xC0, 0xC1),
                vRowStride = 4,
                vPixelStride = 1,
            )
        assertEquals(12, nv21.size)
        assertArrayEquals(byteArrayOf(10, 11, 12, 13, 20, 21, 22, 23), nv21.copyOf(8))
        assertEquals(0xC0.toByte(), nv21[8])
        assertEquals(0x80.toByte(), nv21[9])
        nv21.forEach { b -> assertTrue("padding leaked into NV21", b != 99.toByte()) }
    }

    @Test
    fun interleavedUvPixelStrideTwoMatchesMediaCodecNv21() {
        val vu = byteArrayOf(0xC0.toByte(), 0x80.toByte(), 0xC1.toByte(), 0x81.toByte())
        val v = ByteBuffer.wrap(vu)
        val u = ByteBuffer.wrap(vu).position(1).slice()
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = 4,
                height = 2,
                y = bytes(1, 2, 3, 4, 5, 6, 7, 8),
                yRowStride = 4,
                u = u,
                uRowStride = 4,
                uPixelStride = 2,
                v = v,
                vRowStride = 4,
                vPixelStride = 2,
            )
        assertArrayEquals(
            byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8,
                0xC0.toByte(), 0x80.toByte(), 0xC1.toByte(), 0x81.toByte(),
            ),
            nv21,
        )
    }

    @Test
    fun overstatedLimitDoesNotReadLastRowSentinel() {
        val sentinel = 0x7F.toByte()
        val vStride = 6
        val accessible = YuvJpeg.planeAccessibleBytes(vStride, 1, 2, 2)
        assertEquals(8, accessible)
        val overstated = ByteArray(16) { sentinel }
        overstated[0] = 0xC0.toByte()
        overstated[1] = 0xC1.toByte()
        overstated[6] = 0xC2.toByte()
        overstated[7] = 0xC3.toByte()
        val u = ByteArray(16) { sentinel }
        u[0] = 0x80.toByte()
        u[1] = 0x81.toByte()
        u[6] = 0x82.toByte()
        u[7] = 0x83.toByte()
        val yStride = 8
        val ySize = YuvJpeg.planeAccessibleBytes(yStride, 1, 4, 4)
        val y = ByteArray(ySize) { i -> (i + 1).toByte() }
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = 4,
                height = 4,
                y = ByteBuffer.wrap(y),
                yRowStride = yStride,
                u = ByteBuffer.wrap(u),
                uRowStride = vStride,
                uPixelStride = 1,
                v = ByteBuffer.wrap(overstated),
                vRowStride = vStride,
                vPixelStride = 1,
            )
        val chroma = nv21.copyOfRange(16, nv21.size)
        assertArrayEquals(
            byteArrayOf(
                0xC0.toByte(), 0x80.toByte(), 0xC1.toByte(), 0x81.toByte(),
                0xC2.toByte(), 0x82.toByte(), 0xC3.toByte(), 0x83.toByte(),
            ),
            chroma,
        )
        chroma.forEach { b -> assertTrue("overstated limit was read", b != sentinel) }
    }

    @Test
    fun cropUsesPlaneOffsetsAndEvenOutput() {
        val y = ByteArray(16)
        var i = 0
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                y[i++] = (row * 10 + col).toByte()
            }
        }
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = 2,
                height = 2,
                y = ByteBuffer.wrap(y),
                yRowStride = 4,
                u = bytes(0x80, 0x81, 0x82, 0x83),
                uRowStride = 2,
                uPixelStride = 1,
                v = bytes(0xC0, 0xC1, 0xC2, 0xC3),
                vRowStride = 2,
                vPixelStride = 1,
                cropLeft = 2,
                cropTop = 2,
            )
        assertArrayEquals(byteArrayOf(22, 23, 32, 33), nv21.copyOf(4))
        assertEquals(0xC3.toByte(), nv21[4])
        assertEquals(0x83.toByte(), nv21[5])
    }

    @Test
    fun oddDimensionsEvenAlignWithoutCrashing() {
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = 5,
                height = 3,
                y = bytes(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
                yRowStride = 5,
                u = bytes(0x80, 0x81, 0x82, 0x83),
                uRowStride = 2,
                uPixelStride = 1,
                v = bytes(0xC0, 0xC1, 0xC2, 0xC3),
                vRowStride = 2,
                vPixelStride = 1,
            )
        assertEquals(4 * 2 + 4 * 2 / 2, nv21.size)
        assertEquals(1.toByte(), nv21[0])
        assertEquals(2.toByte(), nv21[1])
        assertEquals(3.toByte(), nv21[2])
        assertEquals(4.toByte(), nv21[3])
    }

    @Test
    fun planeAccessibleBytesMatchesAndroidLastRowRule() {
        assertEquals(0, YuvJpeg.planeAccessibleBytes(8, 1, 0, 4))
        assertEquals(4, YuvJpeg.planeAccessibleBytes(8, 1, 4, 1))
        assertEquals(12, YuvJpeg.planeAccessibleBytes(8, 1, 4, 2))
        assertEquals(11, YuvJpeg.planeAccessibleBytes(8, 2, 2, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDirectByteBuffersSoImageReaderPlanesCannotSegv() {
        YuvJpeg.yuv420888ToNv21(
            width = 4,
            height = 2,
            y = ByteBuffer.allocateDirect(8),
            yRowStride = 4,
            u = bytes(1),
            uRowStride = 2,
            uPixelStride = 1,
            v = bytes(1),
            vRowStride = 2,
            vPixelStride = 1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroSize() {
        YuvJpeg.yuv420888ToNv21(
            width = 0,
            height = 4,
            y = bytes(1),
            yRowStride = 4,
            u = bytes(1),
            uRowStride = 2,
            uPixelStride = 1,
            v = bytes(1),
            vRowStride = 2,
            vPixelStride = 1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyYPlane() {
        YuvJpeg.yuv420888ToNv21(
            width = 4,
            height = 2,
            y = ByteBuffer.allocate(0),
            yRowStride = 4,
            u = bytes(1),
            uRowStride = 2,
            uPixelStride = 1,
            v = bytes(1),
            vRowStride = 2,
            vPixelStride = 1,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHugeStrideThatWouldWalkOffPlane() {
        YuvJpeg.yuv420888ToNv21(
            width = 4,
            height = 2,
            y = bytes(1, 2, 3, 4, 5, 6, 7, 8),
            yRowStride = 100_000,
            u = bytes(1),
            uRowStride = 2,
            uPixelStride = 1,
            v = bytes(1),
            vRowStride = 2,
            vPixelStride = 1,
        )
    }

    @Test
    fun shortChromaBuffersLeaveZerosInsteadOfThrowing() {
        val nv21 =
            YuvJpeg.yuv420888ToNv21(
                width = 4,
                height = 2,
                y = bytes(1, 2, 3, 4, 5, 6, 7, 8),
                yRowStride = 4,
                u = ByteBuffer.allocate(0),
                uRowStride = 2,
                uPixelStride = 1,
                v = ByteBuffer.allocate(0),
                vRowStride = 2,
                vPixelStride = 1,
            )
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), nv21)
    }

    private fun bytes(vararg values: Int): ByteBuffer =
        ByteBuffer.wrap(ByteArray(values.size) { values[it].toByte() })
}
