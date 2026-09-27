package com.hui1601.quickyandroid.data.model

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Scanning : ConnectionState()
    data object Connecting : ConnectionState()
    data class Connected(val deviceName: String, val address: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
