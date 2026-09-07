package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BoardCaptureTest {
    @Test
    fun fileNameUsesEpochAndExtension() {
        assertEquals("murdoku_1710000000000.jpg", BoardCaptureMath.fileName(1_710_000_000_000L))
        assertEquals("murdoku_9.heic", BoardCaptureMath.fileName(9L, "heic"))
    }

    @Test
    fun prependKeepsNewestFirstAndCapsGallery() {
        val first = capture("a", 1)
        val second = capture("b", 2)
        val third = capture("c", 3)
        val ring = BoardCaptureMath.prepend(listOf(first, second), third, limit = 2)
        assertEquals(listOf("c", "a"), ring.map { it.id })
        val replace = BoardCaptureMath.prepend(ring, third.copy(fileName = "again.jpg"), limit = 2)
        assertEquals(listOf("c", "a"), replace.map { it.id })
        assertEquals("again.jpg", replace.first().fileName)
    }

    @Test
    fun serializeRoundTripKeepsFields() {
        val item =
            BoardCapture(
                id = "id-1",
                uri = "content://media/external/images/1",
                fileName = "murdoku_10.jpg",
                takenAtMs = 10L,
                width = 720,
                height = 1280,
                source = CaptureSource.PHOTO,
                mime = BoardCapture.MIME_JPEG,
            )
        val parsed = BoardCaptureMath.parse(BoardCaptureMath.serialize(item))
        assertEquals(item, parsed)
        val batch = BoardCaptureMath.parseAll(BoardCaptureMath.serializeAll(listOf(item)))
        assertEquals(listOf(item), batch)
    }

    @Test
    fun parseRejectsGarbageAndSkipsBlankLines() {
        assertNull(BoardCaptureMath.parse(""))
        assertNull(BoardCaptureMath.parse("only\tthree\tparts"))
        assertNull(BoardCaptureMath.parse("id\turi\tname\tbad\t720\t1280\tPHOTO"))
        assertEquals(
            emptyList<BoardCapture>(),
            BoardCaptureMath.parseAll("\n\n"),
        )
    }

    private fun capture(
        id: String,
        takenAtMs: Long,
    ): BoardCapture =
        BoardCapture(
            id = id,
            uri = "content://$id",
            fileName = "$id.jpg",
            takenAtMs = takenAtMs,
            width = 720,
            height = 1280,
            source = CaptureSource.FRAME,
        )
}
