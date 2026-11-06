package com.interactivebot.remote

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.interactivebot.remote.databinding.ActivityMainBinding

/**
 * Chat remote for the interactive-ai-bot Pi over Bluetooth SPP.
 * Pick the paired Pi, connect, then type (or tap a command button); the bot's
 * reply streams back into the chat list.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val messages = mutableListOf<Msg>()
    private val adapter = ChatAdapter(messages)
    private lateinit var bt: BtClient

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) pickAndConnect() else toast("Bluetooth permission needed") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.chat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        b.chat.adapter = adapter

        // Each received line appends to the running bot reply; a bare marker
        // line starts a fresh reply. We keep it simple: every inbound line is a
        // bot message of its own, which reads cleanly for line-based replies.
        bt = BtClient(
            onLine = { line -> runOnUiThread { addBot(line) } },
            onClosed = { why -> runOnUiThread {
                addBot("— $why —")
                b.btnConnect.isEnabled = true
                b.btnConnect.text = "Connect to Pi"
            } },
        )

        b.btnConnect.setOnClickListener { ensurePermThenConnect() }
        b.btnSend.setOnClickListener { sendInput() }
        b.btnVoice.setOnClickListener { sendCommand("/voice") }
        b.btnCam.setOnClickListener { sendCommand("/cam") }
        b.btnStatus.setOnClickListener { sendCommand("/status") }
    }

    private fun ensurePermThenConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) { permLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT); return }
        }
        pickAndConnect()
    }

    private fun pickAndConnect() {
        val mgr = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = mgr.adapter
        if (adapter == null || !adapter.isEnabled) { toast("Turn on Bluetooth first"); return }

        val devices = bt.pairedDevices(adapter)
        if (devices.isEmpty()) { toast("No paired devices — pair the Pi in Settings first"); return }

        val names = devices.map { safeName(it) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Connect to…")
            .setItems(names) { _, i ->
                b.btnConnect.isEnabled = false
                b.btnConnect.text = "Connecting…"
                addBot("connecting to ${names[i]}…")
                bt.connect(devices[i])
            }
            .show()
    }

    private fun sendInput() {
        val text = b.input.text.toString().trim()
        if (text.isEmpty()) return
        adapter.add(Msg(text, true))
        b.chat.scrollToPosition(messages.size - 1)
        bt.send(text)
        b.input.setText("")
    }

    private fun sendCommand(cmd: String) {
        adapter.add(Msg(cmd, true))
        bt.send(cmd)
        b.chat.scrollToPosition(messages.size - 1)
    }

    private fun addBot(line: String) {
        adapter.add(Msg(line, false))
        b.chat.scrollToPosition(messages.size - 1)
    }

    private fun safeName(d: android.bluetooth.BluetoothDevice): String =
        try { d.name ?: d.address } catch (e: SecurityException) { d.address }

    private fun toast(s: String) =
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        bt.close()
    }
}
