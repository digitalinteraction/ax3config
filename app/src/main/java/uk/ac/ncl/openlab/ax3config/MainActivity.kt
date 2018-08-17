/*
* Copyright (c) 2018, Newcastle University, UK.
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions are met:
* 1. Redistributions of source code must retain the above copyright notice,
*    this list of conditions and the following disclaimer.
* 2. Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
*/

// Open Movement AX3 Configuration Tool for Android
// Dan Jackson, 2018

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
            val written = port.writeString("$outString\r\n", 2000)
            log("WRITTEN: $written")

            val lines = port.readLines(200, false)
            log("READ: ${lines.size}")
            for (line in lines) {
                log("<<< $line")
            }
        } catch (e: IOException) {
            // Deal with error.
            log("IO EXCEPTION: ${e.message}")
        } catch (e: Exception) {
            // Deal with error.
            log("EXCEPTION: ${e.message}")
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
        buttonSend.setOnClickListener {
            val text = editTextInput.text
            log("SEND: $text")
            var usbDevices = UsbSerialPort.getDevices(usbManager);
            // Find device from attached devices
            log("(${usbDevices.size} devices)")
            if (usbDevices.size > 0) {
                var usbDevice = usbDevices[0];
                // log("DEVICE: ${usbDevice.manufacturerName} // ${usbDevice.productName} // ${usbDevice.serialNumber}")
                log("DEVICE: Requesting permission...")
                usbManager.requestPermission(usbDevice, permissionIntent)
            }
            log("---")
        }
    }
}

