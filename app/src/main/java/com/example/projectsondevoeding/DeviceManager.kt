package com.example.projectsondevoeding

object DeviceManager {
    var devices: Array<String> = arrayOf()
    var subscribed: Array<String> = arrayOf()

    fun updateDevices(newDevices: String) {
        devices += newDevices
    }

    fun updateSubscribed(newSubscribes: String) {
        subscribed += newSubscribes
    }
}
