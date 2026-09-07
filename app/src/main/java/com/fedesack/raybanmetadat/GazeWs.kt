package com.fedesack.raybanmetadat

import java.net.Inet4Address
import java.net.NetworkInterface

object GazeWs {
    const val BIND_HOST = "0.0.0.0"
    const val PORT = 8765
    const val PATH = "/frames"
    const val JPEG_QUALITY = 70
    const val TARGET_FPS = 12
    const val MAX_FPS = 15
    const val TARGET_INTERVAL_MS = 1000L / TARGET_FPS
    const val MIN_INTERVAL_MS = 1000L / MAX_FPS
    const val PUMP_SLEEP_MS = 10L
    const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    fun endpoint(ip: String?): String = "ws://${ip ?: "<wifi-ip>"}:$PORT$PATH"
}

data class GazeFrameMeta(
    val tsMs: Long,
    val w: Int,
    val h: Int,
) {
    fun json(): String = """{"ts_ms":$tsMs,"w":$w,"h":$h}"""
}

data class EncodedGaze(
    val jpeg: ByteArray,
    val meta: GazeFrameMeta,
)

data class GazeYuv(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val nv21: Boolean = false,
)

data class GazeRawFrame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val compressed: Boolean,
    val codecConfig: Boolean,
    val presentationTimeUs: Long,
)

data class LanInterface(
    val name: String,
    val up: Boolean,
    val loopback: Boolean,
    val ipv4: String?,
)

object GazePace {
    fun due(
        nowMs: Long,
        lastMs: Long,
        intervalMs: Long = GazeWs.TARGET_INTERVAL_MS,
    ): Boolean = lastMs < 0L || nowMs - lastMs >= intervalMs
}

object GazeLan {
    fun endpoint(ip: String?): String = GazeWs.endpoint(ip)

    fun wifiIpv4(interfaces: List<LanInterface> = discover()): String? {
        val up =
            interfaces.filter { it.up && !it.loopback && !it.ipv4.isNullOrBlank() }
        if (up.isEmpty()) return null
        val preferred =
            up.filter { nif ->
                val name = nif.name.lowercase()
                name.startsWith("wlan") || name.startsWith("ap") || name.startsWith("swlan")
            }
        val pool = if (preferred.isNotEmpty()) preferred else up
        return pool.mapNotNull { it.ipv4 }.firstOrNull { isPrivateLan(it) }
            ?: pool.mapNotNull { it.ipv4 }.firstOrNull()
    }

    fun isPrivateLan(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size != 4) return false
        val a = parts[0].toIntOrNull() ?: return false
        val b = parts[1].toIntOrNull() ?: return false
        return a == 10 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31)
    }

    fun discover(): List<LanInterface> =
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().map { nif ->
                val ipv4 =
                    nif.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .firstOrNull { !it.isLoopbackAddress }
                        ?.hostAddress
                LanInterface(
                    name = nif.name,
                    up = nif.isUp,
                    loopback = nif.isLoopback,
                    ipv4 = ipv4,
                )
            }
        }.getOrDefault(emptyList())
}
