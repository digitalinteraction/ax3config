package uk.ac.ncl.openlab.ax3config

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.file.Files.size
import java.sql.DriverManager.getDrivers




class MainActivity : AppCompatActivity() {

    private var allDevices = mutableSetOf<String>()

    private fun log(message: String) {
        editTextLog.append(message + "\n")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        log("Started.")

        // button is the Button id
        buttonList.setOnClickListener {
            log("LIST:")

            val dev = File("/dev")
            val files = dev.listFiles()
            for (file in files) {
                val path = file.absolutePath
                if (!allDevices.contains(path)) {
                    log("DEVICE: $path")
                    allDevices.add(path);
                }
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

