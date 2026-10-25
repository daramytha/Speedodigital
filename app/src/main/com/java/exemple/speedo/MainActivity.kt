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
    
    private var obdConnection: BluetoothObdConnection? = null
    
    // PERBAIKAN 1: getMainThreadLooper() diubah menjadi getMainLooper()
    private val handler = Handler(Looper.getMainLooper()) 
    private var isRunning = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 101
        // Ganti dengan MAC Address adapter ELM327 Anda yang sebenarnya
        private const val DEVICE_ADDRESS = "00:1D:A5:00:22:33" 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Hubungkan komponen UI dengan aman
        tvRpmValue = findViewById(R.id.tvRpmValue)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)

        // Set nilai awal agar UI langsung tampil normal tanpa menunggu data
        tvRpmValue.text = "0"
        tvSpeedValue.text = "0"

        // Periksa izin Bluetooth
        if (checkBluetoothPermissions()) {
            initBluetoothSafe()
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
                initBluetoothSafe()
            } else {
                Toast.makeText(this, "Izin Bluetooth ditolak. Aplikasi berjalan dalam mode offline.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initBluetoothSafe() {
        Thread {
            try {
                val connection = BluetoothObdConnection(DEVICE_ADDRESS)
                if (connection.connect()) {
                    obdConnection = connection
                    startPollingData()
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Gagal terhubung ke OBD2. Periksa pairing Bluetooth Anda.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error Bluetooth: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // PERBAIKAN 2: Proses polling dipindah ke Background Thread agar UI tidak nge-lag/ANR
    private fun startPollingData() {
        isRunning = true
        
        Thread {
            while (isRunning) {
                try {
                    val connection = obdConnection
                    if (connection != null) {
                        // 1. Minta Data RPM (PID: 010C)
                        val rpmResponse = connection.sendRawCommand("010C")
                        val rpm = parseRpm(rpmResponse)
                        
                        // 2. Minta Data Kecepatan (PID: 010D)
                        val speedResponse = connection.sendRawCommand("010D")
                        val speed = parseSpeed(speedResponse)

                        // Kembali ke Main Thread HANYA untuk update UI
                        runOnUiThread {
                            if (isRunning) {
                                tvRpmValue.text = rpm.toString()
                                tvSpeedValue.text = speed.toString()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Jeda 300ms menggunakan Thread.sleep (karena kita sudah di background thread)
                try {
                    Thread.sleep(300)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.start()
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
            obdConnection?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
