package com.fedesack.raybanmetadat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MurdokuWizardTest {
    @Test
    fun copyIsTheLockedSpanishProductBrief() {
        assertEquals("Murdoku", MurdokuWizardCopy.MODE)
        assertEquals("Instrucciones", MurdokuWizardCopy.STEP1_TITLE)
        assertEquals(
            "Andá a la página de instrucciones del libro naranja y mirala con los lentes.",
            MurdokuWizardCopy.STEP1_PROMPT,
        )
        assertEquals("Listo, capturar", MurdokuWizardCopy.STEP1_CTA)
        assertEquals("Instrucciones guardadas.", MurdokuWizardCopy.STEP1_OK)
        assertEquals("El Murdoku", MurdokuWizardCopy.STEP2_TITLE)
        assertEquals(
            "Ahora andá al Murdoku que querés resolver y encuadrá bien el grid.",
            MurdokuWizardCopy.STEP2_PROMPT,
        )
        assertEquals("Capturar puzzle", MurdokuWizardCopy.STEP2_CTA)
        assertEquals("Puzzle guardado. Analizando…", MurdokuWizardCopy.STEP2_OK)
        assertEquals("Próxima jugada", MurdokuWizardCopy.STEP3_TITLE)
        assertEquals("Próxima jugada", MurdokuWizardCopy.STEP3_HEADING)
        assertEquals("Voz (stub)", MurdokuWizardCopy.VOICE_STUB)
    }

    @Test
    fun newSessionStartsOnInstructionsWithStableId() {
        val session = MurdokuWizardMath.newSession("session-1")
        assertEquals("session-1", session.sessionId)
        assertEquals(MurdokuWizardStep.INSTRUCTIONS, session.step)
        assertEquals(MurdokuAssetKind.INSTRUCTIONS, session.captureKind)
        assertEquals(MurdokuWizardCopy.STEP1_CTA, session.cta)
        assertEquals("Instrucciones", session.stepTitle)
        assertEquals(MurdokuWizardCopy.STEP1_PROMPT, session.prompt)
        assertFalse(session.readyToHandoff)
        assertTrue(MurdokuWizardMath.handoff(session).isEmpty())
    }

    @Test
    fun capturesAdvanceInFixedOrderAndKeepTheSameSessionId() {
        val start = MurdokuWizardMath.newSession("session-9")
        val afterInstructions =
            MurdokuWizardMath.acceptCapture(start, capture("instr", 11L), timestampMs = 11L)
        assertEquals("session-9", afterInstructions.sessionId)
        assertEquals(MurdokuWizardStep.PUZZLE, afterInstructions.step)
        assertEquals(MurdokuWizardCopy.STEP1_OK, afterInstructions.status)
        assertEquals(MurdokuAssetKind.INSTRUCTIONS, afterInstructions.instructions?.kind)
        assertEquals(MurdokuWizardCopy.STEP2_CTA, afterInstructions.cta)
        assertEquals(MurdokuWizardCopy.STEP2_PROMPT, afterInstructions.prompt)
        assertFalse(afterInstructions.readyToHandoff)

        val afterPuzzle =
            MurdokuWizardMath.acceptCapture(afterInstructions, capture("puzzle", 22L), timestampMs = 22L)
        assertEquals("session-9", afterPuzzle.sessionId)
        assertEquals(MurdokuWizardStep.GUIDE, afterPuzzle.step)
        assertEquals(MurdokuWizardCopy.STEP2_OK, afterPuzzle.status)
        assertTrue(afterPuzzle.analyzing)
        assertTrue(afterPuzzle.readyToHandoff)
        assertNull(afterPuzzle.cta)
        assertEquals(MurdokuWizardCopy.STEP3_HEADING, afterPuzzle.prompt)
        assertEquals("session-9", afterPuzzle.analysis?.sessionId)
        assertEquals(emptyList<MurdokuMove>(), afterPuzzle.analysis?.moves)

        val ignored = MurdokuWizardMath.acceptCapture(afterPuzzle, capture("extra", 33L))
        assertEquals(afterPuzzle, ignored)

        val assets = MurdokuWizardMath.handoff(afterPuzzle)
        assertEquals(
            listOf(MurdokuAssetKind.INSTRUCTIONS, MurdokuAssetKind.PUZZLE),
            assets.map { it.kind },
        )
        assertEquals(listOf(11L, 22L), assets.map { it.meta.timestampMs })
        assertTrue(assets.all { it.meta.device == MurdokuHandoffMeta.DEVICE })
        assertTrue(assets.all { it.meta.quality == MurdokuHandoffMeta.QUALITY })
        assertEquals("rayban-meta", MurdokuHandoffMeta.DEVICE)
        assertEquals("HIGH", MurdokuHandoffMeta.QUALITY)
        assertTrue(MurdokuWizardMath.isCompleteHandoff(assets))
        assertEquals("Instrucciones", start.stepTitle)
        assertEquals("El Murdoku", afterInstructions.stepTitle)
        assertEquals("Próxima jugada", afterPuzzle.stepTitle)
    }

    @Test
    fun neverSendsPuzzleWithoutInstructionsFirst() {
        val onlyPuzzle =
            MurdokuWizardState(
                sessionId = "orphan",
                step = MurdokuWizardStep.GUIDE,
                puzzle = capture("p", 9L),
            )
        assertTrue(MurdokuWizardMath.handoff(onlyPuzzle).isEmpty())
        assertFalse(onlyPuzzle.readyToHandoff)
        assertNull(MurdokuHandoffJson.payload(onlyPuzzle))

        val onlyInstructions =
            MurdokuWizardMath.acceptCapture(
                MurdokuWizardMath.newSession("sid-2"),
                capture("i", 1L),
            )
        assertTrue(MurdokuWizardMath.handoff(onlyInstructions).isEmpty())
        assertNull(MurdokuHandoffJson.payload(onlyInstructions))

        val skippedPuzzle =
            MurdokuWizardMath.acceptCapture(
                MurdokuWizardState(sessionId = "sid-3", step = MurdokuWizardStep.PUZZLE),
                capture("p", 2L),
            )
        assertNull(skippedPuzzle.puzzle)
        assertEquals(MurdokuWizardStep.PUZZLE, skippedPuzzle.step)
        assertNull(MurdokuHandoffJson.payload(skippedPuzzle))
    }

    @Test
    fun applyAnalysisRejectsOtherSessionsAndFillsMoves() {
        val state =
            MurdokuWizardMath.acceptCapture(
                MurdokuWizardMath.acceptCapture(
                    MurdokuWizardMath.newSession("sid"),
                    capture("a", 1L),
                ),
                capture("b", 2L),
            )
        val ignored =
            MurdokuWizardMath.applyAnalysis(
                state,
                MurdokuAnalysis("other", listOf(MurdokuMove(0, 1, 7, "no"))),
            )
        assertEquals(state, ignored)
        val applied =
            MurdokuWizardMath.applyAnalysis(
                state,
                MurdokuAnalysis("sid", listOf(MurdokuMove(0, 2, 5, "único"))),
            )
        assertFalse(applied.analyzing)
        assertEquals(1, applied.analysis?.moves?.size)
        assertEquals(0, applied.analysis?.moves?.first()?.row)
        assertEquals(2, applied.analysis?.moves?.first()?.col)
        assertEquals(5, applied.analysis?.moves?.first()?.value)
        assertEquals(MurdokuWizardCopy.STEP3_HEADING, applied.status)
    }

    @Test
    fun handoffJsonKeepsOrderAndAnalysisShapeIsZeroIndexed() {
        val state =
            MurdokuWizardMath.acceptCapture(
                MurdokuWizardMath.acceptCapture(
                    MurdokuWizardMath.newSession("abc"),
                    capture("i", 100L).copy(uri = "content://i", fileName = "murdoku_100.jpg"),
                ),
                capture("p", 200L).copy(uri = "content://p", fileName = "murdoku_200.jpg"),
            )
        assertEquals(
            "{" +
                "\"sessionId\":\"abc\"," +
                "\"device\":\"rayban-meta\"," +
                "\"quality\":\"HIGH\"," +
                "\"assets\":[" +
                "{\"kind\":\"instructions\",\"uri\":\"content://i\",\"fileName\":\"murdoku_100.jpg\"," +
                "\"timestamp\":100,\"device\":\"rayban-meta\",\"quality\":\"HIGH\"}," +
                "{\"kind\":\"puzzle\",\"uri\":\"content://p\",\"fileName\":\"murdoku_200.jpg\"," +
                "\"timestamp\":200,\"device\":\"rayban-meta\",\"quality\":\"HIGH\"}" +
                "]" +
                "}",
            MurdokuHandoffJson.payload(state),
        )
        val analysis =
            MurdokuAnalysis(
                sessionId = "abc",
                moves = listOf(MurdokuMove(0, 1, 9, "fila 0")),
            )
        val json = MurdokuHandoffJson.analysis(analysis)
        assertEquals(
            """{"sessionId":"abc","moves":[{"row":0,"col":1,"value":9,"reason":"fila 0"}]}""",
            json,
        )
        assertEquals(analysis, MurdokuHandoffJson.parseAnalysis(json))
        assertEquals(
            MurdokuAnalysis("abc"),
            MurdokuHandoffJson.parseAnalysis("""{"sessionId":"abc","moves":[]}"""),
        )
        assertNull(MurdokuHandoffJson.parseAnalysis("""{"moves":[]}"""))
    }

    @Test
    fun assetKindParsesKnownValues() {
        assertEquals(MurdokuAssetKind.INSTRUCTIONS, MurdokuAssetKind.parse("instructions"))
        assertEquals(MurdokuAssetKind.PUZZLE, MurdokuAssetKind.parse("PUZZLE"))
        assertNull(MurdokuAssetKind.parse(""))
        assertNull(MurdokuAssetKind.parse("board"))
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
            source = CaptureSource.PHOTO,
        )
}
