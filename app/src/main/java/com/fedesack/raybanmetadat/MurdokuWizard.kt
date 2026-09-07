package com.fedesack.raybanmetadat

import java.util.UUID

enum class MurdokuWizardStep {
    INSTRUCTIONS,
    PUZZLE,
    GUIDE,
}

enum class MurdokuAssetKind(
    val json: String,
) {
    INSTRUCTIONS("instructions"),
    PUZZLE("puzzle"),
    ;

    companion object {
        fun parse(raw: String?): MurdokuAssetKind? =
            entries.firstOrNull { it.json.equals(raw?.trim(), ignoreCase = true) }
    }
}

object MurdokuWizardCopy {
    const val MODE = "Murdoku"
    const val STEP1_TITLE = "Instrucciones"
    const val STEP1_PROMPT = "Andá a la página de instrucciones del libro naranja y mirala con los lentes."
    const val STEP1_CTA = "Listo, capturar"
    const val STEP1_OK = "Instrucciones guardadas."
    const val STEP2_TITLE = "El Murdoku"
    const val STEP2_PROMPT = "Ahora andá al Murdoku que querés resolver y encuadrá bien el grid."
    const val STEP2_CTA = "Capturar puzzle"
    const val STEP2_OK = "Puzzle guardado. Analizando…"
    const val STEP3_TITLE = "Próxima jugada"
    const val STEP3_HEADING = STEP3_TITLE
    const val VOICE_STUB = "Voz (stub)"
    const val GALLERY_PATH = "Pictures/RaybanDat"
    const val ENQUEUE_CTA = "Encolar análisis"
}

data class MurdokuHandoffMeta(
    val timestampMs: Long,
    val device: String = DEVICE,
    val quality: String = QUALITY,
) {
    companion object {
        const val DEVICE = "rayban-meta"
        const val QUALITY = "HIGH"
    }
}

data class MurdokuHandoffAsset(
    val kind: MurdokuAssetKind,
    val capture: BoardCapture,
    val meta: MurdokuHandoffMeta,
)

data class MurdokuMove(
    val row: Int,
    val col: Int,
    val value: Int,
    val reason: String,
)

data class MurdokuAnalysis(
    val sessionId: String,
    val moves: List<MurdokuMove> = emptyList(),
)

data class MurdokuWizardState(
    val sessionId: String,
    val step: MurdokuWizardStep,
    val instructions: BoardCapture? = null,
    val puzzle: BoardCapture? = null,
    val analysis: MurdokuAnalysis? = null,
    val status: String? = null,
    val enqueueStatus: String? = null,
    val analyzing: Boolean = false,
) {
    val readyToHandoff: Boolean
        get() = instructions != null && puzzle != null

    val captureKind: MurdokuAssetKind?
        get() =
            when (step) {
                MurdokuWizardStep.INSTRUCTIONS -> MurdokuAssetKind.INSTRUCTIONS
                MurdokuWizardStep.PUZZLE -> MurdokuAssetKind.PUZZLE
                MurdokuWizardStep.GUIDE -> null
            }

    val stepTitle: String
        get() =
            when (step) {
                MurdokuWizardStep.INSTRUCTIONS -> MurdokuWizardCopy.STEP1_TITLE
                MurdokuWizardStep.PUZZLE -> MurdokuWizardCopy.STEP2_TITLE
                MurdokuWizardStep.GUIDE -> MurdokuWizardCopy.STEP3_TITLE
            }

    val prompt: String
        get() =
            when (step) {
                MurdokuWizardStep.INSTRUCTIONS -> MurdokuWizardCopy.STEP1_PROMPT
                MurdokuWizardStep.PUZZLE -> MurdokuWizardCopy.STEP2_PROMPT
                MurdokuWizardStep.GUIDE -> MurdokuWizardCopy.STEP3_HEADING
            }

    val cta: String?
        get() =
            when (step) {
                MurdokuWizardStep.INSTRUCTIONS -> MurdokuWizardCopy.STEP1_CTA
                MurdokuWizardStep.PUZZLE -> MurdokuWizardCopy.STEP2_CTA
                MurdokuWizardStep.GUIDE -> null
            }

    val sessionCaptures: List<BoardCapture>
        get() = listOfNotNull(instructions, puzzle)
}

object MurdokuWizardMath {
    fun newSession(sessionId: String = UUID.randomUUID().toString()): MurdokuWizardState =
        MurdokuWizardState(
            sessionId = sessionId,
            step = MurdokuWizardStep.INSTRUCTIONS,
        )

    fun acceptCapture(
        state: MurdokuWizardState,
        capture: BoardCapture,
        timestampMs: Long = capture.takenAtMs,
    ): MurdokuWizardState {
        val tagged = capture.copy(kind = state.captureKind ?: capture.kind, takenAtMs = timestampMs)
        return when (state.step) {
            MurdokuWizardStep.INSTRUCTIONS ->
                state.copy(
                    instructions = tagged.copy(kind = MurdokuAssetKind.INSTRUCTIONS),
                    step = MurdokuWizardStep.PUZZLE,
                    status = MurdokuWizardCopy.STEP1_OK,
                    analyzing = false,
                )
            MurdokuWizardStep.PUZZLE -> {
                if (state.instructions == null) return state
                state.copy(
                    puzzle = tagged.copy(kind = MurdokuAssetKind.PUZZLE),
                    step = MurdokuWizardStep.GUIDE,
                    status = MurdokuWizardCopy.STEP2_OK,
                    analyzing = true,
                    analysis = stubAnalysis(state.sessionId),
                )
            }
            MurdokuWizardStep.GUIDE -> state
        }
    }

