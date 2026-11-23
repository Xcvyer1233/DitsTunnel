package com.dit.tunnel

import com.jcraft.jsch.Session
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Server SOCKS5 lokal (127.0.0.1:port). Setiap koneksi CONNECT diteruskan
 * lewat channel "direct-tcpip" milik sesi SSH, sama seperti `ssh -D`.
 */
class Socks5Server(
    private val session: Session,
    private val port: Int,
    private val log: (String) -> Unit
) {
    private var server: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    @Volatile
    private var stopped = false

    fun start() {
        val s = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
        server = s
        pool.execute {
            while (!stopped) {
                try {
                    val client = s.accept()
                    pool.execute { handle(client) }
                } catch (e: Exception) {
                    if (!stopped) log("Accept error: ${e.message}")
                    break
                }
            }
        }
    }

    fun stop() {
        stopped = true
        try { server?.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    private fun handle(client: Socket) {
        try {
            client.tcpNoDelay = true
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()

            // Greeting: VER, NMETHODS, METHODS...
            if (input.readUnsignedByte() != 5) { client.close(); return }
            val nMethods = input.readUnsignedByte()
            input.readFully(ByteArray(nMethods))
            output.write(byteArrayOf(5, 0)) // tanpa autentikasi
            output.flush()

            // Request: VER, CMD, RSV, ATYP, ADDR, PORT
            input.readUnsignedByte() // VER
            val cmd = input.readUnsignedByte()
            input.readUnsignedByte() // RSV
            val atyp = input.readUnsignedByte()
            val host = when (atyp) {
                1 -> {
                    val b = ByteArray(4); input.readFully(b)
                    InetAddress.getByAddress(b).hostAddress
                }
                3 -> {
                    val len = input.readUnsignedByte()
                    val b = ByteArray(len); input.readFully(b)
                    String(b)
                }
                4 -> {
                    val b = ByteArray(16); input.readFully(b)
                    InetAddress.getByAddress(b).hostAddress
                }
                else -> { reply(output, 8); client.close(); return }
            }
            val dstPort = (input.readUnsignedByte() shl 8) or input.readUnsignedByte()

            if (cmd != 1) { reply(output, 7); client.close(); return } // hanya CONNECT

            val channel = session.getStreamForwarder(host, dstPort)
            val remoteIn = channel.inputStream
            val remoteOut = channel.outputStream
            try {
                channel.connect(15000)
            } catch (e: Exception) {
                reply(output, 5)
                client.close()
                return
            }
            reply(output, 0)

            pool.execute { pipe(remoteIn, output, client) }
            pipe(input, remoteOut, client)
            try { channel.disconnect() } catch (_: Exception) {}
        } catch (_: Exception) {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun reply(out: OutputStream, code: Int) {
        out.write(byteArrayOf(5, code.toByte(), 0, 1, 0, 0, 0, 0, 0, 0))
        out.flush()
    }

    private fun pipe(from: InputStream, to: OutputStream, client: Socket) {
        val buf = ByteArray(16 * 1024)
        try {
            while (true) {
                val n = from.read(buf)
                if (n < 0) break
                to.write(buf, 0, n)
                to.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }
}
