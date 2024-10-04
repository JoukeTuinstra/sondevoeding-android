package com.example.projectsondevoeding

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Binder
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
import org.json.JSONObject

class MQTTService : Service() {

    companion object {
        private const val CHANNEL_ID = "foreground_service_channel"
        private const val mqttBroker = "tcp://192.168.0.136:1883"
        private var hasChecked = false
    }

    private var callback: MQTTServiceCallback? = null  // Declare the callback
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MQTTService = this@MQTTService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setCallback(callback: MQTTServiceCallback) {
        this.callback = callback
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        subscribeMQTT(arrayOf("available_devices"))
        createNotificationChannel()
        startForegroundService()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Klaar voor gebruik")
            .setContentText("U kunt de app sluiten.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun subscribeMQTT(topics: Array<String>) {
        val mqttClient = MqttClient(mqttBroker, MqttClient.generateClientId(), null)
        val options = MqttConnectOptions().apply {
            userName = "asvz"
            password = "asvz".toCharArray()
        }

        mqttClient.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) {
                Log.e("MQTTService", "Connection lost", cause)
            }

            @RequiresApi(Build.VERSION_CODES.TIRAMISU)
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                Log.d("MQTTService", "Message arrived: ${message.toString()}")
                message?.let {
                    val messageString = String(it.payload)  // Convert byte array to string
                    if (messageString.startsWith("beep")) {
                        sendNotification(messageString.last())
                        println(messageString.last())
                    }

                    if (messageString.startsWith("who is here{")) {
                        val updatedString = messageString.replace("who is here", "")
                        val availableDevice =
                            JSONObject(updatedString).get("device_name").toString()

                        DeviceManager.updateDevices(availableDevice)
                        callback?.onDeviceAvailable(availableDevice)


                    }
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // No-op
            }
        })

        try {
            mqttClient.connect(options)
            mqttClient.subscribe(topics)

            if (!hasChecked) {
                sendMQTTMessage(
                    "available_devices",
                    MqttMessage().apply { payload = "who_is_here".toByteArray() })
                hasChecked = true
            }
        } catch (e: Exception) {
            Log.e("Error", "Error while subscribing ${e.javaClass.simpleName} - ${e}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun sendMQTTMessage(topic: String, message: MqttMessage) {
        Thread {
            try {
                val mqttClient = MqttClient(mqttBroker, MqttClient.generateClientId(), null)

                val options = MqttConnectOptions().apply {
                    userName = "asvz"
                    password = "asvz".toCharArray()
                }

                mqttClient.connect(options)
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


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun sendNotification(id: Char) {
        val ACTION_SNOOZE = "projectsondevoeding.ACTION_SNOOZE$id"

        val snoozeIntent = Intent(this, NotificationReceiver::class.java).apply {
            action = ACTION_SNOOZE
        }

        val snoozePendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Sondevoeding")
            .setContentText("Sondevoeding piept!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Sondevoeding $id piept! Zorg ervoor dat u actie onderneemt.")
            )
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
}

class NotificationReceiver : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action?.startsWith("projectsondevoeding.ACTION_SNOOZE") == true) {
            // Handle the action here, for example, call buttonClicked()
            Log.d("NotificationReceiver", "Ignore button clicked")
            val service = MQTTService()
            val id = intent.action?.last()
            println("sonde$id")
            service.sendMQTTMessage(
                "sonde$id", //
                MqttMessage().apply { payload = "servo".toByteArray() })
        }
    }

}