    fun applyAnalysis(
        state: MurdokuWizardState,
        analysis: MurdokuAnalysis,
    ): MurdokuWizardState {
        if (analysis.sessionId != state.sessionId) return state
        return state.copy(
            analysis = analysis,
            analyzing = false,
            status = if (analysis.moves.isEmpty()) state.status else MurdokuWizardCopy.STEP3_HEADING,
        )
    }

    fun handoff(state: MurdokuWizardState): List<MurdokuHandoffAsset> {
        val instructions = state.instructions ?: return emptyList()
        val puzzle = state.puzzle ?: return emptyList()
        return listOf(
            asset(MurdokuAssetKind.INSTRUCTIONS, instructions),
            asset(MurdokuAssetKind.PUZZLE, puzzle),
        )
    }

    fun isCompleteHandoff(assets: List<MurdokuHandoffAsset>): Boolean =
        assets.size == 2 &&
            assets[0].kind == MurdokuAssetKind.INSTRUCTIONS &&
            assets[1].kind == MurdokuAssetKind.PUZZLE &&
            assets.all { it.meta.device == MurdokuHandoffMeta.DEVICE } &&
            assets.all { it.meta.quality == MurdokuHandoffMeta.QUALITY }

    fun stubAnalysis(sessionId: String): MurdokuAnalysis = MurdokuAnalysis(sessionId = sessionId)

    fun metaFor(capture: BoardCapture): MurdokuHandoffMeta =
        MurdokuHandoffMeta(timestampMs = capture.takenAtMs)

    private fun asset(
        kind: MurdokuAssetKind,
        capture: BoardCapture,
    ): MurdokuHandoffAsset =
        MurdokuHandoffAsset(
            kind = kind,
            capture = capture.copy(kind = kind),
            meta = metaFor(capture),
        )
}

object MurdokuHandoffJson {
    fun analysis(analysis: MurdokuAnalysis): String {
        val moves =
            analysis.moves.joinToString(",") { move ->
                "{" +
                    "\"row\":${move.row}," +
                    "\"col\":${move.col}," +
                    "\"value\":${move.value}," +
                    "\"reason\":\"${IntentJson.escape(move.reason)}\"" +
                    "}"
            }
        return "{" +
            "\"sessionId\":\"${IntentJson.escape(analysis.sessionId)}\"," +
            "\"moves\":[$moves]" +
            "}"
    }

    fun payload(state: MurdokuWizardState): String? {
        val items = MurdokuWizardMath.handoff(state)
        if (!MurdokuWizardMath.isCompleteHandoff(items)) return null
        val assets =
            items.joinToString(",") { item ->
                "{" +
                    "\"kind\":\"${item.kind.json}\"," +
                    "\"uri\":\"${IntentJson.escape(item.capture.uri)}\"," +
                    "\"fileName\":\"${IntentJson.escape(item.capture.fileName)}\"," +
                    "\"timestamp\":${item.meta.timestampMs}," +
                    "\"device\":\"${IntentJson.escape(item.meta.device)}\"," +
                    "\"quality\":\"${IntentJson.escape(item.meta.quality)}\"" +
                    "}"
            }
        return "{" +
            "\"sessionId\":\"${IntentJson.escape(state.sessionId)}\"," +
            "\"device\":\"${MurdokuHandoffMeta.DEVICE}\"," +
            "\"quality\":\"${MurdokuHandoffMeta.QUALITY}\"," +
            "\"assets\":[$assets]" +
            "}"
    }

    fun parseAnalysis(json: String): MurdokuAnalysis? {
        val sessionId = IntentJson.extractString(json, "sessionId") ?: return null
        return MurdokuAnalysis(sessionId = sessionId, moves = parseMoves(json))
    }

    internal fun parseMoves(json: String): List<MurdokuMove> {
        val block =
            Regex("\"moves\"\\s*:\\s*\\[(.*?)]", setOf(RegexOption.DOT_MATCHES_ALL))
                .find(json)
                ?.groupValues
                ?.get(1)
                ?: return emptyList()
        if (block.isBlank()) return emptyList()
        return Regex("\\{([^}]+)\\}")
            .findAll(block)
            .mapNotNull { match ->
                val obj = match.value
                val row = extractInt(obj, "row") ?: return@mapNotNull null
                val col = extractInt(obj, "col") ?: return@mapNotNull null
                val value = extractInt(obj, "value") ?: return@mapNotNull null
                MurdokuMove(
                    row = row,
                    col = col,
                    value = value,
                    reason = IntentJson.extractString(obj, "reason").orEmpty(),
                )
            }.toList()
    }

    private fun extractInt(
        json: String,
        key: String,
    ): Int? = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()
}
