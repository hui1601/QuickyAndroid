package com.hui1601.quickyandroid.widget

import android.content.Context

/** The saved earbuds a specific placed widget is bound to. */
data class WidgetBinding(
    /** null means "current / last connected device". */
    val address: String?,
    val name: String = ""
)

object WidgetBindingStore {
    private const val PREFS = "widget_bindings"

    fun get(context: Context, appWidgetId: Int): WidgetBinding {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val address = prefs.getString("${appWidgetId}_address", null)
            ?.takeIf { it.isNotBlank() }
        val name = prefs.getString("${appWidgetId}_name", "").orEmpty()
        return WidgetBinding(address, name)
    }

    fun set(context: Context, appWidgetId: Int, address: String?, name: String) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (address.isNullOrBlank()) {
            editor.remove("${appWidgetId}_address")
            editor.remove("${appWidgetId}_name")
        } else {
            editor.putString("${appWidgetId}_address", address)
            editor.putString("${appWidgetId}_name", name)
        }
        editor.apply()
    }

    fun clear(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("${appWidgetId}_address")
            .remove("${appWidgetId}_name")
            .apply()
    }
}
