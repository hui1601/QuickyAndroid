@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hui1601.quickyandroid.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hui1601.quickyandroid.ui.theme.tabular
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarTipFitScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val settings by deviceViewModel.settings.collectAsState()
    var isTesting by remember { mutableStateOf(false) }
    var localLeftResult by remember { mutableStateOf<Int?>(null) }
    var localRightResult by remember { mutableStateOf<Int?>(null) }

    // Sync from settings when test completes (status 0x02 = result available)
    if (settings.earTipFitStatus == 0x02) {
        localLeftResult = settings.earTipFitLeft
        localRightResult = settings.earTipFitRight
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Ear Tip Fit Test",
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                FitTestIcon(isTesting = isTesting)
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text(
                    text = if (isTesting) "Testing your fit..." else "Check your ear tip seal",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isTesting)
                        "Please stay still and keep both earbuds inserted."
                    else
                        "This test plays a short tone to measure how well your ear tips seal. A good seal improves sound quality and ANC performance.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (!isTesting && localLeftResult != null && localRightResult != null) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ResultCard(
                            label = "Left",
                            result = localLeftResult!!,
                            modifier = Modifier.weight(1f)
                        )
                        ResultCard(
                            label = "Right",
                            result = localRightResult!!,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                FilledTonalButton(
                    onClick = {
                        if (isTesting) {
                            isTesting = false
                            deviceViewModel.setEarTipFitTest(false)
                        } else {
                            isTesting = true
                            localLeftResult = null
                            localRightResult = null
                            deviceViewModel.setEarTipFitTest(true)
                            // Results will arrive via 0x11 notification
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(
                        text = if (isTesting) "Stop Test" else "Start Test",
                        style = MaterialTheme.typography.labelLargeEmphasized
                    )
                }
            }
        }
    }
}

@Composable
private fun FitTestIcon(isTesting: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isTesting) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = if (isTesting) 1f else 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Healing,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Fit test result card. The device returns a GRADE, not a percentage
 * (CompactnessActivityEarphone.smali:793-1013 + string resources):
 *   0 = Test Failed, 1 = Poor Fit, 2 = Poor Fit (suboptimal), 3 = Good Fit.
 */
@Composable
private fun ResultCard(label: String, result: Int, modifier: Modifier = Modifier) {
    val isGood = result == 3
    val verdict = when (result) {
        3 -> "Good Fit"
        2 -> "Poor Fit"
        1 -> "Poor Fit"
        else -> "Test Failed"
    }
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isGood)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isGood) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (isGood)
                    MaterialTheme.colorScheme.tertiary
                else
                    MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = verdict,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isGood)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = if (isGood) "Good seal — enjoy full sound quality" else "Try re-seating or changing ear tips",
                style = MaterialTheme.typography.bodySmall,
                color = if (isGood)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
