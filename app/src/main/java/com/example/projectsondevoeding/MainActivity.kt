package com.example.projectsondevoeding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.json.JSONObject
import java.lang.reflect.Executable

class MainActivity : AppCompatActivity() {


    companion object {
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkAndRequestNotificationPermission()
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
        } else {
            startMQTTService()
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

        val buttonContainer = findViewById<LinearLayout>(R.id.buttonContainer)


        DeviceManager.devices.forEach { device ->
            val button = Button(this)
            button.text = "Start/Stop $device"
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            button.setOnClickListener {
                startStopSondeVoeding()
            }

            buttonContainer.addView(button)
        }

    }

    private fun startStopSondeVoeding() {
        Thread {
            try {
                val mqttClient =
                    MqttClient("tcp://192.168.0.136:1883", MqttClient.generateClientId(), null)

                val options = MqttConnectOptions().apply {
                    userName = "asvz"
                    password = "asvz".toCharArray()
                }

                mqttClient.connect(options)

                val topic = "sonde1"
                val message = MqttMessage().apply {
                    payload = "servo".toByteArray()
                }

                mqttClient.publish(topic, message)
                mqttClient.disconnect()


            } catch (e: Exception) {
                Log.e(
                    "MQTT Error",
                    "Error sending MQTT message: ${e.javaClass.simpleName} - ${e.message}",
                    e
                )
            }
        }.start()
    }
}

