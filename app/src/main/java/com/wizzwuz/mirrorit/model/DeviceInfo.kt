package com.wizzwuz.mirrorit.model

data class DeviceInfo(
    val deviceName: String,
    val ipAddress: String,
    val port: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val osVersion: String = "Android",
    val hasShizuku: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
