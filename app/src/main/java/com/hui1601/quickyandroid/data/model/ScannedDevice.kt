package com.hui1601.quickyandroid.data.model

data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val vendorId: Int,
    val leftBattery: Int,
    val rightBattery: Int,
    val caseBattery: Int,
    val isLeftCharging: Boolean,
    val isRightCharging: Boolean,
    val isCaseCharging: Boolean,
    val controlMac: String,
    val otherMac: String,
    val product: ProductMetadata? = null
)
