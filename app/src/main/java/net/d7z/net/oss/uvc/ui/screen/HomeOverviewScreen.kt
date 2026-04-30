package net.d7z.net.oss.uvc.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.d7z.net.oss.uvc.R
import net.d7z.net.oss.uvc.ui.component.EventLogPanel
import net.d7z.net.oss.uvc.ui.component.rememberStatusTone
import net.d7z.net.oss.uvc.ui.component.StatusChip
import net.d7z.net.oss.uvc.ui.component.SummaryCard
import net.d7z.net.oss.uvc.ui.model.BulkStreamAction
import net.d7z.net.oss.uvc.ui.model.MainUiCallbacks
import net.d7z.net.oss.uvc.ui.model.MainUiState

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeOverviewScreen(
    state: MainUiState,
    callbacks: MainUiCallbacks,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 2
        ) {
            state.summaryMetrics.forEach { metric ->
                SummaryCard(
                    modifier = Modifier
                        .widthIn(min = 220.dp)
                        .weight(1f),
                    label = stringResource(metric.labelRes),
                    value = metric.value,
                    icon = metric.icon
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.network_endpoint), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                Text(stringResource(R.string.network_endpoint_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(state.httpUrlText, fontFamily = FontFamily.Monospace) },
                    leadingIcon = { Icon(Icons.Rounded.Lan, contentDescription = null) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledLeadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.currentPortText,
                        onValueChange = callbacks.onPortChanged,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.http_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    FilledTonalButton(onClick = callbacks.onApplyPort, modifier = Modifier.height(56.dp)) {
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.connection_deck), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                Text(state.streamStatusText, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val startingAll = state.bulkStreamAction == BulkStreamAction.Starting
                    val stoppingAll = state.bulkStreamAction == BulkStreamAction.Stopping
                    FilledTonalButton(
                        onClick = callbacks.onStartAllStreaming,
                        modifier = Modifier.weight(1f),
                        enabled = state.connectedCount > state.streamingCount && state.bulkStreamAction == null
                    ) {
                        if (startingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.start_all_streams))
                        }
                    }
                    OutlinedButton(
                        onClick = callbacks.onStopAllStreaming,
                        modifier = Modifier.weight(1f),
                        enabled = state.streamingCount > 0 && state.bulkStreamAction == null
                    ) {
                        if (stoppingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.stop_all_streams))
                        }
                    }
                }
                if (state.cameraNavItems.isEmpty()) {
                    Text(stringResource(R.string.no_camera_sessions_active), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.cameraNavItems.forEach { item ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusTone = rememberStatusTone(item.status)
                                Text(item.title, fontFamily = FontFamily.Serif)
                                StatusChip(
                                    text = stringResource(item.status.labelRes),
                                    containerColor = statusTone.containerColor,
                                    contentColor = statusTone.contentColor
                                )
                            }
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        EventLogPanel(logLines = state.logLines, onClearLogs = callbacks.onClearLogs)
    }
}
