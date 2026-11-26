@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.hui1601.quickyandroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hui1601.quickyandroid.data.parser.AutoEqParseResult
import com.hui1601.quickyandroid.data.parser.AutoEqProfile
import com.hui1601.quickyandroid.data.parser.AutoEqProfileParser
import com.hui1601.quickyandroid.data.repository.AutoEqEntry
import com.hui1601.quickyandroid.data.repository.AutoEqRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AutoEqImportMode(val label: String) {
    BROWSE("Browse"),
    PASTE("Paste")
}

/**
 * Entry point for AutoEQ profiles: searches the public AutoEQ result index or
 * accepts pasted ParametricEQ text / a profile URL. The profile is handed to
 * the editor for review; nothing is written to the device here.
 */
@Composable
internal fun AutoEqImportSheet(
    onDismiss: () -> Unit,
    onImported: (name: String, profile: AutoEqProfile) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(AutoEqImportMode.BROWSE) }
    var query by rememberSaveable { mutableStateOf("") }
    var pastedText by rememberSaveable { mutableStateOf("") }

    var entries by remember { mutableStateOf<List<AutoEqEntry>?>(null) }
    var indexError by remember { mutableStateOf<String?>(null) }
    var loadAttempt by remember { mutableIntStateOf(0) }
    var importError by remember { mutableStateOf<String?>(null) }
    var loadingProfile by remember { mutableStateOf(false) }

    LaunchedEffect(loadAttempt) {
        if (entries != null) return@LaunchedEffect
        indexError = null
        try {
            entries = AutoEqRepository.index()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            indexError = e.message ?: "Could not load the AutoEQ index."
        }
    }

    fun importProfileText(name: String, text: String) {
        when (val result = AutoEqProfileParser.parse(text)) {
            is AutoEqParseResult.Success -> onImported(name, result.profile)
            is AutoEqParseResult.Failure -> importError = result.message
        }
    }

    fun downloadAndImport(name: String, fetchText: suspend () -> String) {
        importError = null
        loadingProfile = true
        scope.launch {
            try {
                importProfileText(name, fetchText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importError = e.message ?: "AutoEQ download failed."
            } finally {
                loadingProfile = false
            }
        }
    }

    val filtered = remember(entries, query) {
        val current = entries.orEmpty()
        val needle = query.trim()
        if (needle.isEmpty()) {
            emptyList()
        } else {
            current.filter { it.name.contains(needle, ignoreCase = true) }.take(50)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Import from AutoEQ",
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                    Text(
                        text = "Measured headphone corrections from the AutoEQ database.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ButtonGroup(modifier = Modifier.fillMaxWidth()) {
                AutoEqImportMode.entries.forEach { item ->
                    toggleableItem(
                        checked = mode == item,
                        label = item.label,
                        onCheckedChange = { checked ->
                            if (checked) {
                                mode = item
                                importError = null
                            }
                        },
                        weight = 1f
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (mode) {
                AutoEqImportMode.BROWSE -> BrowseMode(
                    query = query,
                    onQueryChange = {
                        query = it
                        importError = null
                    },
                    entries = entries,
                    indexError = indexError,
                    importError = importError,
                    loadingProfile = loadingProfile,
                    filtered = filtered,
                    onRetry = { loadAttempt++ },
                    onSelect = { entry ->
                        downloadAndImport(entry.name) { AutoEqRepository.profileText(entry) }
                    }
                )

                AutoEqImportMode.PASTE -> PasteMode(
                    text = pastedText,
                    onTextChange = {
                        pastedText = it
                        importError = null
                    },
                    importError = importError,
                    loadingProfile = loadingProfile,
                    onImport = {
                        val text = pastedText.trim()
                        if (text.startsWith("http://") || text.startsWith("https://")) {
                            downloadAndImport("AutoEQ profile") {
                                AutoEqRepository.profileTextFromUrl(text)
                            }
                        } else {
                            importProfileText("Pasted AutoEQ profile", text)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BrowseMode(
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<AutoEqEntry>?,
    indexError: String?,
    importError: String?,
    loadingProfile: Boolean,
    filtered: List<AutoEqEntry>,
    onRetry: () -> Unit,
    onSelect: (AutoEqEntry) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Headphone model") },
        placeholder = { Text("e.g. Sony WF-1000XM4") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    when {
        loadingProfile -> LoadingRow("Downloading profile…")
        entries == null && indexError == null -> LoadingRow("Loading AutoEQ index…")
        indexError != null -> {
            Text(
                text = indexError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
        query.isBlank() -> Text(
            text = "Search 6,000+ measured profiles, then import one into the " +
                "parametric editor. Nothing is sent to the earbuds until you tap Apply.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        filtered.isEmpty() -> Text(
            text = "No profile matches \"${query.trim()}\".",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
        ) {
            items(filtered) { entry ->
                ListItem(
                    headlineContent = {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                    },
                    supportingContent = {
                        Text(entry.source, style = MaterialTheme.typography.bodySmall)
                    },
                    trailingContent = {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = "Import ${entry.name}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.clickable { onSelect(entry) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    importError?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun PasteMode(
    text: String,
    onTextChange: (String) -> Unit,
    importError: String?,
    loadingProfile: Boolean,
    onImport: () -> Unit
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("ParametricEQ text or profile URL") },
        placeholder = {
            Text(
                "Preamp: -6.1 dB\nFilter 1: ON LSC Fc 105 Hz Gain 6.4 dB Q 0.70",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        },
        minLines = 5,
        maxLines = 10
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Paste the contents of an AutoEQ * ParametricEQ.txt file, or a " +
            "GitHub/raw profile URL. High-shelf filters become broad peaking bands " +
            "because the firmware has no high-shelf type.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    importError?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onImport,
            enabled = text.isNotBlank() && !loadingProfile
        ) {
            Text("Import profile")
        }
        if (loadingProfile) {
            LoadingIndicator(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun LoadingRow(label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        LoadingIndicator(modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
