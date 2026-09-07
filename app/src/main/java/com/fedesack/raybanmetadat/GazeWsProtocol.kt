package com.fedesack.raybanmetadat

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Base64

data class GazeWsRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
) {
    val key: String? get() = headers["sec-websocket-key"]

    val upgrade: Boolean
        get() =
            method.equals("GET", ignoreCase = true) &&
                headers["upgrade"]?.contains("websocket", ignoreCase = true) == true &&
                !key.isNullOrBlank()
}

object GazeWsHandshake {
    fun parse(raw: String): GazeWsRequest? {
        val lines = raw.split("\r\n")
        if (lines.isEmpty()) return null
        val parts = lines[0].trim().split(' ')
        if (parts.size < 2) return null
        val headers = linkedMapOf<String, String>()
        for (line in lines.drop(1)) {
            if (line.isEmpty()) break
            val idx = line.indexOf(':')
            if (idx <= 0) continue
            headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return GazeWsRequest(
            method = parts[0],
            path = parts[1].substringBefore('?'),
            headers = headers,
        )
    }

    fun isFramesPath(path: String): Boolean = path == GazeWs.PATH

    fun acceptKey(key: String): String {
        val digest =
            MessageDigest.getInstance("SHA-1")
                .digest((key + GazeWs.GUID).toByteArray(Charsets.US_ASCII))
        return Base64.getEncoder().encodeToString(digest)
    }

    fun switchingProtocols(key: String): String =
        "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${acceptKey(key)}\r\n" +
            "\r\n"

    fun notFound(): String =
        "HTTP/1.1 404 Not Found\r\n" +
            "Connection: close\r\n" +
            "Content-Length: 0\r\n" +
            "\r\n"

    fun badRequest(): String =
        "HTTP/1.1 400 Bad Request\r\n" +
            "Connection: close\r\n" +
            "Content-Length: 0\r\n" +
            "\r\n"

    fun readHead(
        input: InputStream,
        maxBytes: Int = 8192,
    ): String? {
        val buf = ByteArrayOutputStream()
        var match = 0
        val end = byteArrayOf(13, 10, 13, 10)
        while (buf.size() < maxBytes) {
            val next = input.read()
            if (next < 0) return null
            buf.write(next)
            if (next == end[match].toInt()) {
                match += 1
                if (match == 4) return buf.toString(Charsets.US_ASCII)
            } else {
                match = if (next == end[0].toInt()) 1 else 0
            }
        }
        return null
    }
}

object GazeWsFrames {
    const val OP_TEXT = 0x1
    const val OP_BINARY = 0x2
    const val OP_CLOSE = 0x8
    const val OP_PING = 0x9
    const val OP_PONG = 0xA

    fun encode(
        opcode: Int,
        payload: ByteArray,
    ): ByteArray {
        val len = payload.size
        val header =
            when {
                len < 126 -> 2
                len <= 0xFFFF -> 4
                else -> 10
            }
        val out = ByteArray(header + len)
        out[0] = (0x80 or (opcode and 0x0F)).toByte()
        when {
            len < 126 -> {
                out[1] = len.toByte()
            }
            len <= 0xFFFF -> {
                out[1] = 126.toByte()
                out[2] = (len shr 8).toByte()
                out[3] = (len and 0xFF).toByte()
            }
            else -> {
                out[1] = 127.toByte()
                var cursor = 2
                for (shift in 56 downTo 0 step 8) {
                    out[cursor] = ((len.toLong() shr shift) and 0xFF).toByte()
                    cursor += 1
                }
            }
        }
        System.arraycopy(payload, 0, out, header, len)
        return out
    }

    fun encodeText(text: String): ByteArray = encode(OP_TEXT, text.toByteArray(Charsets.UTF_8))

    fun encodeBinary(bytes: ByteArray): ByteArray = encode(OP_BINARY, bytes)

    fun read(input: InputStream, maxPayload: Int = 65_536): Incoming? {
        val b0 = input.read()
        if (b0 < 0) return null
        val b1 = input.read()
        if (b1 < 0) return null
        val opcode = b0 and 0x0F
        val masked = b1 and 0x80 != 0
        var len = (b1 and 0x7F).toLong()
        when (len) {
            126L -> {
                val extra = readExact(input, 2) ?: return null
                len = ((extra[0].toInt() and 0xFF) shl 8 or (extra[1].toInt() and 0xFF)).toLong()
            }
            127L -> {
                val extra = readExact(input, 8) ?: return null
                len = 0L
                extra.forEach { byte ->
                    len = (len shl 8) or (byte.toInt() and 0xFF).toLong()
                }
            }
        }
        if (len < 0L || len > maxPayload) return null
        val mask = if (masked) readExact(input, 4) ?: return null else ByteArray(0)
        val payload = readExact(input, len.toInt()) ?: return null
        if (masked) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
        }
        return Incoming(opcode = opcode, payload = payload)
    }

    private fun readExact(
        input: InputStream,
        count: Int,
    ): ByteArray? {
        val out = ByteArray(count)
        var filled = 0
        while (filled < count) {
            val n = input.read(out, filled, count - filled)
            if (n < 0) return null
            filled += n
        }
        return out
    }

    data class Incoming(
        val opcode: Int,
        val payload: ByteArray,
    )
}
