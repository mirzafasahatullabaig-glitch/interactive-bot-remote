package com.interactivebot.remote

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.BufferedReader
import java.util.UUID
import kotlin.concurrent.thread

/**
 * Bluetooth Classic SPP client for the interactive-ai-bot Pi server.
 *
 * The Pi advertises a Serial Port (SPP) service; we connect with the standard
 * SPP UUID and exchange newline-terminated text: one request per line out, the
 * bot's reply streamed back in. Reading runs on its own thread and pushes each
 * received line to [onLine]; connection loss calls [onClosed].
 */
class BtClient(
    private val onLine: (String) -> Unit,
    private val onClosed: (String) -> Unit,
) {
    companion object {
        // Well-known SPP UUID -- matches the Pi's advertised Serial Port service.
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    @Volatile private var running = false

    /** Paired devices, so the UI can let the user pick the Pi. Caller must hold
     *  BLUETOOTH_CONNECT on Android 12+. */
    fun pairedDevices(adapter: BluetoothAdapter): List<BluetoothDevice> =
        try { adapter.bondedDevices?.toList() ?: emptyList() }
        catch (e: SecurityException) { emptyList() }

    /** Connect on a background thread; invokes [onLine]/[onClosed] as events arrive. */
    fun connect(device: BluetoothDevice) {
        thread(name = "bt-connect") {
            try {
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()                 // blocks until connected or throws
                socket = s
                running = true
                onLine("✓ connected to ${safeName(device)}")
                readLoop(s.inputStream.bufferedReader())
            } catch (e: SecurityException) {
                onClosed("permission denied: ${e.message}")
            } catch (e: Exception) {
                onClosed("connect failed: ${e.message}")
            }
        }
    }

    private fun readLoop(reader: BufferedReader) {
        try {
            while (running) {
                val line = reader.readLine() ?: break   // null = peer closed
                onLine(line)
            }
        } catch (e: Exception) {
            // fall through to onClosed below
        } finally {
            running = false
            onClosed("disconnected")
        }
    }

    /** Send one line to the bot (newline appended, as the Pi's readline expects). */
    fun send(text: String) {
        val s = socket ?: return
        thread(name = "bt-send") {
            try {
                s.outputStream.write((text + "\n").toByteArray(Charsets.UTF_8))
                s.outputStream.flush()
            } catch (e: Exception) {
                onClosed("send failed: ${e.message}")
            }
        }
    }

    fun close() {
        running = false
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun safeName(d: BluetoothDevice): String =
        try { d.name ?: d.address } catch (e: SecurityException) { d.address }
}
