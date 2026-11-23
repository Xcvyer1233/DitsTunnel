package com.dit.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TunnelService : Service() {

    companion object {
        const val ACTION_START = "com.dit.tunnel.START"
        const val ACTION_STOP = "com.dit.tunnel.STOP"
        const val SOCKS_PORT = 1080
        private const val CHANNEL_ID = "dits_tunnel"

        @Volatile var running = false
        var onLog: ((String) -> Unit)? = null
        var onState: ((Boolean) -> Unit)? = null
        private val main = Handler(Looper.getMainLooper())

        fun log(msg: String) {
            val t = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            main.post { onLog?.invoke("[$t] $msg") }
        }

        fun setState(r: Boolean) {
            running = r
            main.post { onState?.invoke(r) }
        }
    }

    private var session: Session? = null
    private var socks: Socks5Server? = null
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(1, buildNotification())
                val host = intent.getStringExtra("host").orEmpty()
                val port = intent.getIntExtra("port", 22)
                val user = intent.getStringExtra("user").orEmpty()
                val pass = intent.getStringExtra("pass").orEmpty()
                connect(host, port, user, pass)
            }
            ACTION_STOP -> {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun connect(host: String, port: Int, user: String, pass: String) {
        if (worker?.isAlive == true) return
        worker = Thread {
            try {
                log("Menghubungkan ke $host:$port ...")
                val jsch = JSch()
                val s = jsch.getSession(user, host, port)
                s.setPassword(pass)
                // Catatan: host key tidak diverifikasi (mirip HTTP Custom).
                s.setConfig("StrictHostKeyChecking", "no")
                s.setServerAliveInterval(30000)
                s.connect(20000)
                session = s
                log("SSH terhubung.")

                val server = Socks5Server(s, SOCKS_PORT) { log(it) }
                server.start()
                socks = server
                log("SOCKS5 aktif di 127.0.0.1:$SOCKS_PORT")
                setState(true)

                while (s.isConnected && running) Thread.sleep(1000)
                if (running) log("Koneksi SSH terputus.")
            } catch (e: Exception) {
                log("Gagal: ${e.message}")
            } finally {
                disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }.also {
            running = true
            it.start()
        }
    }

    private fun disconnect() {
        try { socks?.stop() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        socks = null
        session = null
        if (running) log("Terputus.")
        setState(false)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Dit's Tunnel", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
        else Notification.Builder(this)
        return b.setContentTitle("Dit's Tunnel")
            .setContentText("Tunnel aktif — SOCKS5 127.0.0.1:$SOCKS_PORT")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
