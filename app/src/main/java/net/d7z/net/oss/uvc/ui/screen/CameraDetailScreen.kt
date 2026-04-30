package net.d7z.net.oss.uvc.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import net.d7z.net.oss.uvc.R
import net.d7z.net.oss.uvc.ui.component.EventLogPanel
import net.d7z.net.oss.uvc.ui.component.rememberStatusTone
import net.d7z.net.oss.uvc.ui.component.StatusChip
import net.d7z.net.oss.uvc.ui.model.MainUiCallbacks
import net.d7z.net.oss.uvc.ui.model.MainUiState

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CameraDetailScreen(
    selectedName: String?,
    state: MainUiState,
    callbacks: MainUiCallbacks,
    modifier: Modifier = Modifier
) {
    val detail = state.cameraDetail
    if (detail == null || detail.deviceName != selectedName) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.camera_pending), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                Text(stringResource(R.string.camera_pending_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = callbacks.onSelectHome) { Text(stringResource(R.string.back_home)) }
            }
        }
        return
    }

    val resolutionMenuExpanded = remember { mutableStateOf(false) }
    val fpsMenuExpanded = remember { mutableStateOf(false) }
    val statusTone = rememberStatusTone(detail.status)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(detail.title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                        Text(detail.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusChip(
                        text = stringResource(detail.status.labelRes),
                        containerColor = statusTone.containerColor,
                        contentColor = statusTone.contentColor
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2
                ) {
                    ExposedDropdownMenuBox(
                        expanded = resolutionMenuExpanded.value,
                        onExpandedChange = {
                            if (!detail.isStreaming) {
                                resolutionMenuExpanded.value = !resolutionMenuExpanded.value
                            }
                        },
                        modifier = Modifier.widthIn(min = 220.dp)
                    ) {
                        OutlinedTextField(
                            value = detail.resolutionOptions.getOrNull(detail.selectedResolutionIndex).orEmpty(),
                            onValueChange = {},
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !detail.isStreaming)
                                .fillMaxWidth(),
                            label = { Text(stringResource(R.string.resolution_hint)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = resolutionMenuExpanded.value)
                            },
                            readOnly = true,
                            enabled = !detail.isStreaming
                        )
                        DropdownMenu(
                            expanded = resolutionMenuExpanded.value,
                            onDismissRequest = { resolutionMenuExpanded.value = false }
                        ) {
                            detail.resolutionOptions.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        callbacks.onResolutionSelected(index)
                                        resolutionMenuExpanded.value = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = fpsMenuExpanded.value,
                        onExpandedChange = {
                            if (!detail.isStreaming) {
                                fpsMenuExpanded.value = !fpsMenuExpanded.value
                            }
                        },
                        modifier = Modifier.widthIn(min = 160.dp)
                    ) {
                        OutlinedTextField(
                            value = detail.fpsOptions.getOrNull(detail.selectedFpsIndex).orEmpty(),
                            onValueChange = {},
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !detail.isStreaming)
                                .fillMaxWidth(),
                            label = { Text(stringResource(R.string.fps_hint)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = fpsMenuExpanded.value)
                            },
                            readOnly = true,
                            enabled = !detail.isStreaming
                        )
                        DropdownMenu(
                            expanded = fpsMenuExpanded.value,
                            onDismissRequest = { fpsMenuExpanded.value = false }
                        ) {
                            detail.fpsOptions.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        callbacks.onFpsSelected(index)
                                        fpsMenuExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = callbacks.onToggleStreaming,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (detail.isStreaming) stringResource(R.string.stop_stream) else stringResource(R.string.start_stream))
                    }
                }

                Text(
                    stringResource(R.string.preview_control_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.technical_profile), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                detail.technicalFacts.forEach { (label, value) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = value,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        EventLogPanel(logLines = state.logLines, onClearLogs = callbacks.onClearLogs)
    }
}
