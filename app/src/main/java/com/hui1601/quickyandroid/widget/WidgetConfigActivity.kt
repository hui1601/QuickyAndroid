package com.hui1601.quickyandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hui1601.quickyandroid.data.repository.SavedDevice
import com.hui1601.quickyandroid.data.repository.SavedDeviceStore
import com.hui1601.quickyandroid.ui.theme.QuickyAndroidTheme
import kotlinx.coroutines.flow.first

/**
 * Launched by the launcher when a Quicky widget is placed. Lets the user bind
 * the widget to the current/last device or to one specific saved pair.
 */
class WidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            QuickyAndroidTheme {
                WidgetConfigScreen(
                    widgetId = widgetId,
                    onConfirm = { address, name -> confirm(widgetId, address, name) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun confirm(widgetId: Int, address: String?, name: String) {
        WidgetBindingStore.set(this, widgetId, address, name)
        DeviceWidgetStore.pushAll(this)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WidgetConfigScreen(
    widgetId: Int,
    onConfirm: (address: String?, name: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val existing = remember { WidgetBindingStore.get(context, widgetId) }
    var devices by remember { mutableStateOf<List<SavedDevice>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selectedAddress by rememberSaveable {
        mutableStateOf(existing.address)
    }
    var selectedName by rememberSaveable { mutableStateOf(existing.name) }

    LaunchedEffect(Unit) {
        devices = runCatching { SavedDeviceStore(context).devices.first() }.getOrDefault(emptyList())
        if (existing.address != null && devices.none { it.address == existing.address }) {
            // The bound pair was forgotten since the widget was configured.
            selectedAddress = null
            selectedName = ""
        }
        loaded = true
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "Widget device",
                    style = MaterialTheme.typography.headlineMediumEmphasized
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose which earbuds this widget shows and controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (!loaded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        LoadingIndicator(modifier = Modifier.size(40.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DeviceOption(
                            title = "Current or last connected",
                            subtitle = "Follow whichever earbuds you use",
                            selected = selectedAddress == null,
                            onClick = {
                                selectedAddress = null
                                selectedName = ""
                            }
                        )
                        devices.forEach { device ->
                            DeviceOption(
                                title = device.name,
                                subtitle = device.address,
                                selected = selectedAddress == device.address,
                                onClick = {
                                    selectedAddress = device.address
                                    selectedName = device.name
                                }
                            )
                        }
                    }

                    if (devices.isEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No saved earbuds yet. Connect them in Quicky first; " +
                                "the widget will follow the last used device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Spacer(modifier = Modifier.size(8.dp))
                    Button(
                        onClick = {
                            onConfirm(
                                selectedAddress,
                                selectedName
                            )
                        }
                    ) { Text("Add widget") }
                }
            }
        }
    }
}

@Composable
private fun DeviceOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
