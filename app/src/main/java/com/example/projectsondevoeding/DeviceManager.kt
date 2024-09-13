package com.example.projectsondevoeding

object DeviceManager {
    var devices: Array<String> = arrayOf("available_devices")
    var subscribed: Array<String> = arrayOf()

    fun updateDevices(newDevices: Array<String>) {
        devices = newDevices
    }

    fun updateSubscribed(newSubscribes: String) {
        subscribed += newSubscribes
    }
}
