package com.example.projectsondevoeding

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

class MQTTService : Service() {

    private lateinit var mqttClient: MqttClient

    companion object {
        private const val CHANNEL_ID = "ForegroundServiceChannel"
        private const val mqttBroker = "tcp://192.168.0.194:1883"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        connectToMQTT()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MQTT Service")
            .setContentText("Service is running")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    private fun connectToMQTT() {
        mqttClient = MqttClient(mqttBroker, MqttClient.generateClientId(), null)
        val options = MqttConnectOptions().apply {
            userName = "jouke"
            password = "jouke".toCharArray()
        }
        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.d("MQTTService", "Connection lost", cause)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d(topic, message.toString())
                sendNotification()
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Handle message delivery completion
            }
        })

        mqttClient.connect(options)
        mqttClient.subscribe("beep_detection2")
    }

    private fun sendNotification() {
        val ACTION_SNOOZE = "projectsondevoeding.ACTION_SNOOZE"

        val snoozeIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }

        val snoozePendingIntent: PendingIntent = PendingIntent.getBroadcast(this, 0, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
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
                    this@MQTTService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("Notification allowed", "Sending notification")
            }
            notify(1, notification)
        }
    }

    fun sendMQTTServo() {
        Thread {
            try {
                val mqttClient = MqttClient(mqttBroker, MqttClient.generateClientId(), null)

                val options = MqttConnectOptions().apply {
                    userName = "jouke"
                    password = "jouke".toCharArray()
                }

                mqttClient.connect(options)

                val topic = "test2"
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
}

class NotificationReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "projectsondevoeding.ACTION_SNOOZE") {
            // Handle the action here, for example, call buttonClicked()
            Log.d("NotificationReceiver", "Snooze button clicked")

            // Cancel the notification
            val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(0)

            // Optionally, you can call buttonClicked() or any other method if needed
            val service = MQTTService()
            service.sendMQTTServo()
        }
    }
}
