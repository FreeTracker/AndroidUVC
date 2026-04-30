package net.d7z.net.oss.uvc.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.d7z.net.oss.uvc.R
import net.d7z.net.oss.uvc.ui.component.DrawerCameraItem
import net.d7z.net.oss.uvc.ui.component.DrawerMetricChip
import net.d7z.net.oss.uvc.ui.component.PreviewHeroCard
import net.d7z.net.oss.uvc.ui.model.CameraUiStatus
import net.d7z.net.oss.uvc.ui.model.Destination
import net.d7z.net.oss.uvc.ui.model.MainUiCallbacks
import net.d7z.net.oss.uvc.ui.model.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    callbacks: MainUiCallbacks
) {
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val wideLayout = configuration.screenWidthDp >= 900
    val selectedName = (state.currentDestination as? Destination.Camera)?.deviceName
    val onHome = state.currentDestination == Destination.Home
    val cameraDetail = state.cameraDetail

    val heroTitle = when {
        cameraDetail == null -> ""
        !cameraDetail.isStreaming -> stringResource(R.string.preview_state_stream_stopped)
        !cameraDetail.isPreviewEnabled -> stringResource(R.string.preview_state_disabled)
        state.previewBitmap == null -> stringResource(R.string.preview_state_waiting)
        else -> cameraDetail.title
    }
    val heroSubtitle = when {
        cameraDetail == null -> ""
        !cameraDetail.isStreaming -> stringResource(R.string.preview_state_stream_stopped_body)
        !cameraDetail.isPreviewEnabled -> stringResource(R.string.preview_state_disabled_body)
        state.previewBitmap == null -> stringResource(R.string.preview_state_waiting_body)
        else -> cameraDetail.subtitle
    }
    val previewStatus = when {
        cameraDetail == null -> CameraUiStatus.Discovered
        cameraDetail.isStreaming -> CameraUiStatus.Streaming
        else -> cameraDetail.status
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .padding(end = 24.dp)
                    .widthIn(max = 360.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                DrawerPanel(
                    state = state,
                    onHomeSelected = {
                        callbacks.onSelectHome()
                        scope.launch { drawerState.close() }
                    },
                    onCameraSelected = { deviceName ->
                        callbacks.onSelectCamera(deviceName)
                        scope.launch { drawerState.close() }
                    },
                    onAboutSelected = {
                        callbacks.onOpenAbout()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        },
        gesturesEnabled = !wideLayout
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = {
                            callbacks.onOpenDrawer()
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Rounded.Menu, contentDescription = stringResource(R.string.open_cameras))
                        }
                    },
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.app_title_short), fontFamily = FontFamily.Serif)
                            Text(
                                text = if (state.currentDestination == Destination.Home) {
                                    stringResource(R.string.home_status_overview)
                                } else {
                                    state.previewSubtitle
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = callbacks.onRefresh) {
                            Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!onHome) {
                    PreviewHeroCard(
                        bitmap = state.previewBitmap,
                        cardTitle = state.previewTitle,
                        cardSubtitle = state.previewSubtitle,
                        heroTitle = heroTitle,
                        heroSubtitle = heroSubtitle,
                        status = previewStatus,
                        previewEnabled = state.cameraDetail?.isPreviewEnabled == true,
                        showPreviewControl = true,
                        onTogglePreview = {
                            state.cameraDetail?.let { callbacks.onTogglePreview(!it.isPreviewEnabled) }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (state.currentDestination) {
                        Destination.Home -> HomeOverviewScreen(
                            state = state,
                            callbacks = callbacks,
                            modifier = Modifier.fillMaxSize()
                        )
                        is Destination.Camera -> CameraDetailScreen(
                            selectedName = selectedName,
                            state = state,
                            callbacks = callbacks,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (state.isCameraDetailLoading && state.currentDestination is Destination.Camera) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerPanel(
    state: MainUiState,
    onHomeSelected: () -> Unit,
    onCameraSelected: (String) -> Unit,
    onAboutSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.control_deck), style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Serif)
                Text(
                    stringResource(R.string.drawer_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawerMetricChip(stringResource(R.string.connected_short), state.connectedCount.toString())
                    DrawerMetricChip(stringResource(R.string.live_short), state.streamingCount.toString())
                }
            }
        }

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.home)) },
            selected = state.currentDestination == Destination.Home,
            onClick = onHomeSelected,
            icon = { Icon(Icons.Rounded.Dashboard, contentDescription = null) },
            badge = {
                if (state.connectedCount > 0) {
                    Badge { Text(state.connectedCount.toString()) }
                }
            }
        )

        Text(
            stringResource(R.string.connected_cameras_section),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 6.dp)
        )

        if (state.cameraNavItems.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Text(
                    stringResource(R.string.no_uvc_cameras_detected),
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.cameraNavItems, key = { it.deviceName }) { item ->
                    DrawerCameraItem(
                        item = item,
                        selected = state.currentDestination == Destination.Camera(item.deviceName),
                        onClick = { onCameraSelected(item.deviceName) }
                    )
                }
            }
        }

        HorizontalDivider()

        NavigationDrawerItem(
            label = { Text(stringResource(R.string.about)) },
            selected = false,
            onClick = onAboutSelected,
            icon = { Icon(Icons.Rounded.Info, contentDescription = null) }
        )
    }
}
