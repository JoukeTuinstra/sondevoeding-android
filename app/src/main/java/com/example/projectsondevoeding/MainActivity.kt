package com.example.projectsondevoeding

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity() {


    companion object {
        private const val CHANNEL_ID = "channel_id"
        private const val REQUEST_CODE_POST_NOTIFICATIONS = 1
        private const val mqttBroker = "tcp://192.168.0.194:1883"
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        subscribeMQTT("beep_detection")
        createNotificationChannel()

    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
        } else {
            sendNotification()
        }
    }

    private fun subscribeMQTT(topic: String) {
        val mqttClient = MqttClient(mqttBroker, MqttClient.generateClientId(), null)
        val options = MqttConnectOptions().apply {
            userName = "remco"
            password = "remco".toCharArray()

        }
        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.d("Connection lost", "Connection was lost, cause:", cause)
            }

            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d("beep", "message arrived")
                checkAndRequestNotificationPermission()

            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Handle message delivery completion
            }
        })

        mqttClient.connect(options)
        mqttClient.subscribe(topic)
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun sendMQTTServo() {
        Thread {
            try {
                val mqttClient = MqttClient(mqttBroker, MqttClient.generateClientId(), null)
                Log.d("MQTT", "connected")

                val options = MqttConnectOptions().apply {
                    userName = "remco"
                    password = "remco".toCharArray()
                }

                mqttClient.connect(options)

                val topic = "test"
                val message = MqttMessage().apply {
                    payload = "servo".toByteArray()
                }

                mqttClient.publish(topic, message)
                mqttClient.disconnect()



            } catch (e: Exception) {
                Log.e("MQTT Error", "Error sending MQTT message: ${e.javaClass.simpleName} - ${e.message}", e)
            }
        }.start()
    }



    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun sendNotification() {
        val ACTION_SNOOZE = "projectsondevoeding.ACTION_SNOOZE"

        val snoozeIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }

        val snoozePendingIntent: PendingIntent = PendingIntent.getBroadcast(this, 0, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sondevoeding")
            .setContentText("Sondevoeding piept!")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Sondevoeding piept! Zorg ervoor dat u onmiddellijk actie onderneemt."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .addAction(R.drawable.ic_launcher_foreground, "Snooze", snoozePendingIntent)
            .setAutoCancel(true)
            .build()

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("Notification allowed", "Sending notification")
            }
            notify(1, notification)
        }

    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    sendNotification()
                } else {
                    Log.d("Permission", "POST_NOTIFICATIONS permission denied.")
                }
            }
        }
    }
}

class NotificationReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "projectsondevoeding.ACTION_SNOOZE") {
            // Handle the action here, for example, call buttonClicked()
            Log.d("NotificationReceiver", "Ignore button clicked")
            val mainActivity = MainActivity()
            mainActivity.sendMQTTServo()
        }
    }
}
