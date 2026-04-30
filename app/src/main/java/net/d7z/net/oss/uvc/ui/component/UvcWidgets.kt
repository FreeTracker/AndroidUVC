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
import net.d7z.net.oss.uvc.ui.model.CameraNavItem

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
fun CameraStatusGlyph(isStreaming: Boolean, hasPermission: Boolean) {
    val statusColor = when {
        !hasPermission -> MaterialTheme.colorScheme.error
        isStreaming -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (isStreaming) Icons.Rounded.PlayCircle else Icons.Rounded.SettingsInputHdmi,
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
    title: String,
    subtitle: String,
    isStreaming: Boolean,
    previewEnabled: Boolean,
    showPreviewControl: Boolean,
    onTogglePreview: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val darkSurface = colorScheme.surface.luminance() < 0.5f
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
    val liveContainer = if (darkSurface) colorScheme.primaryContainer else Color(0xFFB8F6CA)
    val liveContent = if (darkSurface) colorScheme.onPrimaryContainer else Color(0xFF12301F)
    val idleContainer = if (darkSurface) colorScheme.secondaryContainer else Color(0xFFFDE68A)
    val idleContent = if (darkSurface) colorScheme.onSecondaryContainer else Color(0xFF3D2B00)
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
                                text = if (isStreaming) stringResource(R.string.live_stream) else stringResource(R.string.standby),
                                containerColor = if (isStreaming) liveContainer else idleContainer,
                                contentColor = if (isStreaming) liveContent else idleContent
                            )
                            StatusChip(
                                text = if (previewEnabled) stringResource(R.string.preview_armed) else stringResource(R.string.preview_off),
                                containerColor = previewContainer,
                                contentColor = previewContent
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, color = placeholderTitleColor, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                            Text(
                                subtitle,
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
                    Text(title, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                FilterChip(
                    selected = isStreaming,
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.padding(start = 12.dp),
                    label = { Text(if (isStreaming) stringResource(R.string.streaming) else stringResource(R.string.idle)) },
                    colors = FilterChipDefaults.filterChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
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
        icon = { CameraStatusGlyph(isStreaming = item.isStreaming, hasPermission = item.hasPermission) },
        badge = {
            if (item.cameraIndex != null) {
                Badge { Text(item.cameraIndex.toString()) }
            }
        }
    )
}
