import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothObdConnection(private val deviceAddress: String) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null

    // UUID standar untuk Serial Port Profile (SPP) yang digunakan ELM327
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connect(): Boolean {
        val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return false
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            inputStream = bluetoothSocket?.inputStream
            
            // Inisialisasi awal ELM327
            sendRawCommand("ATZ")   // Reset ELM327
            sendRawCommand("ATE0")  // Matikan Echo
            sendRawCommand("ATSP0") // Auto protocol
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            disconnect()
            return false
        }
    }

    fun sendRawCommand(command: String): String {
        try {
            val cmd = "$command\r"
            outputStream?.write(cmd.toByteArray())
            outputStream?.flush()

            val buffer = ByteArray(1024)
            val bytes = inputStream?.read(buffer) ?: 0
            return String(buffer, 0, bytes).trim()
        } catch (e: Exception) {
            e.printStackTrace()
            return "ERROR"
        }
    }

    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            bluetoothSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
