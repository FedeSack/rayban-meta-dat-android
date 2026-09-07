package com.fedesack.raybanmetadat

object IntentJson {
    fun encode(intent: VoiceIntent): String {
        val flags = """{"voiceDevMode":${intent.voiceDevMode}}"""
        return buildString {
            append('{')
            appendField("id", intent.id)
            append(',')
            appendField("utterance", intent.utterance)
            append(',')
            appendField("source", intent.source.json)
            append(',')
            appendField("ts", intent.ts)
            append(',')
            appendField("deviceId", intent.deviceId)
            append(',')
            appendField("appVersion", intent.appVersion)
            append(',')
            append("\"flags\":")
            append(flags)
            append(',')
            appendField("prefer", intent.prefer.json)
            append('}')
        }
    }

    fun decodeAck(json: String): DevIntentAck? {
        val intentId = extractString(json, "intentId") ?: return null
        val status = DevIntentStatus.parse(extractString(json, "status")) ?: return null
        return DevIntentAck(
            intentId = intentId,
            status = status,
            kind = DevIntentKind.parse(extractString(json, "kind")),
            prUrl = extractString(json, "prUrl"),
            commit = extractString(json, "commit"),
            skillId = extractString(json, "skillId"),
            message = extractString(json, "message"),
        )
    }

    private fun StringBuilder.appendField(
        key: String,
        value: String,
    ) {
        append('"')
        append(key)
        append('"')
        append(':')
        append('"')
        append(escape(value))
        append('"')
    }

    internal fun escape(value: String): String {
        val out = StringBuilder(value.length + 8)
        value.forEach { ch ->
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    internal fun extractString(
        json: String,
        key: String,
    ): String? {
        val pattern = "\"${Regex.escape(key)}\"\\s*:\\s*\""
        val match = Regex(pattern).find(json) ?: return null
        val start = match.range.last + 1
        val out = StringBuilder()
        var i = start
        while (i < json.length) {
            val ch = json[i]
            when {
                ch == '\\' && i + 1 < json.length -> {
                    when (val next = json[i + 1]) {
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        else -> out.append(next)
                    }
                    i += 2
                }
                ch == '"' -> return out.toString()
                else -> {
                    out.append(ch)
                    i += 1
                }
            }
        }
        return null
    }
}
