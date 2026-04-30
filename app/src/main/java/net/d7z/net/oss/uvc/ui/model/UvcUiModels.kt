package net.d7z.net.oss.uvc.ui.model

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Preview
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import net.d7z.net.oss.uvc.R

sealed interface Destination {
    data object Home : Destination
    data class Camera(val deviceName: String) : Destination
}

enum class CameraUiStatus(@param:StringRes val labelRes: Int) {
    Streaming(R.string.live_short),
    Ready(R.string.ready_short),
    Locked(R.string.locked_short),
    Discovered(R.string.discovered_short)
}

enum class BulkStreamAction {
    Starting,
    Stopping
}

data class CameraNavItem(
    val deviceName: String,
    val title: String,
    val subtitle: String,
    val cameraIndex: Int?,
    val status: CameraUiStatus
)

data class CameraDetailUi(
    val deviceName: String,
    val title: String,
    val subtitle: String,
    val cameraIndex: Int?,
    val status: CameraUiStatus,
    val isStreaming: Boolean,
    val isTransitioning: Boolean,
    val isStopping: Boolean,
    val isPreviewEnabled: Boolean,
    val resolutionOptions: List<String>,
    val selectedResolutionIndex: Int,
    val fpsOptions: List<String>,
    val selectedFpsIndex: Int,
    val technicalFacts: List<Pair<String, String>>
)

data class SummaryMetric(
    @param:StringRes val labelRes: Int,
    val value: String,
    val icon: ImageVector
)

data class MainUiState(
    val currentDestination: Destination = Destination.Home,
    val cameraNavItems: List<CameraNavItem> = emptyList(),
    val cameraDetail: CameraDetailUi? = null,
    val isCameraDetailLoading: Boolean = false,
    val previewBitmap: Bitmap? = null,
    val bandwidthText: String = "0.0 Mbps",
    val avgFpsText: String = "0.0",
    val httpUrlText: String = "http://0.0.0.0:8080",
    val streamStatusText: String = "STREAMS: NONE",
    val currentPortText: String = "8080",
    val connectedCount: Int = 0,
    val streamingCount: Int = 0,
    val bulkStreamAction: BulkStreamAction? = null,
    val logLines: List<String> = emptyList()
) {
    val summaryMetrics: List<SummaryMetric>
        get() = listOf(
            SummaryMetric(R.string.connected_cameras, connectedCount.toString(), Icons.Rounded.Cameraswitch),
            SummaryMetric(R.string.active_streams, streamingCount.toString(), Icons.Rounded.PlayCircle),
            SummaryMetric(R.string.average_fps, avgFpsText, Icons.Rounded.Preview),
            SummaryMetric(R.string.bandwidth_metric, bandwidthText, Icons.Rounded.Lan)
        )

    val previewTitle: String
        get() = cameraDetail?.title ?: ""

    val previewSubtitle: String
        get() = cameraDetail?.subtitle ?: ""
}

data class MainUiCallbacks(
    val onOpenDrawer: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onRefresh: () -> Unit,
    val onStartAllStreaming: () -> Unit,
    val onStopAllStreaming: () -> Unit,
    val onSelectHome: () -> Unit,
    val onSelectCamera: (String) -> Unit,
    val onApplyPort: () -> Unit,
    val onPortChanged: (String) -> Unit,
    val onTogglePreview: (Boolean) -> Unit,
    val onResolutionSelected: (Int) -> Unit,
    val onFpsSelected: (Int) -> Unit,
    val onToggleStreaming: () -> Unit,
    val onClearLogs: () -> Unit
)
