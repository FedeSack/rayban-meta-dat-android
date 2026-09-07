package com.fedesack.raybanmetadat

enum class CaptureSource {
    PHOTO,
    FRAME,
}

data class BoardCapture(
    val id: String,
    val uri: String,
    val fileName: String,
    val takenAtMs: Long,
    val width: Int?,
    val height: Int?,
    val source: CaptureSource,
    val mime: String = MIME_JPEG,
    val kind: MurdokuAssetKind? = null,
) {
    companion object {
        const val MIME_JPEG = "image/jpeg"
        const val MIME_HEIC = "image/heic"
    }
}

object BoardCaptureMath {
    const val GALLERY_LIMIT = 8
    const val JPEG_QUALITY = 95

    fun fileName(
        takenAtMs: Long,
        extension: String = "jpg",
    ): String = "murdoku_${takenAtMs}.$extension"

    fun prepend(
        existing: List<BoardCapture>,
        item: BoardCapture,
        limit: Int = GALLERY_LIMIT,
    ): List<BoardCapture> = (listOf(item) + existing.filter { it.id != item.id }).take(limit)

    fun serialize(item: BoardCapture): String =
        listOf(
            item.id,
            item.uri,
            item.fileName,
            item.takenAtMs.toString(),
            item.width?.toString().orEmpty(),
            item.height?.toString().orEmpty(),
            item.source.name,
            item.mime,
            item.kind?.json.orEmpty(),
        ).joinToString("\t")

    fun parse(line: String): BoardCapture? {
        val parts = line.split('\t')
        if (parts.size < 7) return null
        val takenAtMs = parts[3].toLongOrNull() ?: return null
        val source = runCatching { CaptureSource.valueOf(parts[6]) }.getOrNull() ?: return null
        return BoardCapture(
            id = parts[0],
            uri = parts[1],
            fileName = parts[2],
            takenAtMs = takenAtMs,
            width = parts.getOrNull(4)?.toIntOrNull(),
            height = parts.getOrNull(5)?.toIntOrNull(),
            source = source,
            mime = parts.getOrNull(7)?.ifBlank { null } ?: BoardCapture.MIME_JPEG,
            kind = MurdokuAssetKind.parse(parts.getOrNull(8)),
        )
    }

    fun serializeAll(items: List<BoardCapture>): String = items.joinToString("\n") { serialize(it) }

    fun parseAll(raw: String): List<BoardCapture> =
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { parse(it) }
            .toList()
}
