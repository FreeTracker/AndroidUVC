package net.d7z.net.oss.uvc.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.d7z.net.oss.uvc.R
import net.d7z.net.oss.uvc.ui.model.StartGateUiState

@Composable
fun StartGateScreen(
    state: StartGateUiState,
    onContinue: () -> Unit,
    onDismissUsbPermissionDialog: () -> Unit,
    onConfirmUsbPermissionDialog: () -> Unit
) {
    if (state.showUsbPermissionDialog) {
        AlertDialog(
            onDismissRequest = onDismissUsbPermissionDialog,
            title = { Text(stringResource(R.string.usb_permission_dialog_title)) },
            text = { Text(stringResource(R.string.usb_permission_dialog_message)) },
            confirmButton = {
                TextButton(onClick = onConfirmUsbPermissionDialog) {
                    Text(stringResource(R.string.continue_action))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUsbPermissionDialog) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.app_title_short),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.start_gate_description),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PermissionStatusCard(
                icon = Icons.Rounded.Videocam,
                title = stringResource(R.string.runtime_permissions_title),
                description = if (state.runtimePermissionsGranted) {
                    stringResource(R.string.permissions_ready)
                } else {
                    stringResource(R.string.runtime_permissions_needed)
                },
                ready = state.runtimePermissionsGranted
            )

            PermissionStatusCard(
                icon = Icons.Rounded.Usb,
                title = stringResource(R.string.usb_permissions_title),
                description = when {
                    state.usbPermissionsGranted -> stringResource(R.string.permissions_ready)
                    state.requestingUsb -> stringResource(R.string.usb_permission_requesting)
                    else -> stringResource(R.string.usb_permissions_needed)
                },
                ready = state.usbPermissionsGranted
            )

            FilledTonalButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = when {
                        state.runtimePermissionsGranted && state.usbPermissionsGranted -> stringResource(R.string.open_dashboard)
                        state.runtimePermissionsGranted -> stringResource(R.string.grant_usb_access)
                        else -> stringResource(R.string.grant_permissions)
                    }
                )
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    ready: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (ready) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            stringResource(R.string.ready_status),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
