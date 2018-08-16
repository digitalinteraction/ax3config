package uk.ac.ncl.openlab.ax3config

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import android.content.IntentFilter
import android.hardware.usb.UsbDevice

private const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"


class MainActivity : AppCompatActivity() {

    private fun log(message: String) {
        editTextLog.append(message + "\n")
    }

    private lateinit var permissionIntent: PendingIntent

    private lateinit var usbManager: UsbManager

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            log("Permission allowed -- setting up device communication...")
                            connect(device)
                        }
                    } else {
                        log("Permission denied for device.")
                    }
                }
            }
        }
    }

    fun connect(device: UsbDevice) {
        log("CONNECT")
        val port = UsbSerialPort(device)
        try {
            // log("PORT: $port")
            port.open(usbManager)
            log("Connected to: " + port.serialNumber)

            val outString = editTextInput.text
            log(">>> $outString")
            val outBuffer = "$outString\r\n".toByteArray()
            val numBytesWritten = port.write(outBuffer, 2000)
            log("WRITTEN: $numBytesWritten")

            val inBuffer = ByteArray(256)
            val numBytesRead = port.read(inBuffer, 2000)
            log("READ: $numBytesRead")
            val inString = String(inBuffer, 0, numBytesRead).trim()
            log("<<< $inString")


        } catch (e: IOException) {
            // Deal with error.
            log("IO EXCEPTION")
        } catch (e: Exception) {
            // Deal with error.
            log("EXCEPTION ${e.message}")
        } finally {
            log("Closing...")
            port.close()
//connection.close()
            log("Closed")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        log("Started.")

        permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), 0)
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // button is the Button id
        buttonList.setOnClickListener {
            log("LIST:")
            var usbDevice: UsbDevice? = UsbSerialPort.findFirstDevice(usbManager);
            // Find device from attached devices
            if (usbDevice == null) {
                log("(no device)")
            } else {
                log("DEVICE: ${usbDevice}")
                usbManager.requestPermission(usbDevice, permissionIntent)
            }
            log("---")
        }

        // button is the Button id
        buttonSend.setOnClickListener {
            val text = editTextInput.text
            log(">>> $text")
        }
    }
}

