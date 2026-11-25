package com.hui1601.quickyandroid.widget

/**
 * Process-local shortcut between the ANC widget and the live GATT connection
 * owned by DeviceViewModel. When the app process is gone (or the link is
 * closed) the bridge is empty and the widget falls back to
 * [AncWidgetService], which opens a short-lived connection.
 */
object WidgetAncBridge {

    @Volatile
    private var sender: ((Int) -> Boolean)? = null

    @Volatile
    private var connectedAddress: String? = null

    fun register(address: String?, block: (Int) -> Boolean) {
        connectedAddress = address
        sender = block
    }

    fun unregister() {
        sender = null
        connectedAddress = null
    }

    fun isActive(): Boolean = sender != null

    /**
     * Send to the live link only when the widget is not bound to a different
     * device; otherwise let AncWidgetService open the right connection.
     */
    fun send(target: Int, address: String?): Boolean {
        val block = sender ?: return false
        if (!address.isNullOrBlank() && !connectedAddress.isNullOrBlank() &&
            !address.equals(connectedAddress, ignoreCase = true)
        ) {
            return false
        }
        return block(target)
    }
}
