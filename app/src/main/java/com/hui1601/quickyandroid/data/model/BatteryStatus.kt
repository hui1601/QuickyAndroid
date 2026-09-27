package com.hui1601.quickyandroid.data.model

data class BatteryStatus(
    val leftLevel: Int = 0,
    val leftCharging: Boolean = false,
    val rightLevel: Int = 0,
    val rightCharging: Boolean = false,
    val caseLevel: Int = 0,
    val caseCharging: Boolean = false
) {
    fun isEmpty(): Boolean = leftLevel == 0 && rightLevel == 0 && caseLevel == 0
}
