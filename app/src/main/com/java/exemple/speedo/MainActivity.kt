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
    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 101
        // Pastikan MAC Address ini benar-benar ada di daftar Bluetooth HP kamu
        private const val DEVICE_ADDRESS = "00:1D:A5:00:22:33" 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvRpmValue = findViewById(R.id.tvRpmValue)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)

        tvRpmValue.text = "0"
        tvSpeedValue.text = "0"

        // Cek izin. Jika belum ada, minta izin dulu.
        if (checkBluetoothPermissions()) {
            // Izin sudah ada, baru hubungkan
            initBluetoothSafe()
        } else {
            // Minta izin. (Aplikasi TIDAK akan crash karena belum konek bluetooth)
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

    // Ini akan terpanggil OTOMATIS setelah user klik "Allow" atau "Deny"
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Jika user klik "Allow", baru kita konek
                initBluetoothSafe()
            } else {
                Toast.makeText(this, "Izin Bluetooth ditolak. Tidak bisa konek OBD2.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initBluetoothSafe() {
        // Tampilkan pesan sedang mencoba konek
        Toast.makeText(this, "Mencoba terhubung ke OBD2...", Toast.LENGTH_SHORT).show()

        Thread {
            try {
                // Coba hubungkan
                val connection = BluetoothObdConnection(DEVICE_ADDRESS)
                if (connection.connect()) {
                    obdConnection = connection
                    runOnUiThread {
                        Toast.makeText(this, "Berhasil terhubung!", Toast.LENGTH_SHORT).show()
                    }
                    startPollingData()
                } else {
                    runOnUiThread {
                        Toast.makeText(this, "Gagal terhubung. Pastikan ELM327 menyala & MAC Address benar.", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun startPollingData() {
        isRunning = true
        
        // Gunakan Thread terpisah untuk mengambil data agar UI tidak macet
        Thread {
            while (isRunning) {
                try {
                    val connection = obdConnection
                    if (connection != null) {
                        val rpmResponse = connection.sendRawCommand("010C")
                        val rpm = parseRpm(rpmResponse)
                        
                        val speedResponse = connection.sendRawCommand("010D")
                        val speed = parseSpeed(speedResponse)

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

                try {
                    Thread.sleep(300) // Jeda 300ms
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
