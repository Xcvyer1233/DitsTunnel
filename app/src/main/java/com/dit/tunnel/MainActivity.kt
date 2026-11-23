package com.dit.tunnel

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var etHost: EditText
    private lateinit var etPort: EditText
    private lateinit var etUser: EditText
    private lateinit var etPass: EditText
    private lateinit var btn: Button
    private lateinit var tvLog: TextView
    private lateinit var scroll: ScrollView

    private val prefs by lazy { getSharedPreferences("dits", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContentView(buildUi())

        etHost.setText(prefs.getString("host", ""))
        etPort.setText(prefs.getString("port", "22"))
        etUser.setText(prefs.getString("user", ""))

        TunnelService.onLog = { line ->
            tvLog.append(line + "\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
        TunnelService.onState = { updateButton(it) }
        updateButton(TunnelService.running)

        btn.setOnClickListener {
            if (TunnelService.running) {
                startService(Intent(this, TunnelService::class.java).setAction(TunnelService.ACTION_STOP))
            } else {
                val host = etHost.text.toString().trim()
                val port = etPort.text.toString().trim().toIntOrNull() ?: 22
                val user = etUser.text.toString().trim()
                val pass = etPass.text.toString()
                if (host.isEmpty() || user.isEmpty()) {
                    tvLog.append("Host dan username wajib diisi.\n")
                    return@setOnClickListener
                }
                prefs.edit()
                    .putString("host", host)
                    .putString("port", port.toString())
                    .putString("user", user)
                    .apply()
                val i = Intent(this, TunnelService::class.java)
                    .setAction(TunnelService.ACTION_START)
                    .putExtra("host", host)
                    .putExtra("port", port)
                    .putExtra("user", user)
                    .putExtra("pass", pass)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            }
        }
    }

    override fun onDestroy() {
        TunnelService.onLog = null
        TunnelService.onState = null
        super.onDestroy()
    }

    private fun updateButton(running: Boolean) {
        btn.text = if (running) "DISCONNECT" else "CONNECT"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun field(hint: String, type: Int = InputType.TYPE_CLASS_TEXT) = EditText(this).apply {
        this.hint = hint
        inputType = type
        setSingleLine()
    }

    private fun buildUi(): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "Dit's Tunnel"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "SSH tunnel → SOCKS5 127.0.0.1:${TunnelService.SOCKS_PORT}"
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })
        etHost = field("Host / IP server SSH")
        etPort = field("Port", InputType.TYPE_CLASS_NUMBER)
        etUser = field("Username")
        etPass = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        listOf(etHost, etPort, etUser, etPass).forEach { root.addView(it) }

        btn = Button(this).apply { text = "CONNECT" }
        root.addView(btn)

        scroll = ScrollView(this)
        tvLog = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        scroll.addView(tvLog)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }
}
