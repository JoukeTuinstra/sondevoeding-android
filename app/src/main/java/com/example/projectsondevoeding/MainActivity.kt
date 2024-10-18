package com.example.projectsondevoeding

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.lang.reflect.Executable

class MainActivity : AppCompatActivity(), MQTTServiceCallback {


    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1
    }

    private var mqttService: MQTTService? = null
    private var isBound = false
    private var onCooldown = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as MQTTService.LocalBinder
            mqttService = binder.getService()
            mqttService?.setCallback(this@MainActivity)  // Set the callback to this activity
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkAndRequestNotificationPermission()
        startMQTTService()

        // Bind to the service
        Intent(this, MQTTService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onDeviceAvailable(deviceName: String) {
        if (DeviceManager.devices.contains(deviceName)) {
            Log.d("MainActivity", "Device available: $deviceName")

            runOnUiThread {
                val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)

                // First button
                val firstButton = Button(this)
                firstButton.text = "Abonneer op: $deviceName"
                firstButton.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                firstButton.setOnClickListener {
                    manageNotifs(deviceName)
                }

                // Second button
                val secondButton = Button(this)
                secondButton.text = "Aan/Uit"
                secondButton.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                secondButton.setOnClickListener {
                    startStopSondevoeding(deviceName)  // A function to show device details (define this)
                }

                // Add both buttons to the container
                buttonContainer.addView(firstButton)
                buttonContainer.addView(secondButton)
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkAndRequestNotificationPermission() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_POST_NOTIFICATIONS
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permission", "POST_NOTIFICATIONS permission accepted.")


                } else {
                    Log.d("Permission", "POST_NOTIFICATIONS permission denied.")
                }
            }
        }

    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun refreshAvailable(view: View) {
        if (isBound) {
            mqttService?.sendMQTTMessage(
                "available_devices",
                MqttMessage().apply { payload = "who_is_here".toByteArray() })
            DeviceManager.devices = arrayOf()
            findViewById<LinearLayout>(R.id.buttonContainer).removeAllViews()

            val button = view as Button
            button.isEnabled = false

            Handler(Looper.getMainLooper()).postDelayed({
                button.isEnabled = true
            }, 1500)

        } else {
            Log.e("MainActivity", "Service is not bound!")
        }
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun manageNotifs(device: String) {

        if (!DeviceManager.subscribed.contains(device)) {
            DeviceManager.updateSubscribed(device)
        }

        if (isBound) {
            mqttService?.subscribeMQTT(arrayOf("available_devices", "beep_detection"))
        } else {
            Log.e("MainActivity", "Service is not bound!")
        }
    }

    private fun startMQTTService() {
        val serviceIntent = Intent(this, MQTTService::class.java)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e("MQTT service error", "Is the server running?", e)
        }
    }

    private fun startStopSondevoeding(device: String) {
        try {
            val mqttClient =
                MqttClient("tcp://192.168.0.136:1883", MqttClient.generateClientId(), null)

            val options = MqttConnectOptions().apply {
                userName = "asvz"
                password = "asvz".toCharArray()
            }

            mqttClient.connect(options)
            mqttClient.publish(device, MqttMessage().apply { payload = "servo".toByteArray() })
            mqttClient.disconnect()


        } catch (e: Exception) {
            Log.e(
                "MQTT Error",
                "Error sending MQTT message: ${e.javaClass.simpleName} - ${e.message}",
                e
            )
        } catch (e: MqttException) {
            Log.e(
                "MQTT Error",
                "Error sending MQTT message: ${e.javaClass.simpleName} - ${e.message}",
                e
            )
        }
    }
}

interface MQTTServiceCallback {
    fun onDeviceAvailable(deviceName: String)
}

