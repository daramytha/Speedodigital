import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvRpmValue: TextView
    private lateinit var tvSpeedValue: TextView
    
    private lateinit var obdConnection: BluetoothObdConnection
    private val handler = Handler(Looper.getMainThreadLooper())
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvRpmValue = findViewById(R.id.tvRpmValue)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)

        // Ganti dengan MAC Address adapter ELM327 Anda yang sudah di-pairing sebelumnya
        val deviceAddress = "00:1D:A5:00:22:33" 

        // Jalankan koneksi di background thread agar tidak ANR (Application Not Responding)
        Thread {
            obdConnection = BluetoothObdConnection(deviceAddress)
            if (obdConnection.connect()) {
                startPollingData()
            }
        }.start()
    }

    private fun startPollingData() {
        isRunning = true
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return

                // 1. Minta Data RPM (PID: 010C)
                val rpmResponse = obdConnection.sendRawCommand("010C")
                val rpm = parseRpm(rpmResponse)
                tvRpmValue.text = rpm.toString()

                // 2. Minta Data Kecepatan (PID: 010D)
                val speedResponse = obdConnection.sendRawCommand("010D")
                val speed = parseSpeed(speedResponse)
                tvSpeedValue.text = speed.toString()

                // Ambil data setiap 200ms
                handler.postDelayed(this, 200)
            }
        })
    }

    // Rumus RPM: ((A * 256) + B) / 4
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

    // Rumus Kecepatan: A (langsung dalam km/h)
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
        obdConnection.disconnect()
    }
}
