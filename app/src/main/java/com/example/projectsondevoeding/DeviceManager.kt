package com.example.projectsondevoeding

object DeviceManager {
    var devices: Array<String> = arrayOf("available_devices")

    fun updateDevices(newDevices: Array<String>) {
        devices = newDevices
    }
}
