package com.fedesack.raybanmetadat

import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface GazeSocketHub {
    val listening: Boolean
    val clientCount: Int
    val localPort: Int

    fun start(): Boolean

    fun stop()

    fun broadcastText(text: String)

    fun broadcastBinary(bytes: ByteArray)
}

class GazeWsServer(
    private val host: String = GazeWs.BIND_HOST,
    private val port: Int = GazeWs.PORT,
) : GazeSocketHub {
    private val running = AtomicBoolean(false)
    private val clients = CopyOnWriteArraySet<Client>()
    private val boundPort = AtomicInteger(port)
    @Volatile override var listening: Boolean = false
        private set
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null

    override val clientCount: Int get() = clients.size
    override val localPort: Int get() = boundPort.get()

    override fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return listening
        return try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(host, port), 8)
            boundPort.set(socket.localPort)
            server = socket
            listening = true
            acceptThread =
                Thread({ acceptLoop(socket) }, "gaze-ws").apply {
                    isDaemon = true
                    start()
                }
            true
        } catch (_: Exception) {
            running.set(false)
            listening = false
            false
        }
    }

    override fun stop() {
        running.set(false)
        listening = false
        runCatching { server?.close() }
        server = null
        clients.toList().forEach { it.close() }
        clients.clear()
        acceptThread?.join(500)
        acceptThread = null
    }

    override fun broadcastText(text: String) {
        broadcast(GazeWsFrames.encodeText(text))
    }

    override fun broadcastBinary(bytes: ByteArray) {
        broadcast(GazeWsFrames.encodeBinary(bytes))
    }

    private fun broadcast(frame: ByteArray) {
        clients.forEach { client ->
            if (!client.send(frame)) {
                client.close()
                clients.remove(client)
            }
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running.get()) {
            val inbound =
                try {
                    socket.accept()
                } catch (_: Exception) {
                    if (running.get()) continue else break
                }
            Thread({ handle(inbound) }, "gaze-ws-client").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            val head = GazeWsHandshake.readHead(input)
            val request = head?.let { GazeWsHandshake.parse(it) }
            if (request == null || !request.upgrade) {
                output.write(GazeWsHandshake.badRequest().toByteArray(Charsets.US_ASCII))
                output.flush()
                socket.close()
                return
            }
            if (!GazeWsHandshake.isFramesPath(request.path)) {
                output.write(GazeWsHandshake.notFound().toByteArray(Charsets.US_ASCII))
                output.flush()
                socket.close()
                return
            }
            val key = request.key ?: run {
                output.write(GazeWsHandshake.badRequest().toByteArray(Charsets.US_ASCII))
                output.flush()
                socket.close()
                return
            }
            output.write(GazeWsHandshake.switchingProtocols(key).toByteArray(Charsets.US_ASCII))
            output.flush()
            val client = Client(socket, output)
            clients.add(client)
            while (running.get() && !socket.isClosed) {
                val frame = GazeWsFrames.read(input) ?: break
                when (frame.opcode) {
                    GazeWsFrames.OP_CLOSE -> break
                    GazeWsFrames.OP_PING -> client.send(GazeWsFrames.encode(GazeWsFrames.OP_PONG, frame.payload))
                    else -> Unit
                }
            }
            client.close()
            clients.remove(client)
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }

    private class Client(
        private val socket: Socket,
        private val output: OutputStream,
    ) {
        fun send(frame: ByteArray): Boolean =
            try {
                synchronized(output) {
                    output.write(frame)
                    output.flush()
                }
                true
            } catch (_: Exception) {
                false
            }

        fun close() {
            runCatching { socket.close() }
        }
    }
}
