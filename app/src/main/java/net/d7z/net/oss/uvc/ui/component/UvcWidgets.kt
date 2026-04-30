package net.d7z.net.oss.uvc.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.d7z.net.oss.uvc.R
import net.d7z.net.oss.uvc.ui.model.CameraUiStatus
import net.d7z.net.oss.uvc.ui.model.CameraNavItem

data class StatusTone(
    val containerColor: Color,
    val contentColor: Color
)

@Composable
fun rememberStatusTone(status: CameraUiStatus): StatusTone {
    val colorScheme = MaterialTheme.colorScheme
    return when (status) {
        CameraUiStatus.Streaming -> StatusTone(colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
        CameraUiStatus.Ready -> StatusTone(colorScheme.tertiaryContainer, colorScheme.onTertiaryContainer)
        CameraUiStatus.Locked -> StatusTone(colorScheme.errorContainer, colorScheme.onErrorContainer)
        CameraUiStatus.Discovered -> StatusTone(colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
    }
}

@Composable
fun DrawerMetricChip(label: String, value: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text("$label $value") },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            disabledLabelColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
fun CameraStatusGlyph(status: CameraUiStatus) {
    val statusColor = when (status) {
        CameraUiStatus.Locked -> MaterialTheme.colorScheme.error
        CameraUiStatus.Streaming -> MaterialTheme.colorScheme.primary
        CameraUiStatus.Ready -> MaterialTheme.colorScheme.tertiary
        CameraUiStatus.Discovered -> MaterialTheme.colorScheme.outline
    }
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (status == CameraUiStatus.Streaming) Icons.Rounded.PlayCircle else Icons.Rounded.SettingsInputHdmi,
            contentDescription = null
        )
        Canvas(modifier = Modifier.size(34.dp)) {
            drawCircle(
                color = statusColor,
                radius = 5.dp.toPx(),
                center = Offset(size.width - 6.dp.toPx(), 6.dp.toPx())
            )
        }
    }
}

@Composable
fun StatusChip(text: String, containerColor: Color, contentColor: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SummaryCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif)
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
fun PreviewHeroCard(
    bitmap: Bitmap?,
    cardTitle: String,
    cardSubtitle: String,
    heroTitle: String,
    heroSubtitle: String,
    status: CameraUiStatus,
    previewEnabled: Boolean,
    showPreviewControl: Boolean,
    onTogglePreview: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val darkSurface = colorScheme.surface.luminance() < 0.5f
    val statusTone = rememberStatusTone(status)
    val previewGradient = if (darkSurface) {
        listOf(
            colorScheme.surfaceVariant,
            colorScheme.primaryContainer,
            colorScheme.tertiaryContainer
        )
    } else {
        listOf(
            colorScheme.primaryContainer,
            colorScheme.tertiaryContainer,
            colorScheme.surfaceVariant
        )
    }
    val placeholderTitleColor = if (darkSurface) colorScheme.onSurface else colorScheme.onPrimaryContainer
    val placeholderBodyColor = placeholderTitleColor.copy(alpha = 0.78f)
    val previewContainer = if (darkSurface) colorScheme.tertiaryContainer else Color(0xFFE0F2FE)
    val previewContent = if (darkSurface) colorScheme.onTertiaryContainer else Color(0xFF0B395D)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 7.4f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = previewGradient
                        )
                    )
            ) {
                if (bitmap != null && previewEnabled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.preview_content_description),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            StatusChip(
                                text = stringResource(status.labelRes),
                                containerColor = statusTone.containerColor,
                                contentColor = statusTone.contentColor
                            )
                            StatusChip(
                                text = if (previewEnabled) stringResource(R.string.preview_armed) else stringResource(R.string.preview_off),
                                containerColor = previewContainer,
                                contentColor = previewContent
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(heroTitle, color = placeholderTitleColor, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                            Text(
                                heroSubtitle,
                                color = placeholderBodyColor,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }

                if (showPreviewControl) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(18.dp),
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.28f)
                    ) {
                        FilledIconButton(
                            onClick = onTogglePreview,
                            modifier = Modifier.size(64.dp),
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                containerColor = colorScheme.surface.copy(alpha = 0.92f),
                                contentColor = colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                imageVector = if (previewEnabled) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (previewEnabled) stringResource(R.string.disable_preview) else stringResource(R.string.enable_preview),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(cardTitle, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                    Text(cardSubtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                FilterChip(
                    selected = status == CameraUiStatus.Streaming,
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.padding(start = 12.dp),
                    label = { Text(stringResource(status.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = statusTone.containerColor,
                        disabledLabelColor = statusTone.contentColor
                    )
                )
            }
        }
    }
}

@Composable
fun EventLogPanel(logLines: List<String>, onClearLogs: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.event_stream), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                TextButton(onClick = onClearLogs) { Text(stringResource(R.string.clear)) }
            }
            LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logLines, key = { it }) { line ->
                    Text(line, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun DrawerCameraItem(
    item: CameraNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        modifier = Modifier.fillMaxWidth(),
        label = {
            Column(modifier = Modifier.widthIn(max = 220.dp)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        selected = selected,
        onClick = onClick,
        icon = { CameraStatusGlyph(status = item.status) },
        badge = {
            if (item.cameraIndex != null) {
                Badge { Text(item.cameraIndex.toString()) }
            }
        }
    )
}
