package com.example.obd2app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvRpmValue: TextView
    private lateinit var tvSpeedValue: TextView
    private lateinit var obdConnection: BluetoothObdConnection
    private val handler = Handler(Looper.getMainThreadLooper())
    private var isRunning = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvRpmValue = findViewById(R.id.tvRpmValue)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)

        // Periksa izin Bluetooth saat aplikasi dibuka
        if (checkBluetoothPermissions()) {
            initBluetoothAndPoll()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun checkBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initBluetoothAndPoll()
            } else {
                Toast.makeText(this, "Izin Bluetooth diperlukan untuk menghubungkan OBD2!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initBluetoothAndPoll() {
        // Ganti dengan MAC Address adapter ELM327 Anda yang sebenarnya
        val deviceAddress = "00:1D:A5:00:22:33" 

        Thread {
            try {
                obdConnection = BluetoothObdConnection(deviceAddress)
                if (obdConnection.connect()) {
                    startPollingData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun startPollingData() {
        isRunning = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                try {
                    // 1. Minta Data RPM (PID: 010C)
                    val rpmResponse = obdConnection.sendRawCommand("010C")
                    val rpm = parseRpm(rpmResponse)
                    runOnUiThread { tvRpmValue.text = rpm.toString() }

                    // 2. Minta Data Kecepatan (PID: 010D)
                    val speedResponse = obdConnection.sendRawCommand("010D")
                    val speed = parseSpeed(speedResponse)
                    runOnUiThread { tvSpeedValue.text = speed.toString() }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Ambil data setiap 200ms
                handler.postDelayed(this, 200)
            }
        })
    }

    private fun parseRpm(response: String): Int {
        return try {
            val clean = response.replace(" ", "").replace(">", "")
            if (clean.startsWith("410C") && clean.length >= 8) {
                val a = clean.substring(4, 6).toInt(16)
                val b = clean.substring(6, 8).toInt(16)
                ((a * 256) + b) / 4
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    private fun parseSpeed(response: String): Int {
        return try {
            val clean = response.replace(" ", "").replace(">", "")
            if (clean.startsWith("410D") && clean.length >= 6) {
                clean.substring(4, 6).toInt(16)
            } else 0
        } catch (e: Exception) {
            0
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            if (::obdConnection.isInitialized) {
                obdConnection.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
