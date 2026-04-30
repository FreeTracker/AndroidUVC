package net.d7z.net.oss.uvc

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.d7z.net.oss.uvc.ui.model.CameraDetailUi
import net.d7z.net.oss.uvc.ui.model.CameraNavItem
import net.d7z.net.oss.uvc.ui.model.CameraUiStatus
import net.d7z.net.oss.uvc.ui.model.BulkStreamAction
import net.d7z.net.oss.uvc.ui.model.Destination
import net.d7z.net.oss.uvc.ui.model.MainUiCallbacks
import net.d7z.net.oss.uvc.ui.model.MainUiState
import net.d7z.net.oss.uvc.ui.screen.MainScreen
import net.d7z.net.oss.uvc.ui.theme.UvcComposeTheme
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val actionUsbPermission = "net.d7z.net.oss.uvc.USB_PERMISSION"

    private var uvcService: UvcStreamingService? = null
    private var isBound = false
    private var selectedDeviceName: String? = null
    private var uiState by mutableStateOf(MainUiState())

    private val mainHandler = Handler(Looper.getMainLooper())
    private val decodeExecutor = Executors.newSingleThreadExecutor()
    private val detailExecutor = Executors.newSingleThreadExecutor()
    private val detailRequestToken = AtomicInteger(0)
    private val requestingPermissions = mutableSetOf<String>()
    private val permissionCooldowns = mutableMapOf<String, Long>()
    private val cooldownMs = 10_000L
    private val logLines = mutableListOf<String>()
    private var bulkStreamAction: BulkStreamAction? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as UvcStreamingService.LocalBinder
            uvcService = binder.getService()
            isBound = true
            setupServiceListeners()
            mainHandler.postDelayed({ refreshDeviceList() }, 300)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            uvcService = null
            detailRequestToken.incrementAndGet()
            updateUiSnapshot()
        }
    }

    private val uiUpdater = object : Runnable {
        override fun run() {
            updateUiSnapshot()
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val callbacks = MainUiCallbacks(
        onOpenDrawer = {},
        onOpenAbout = { startActivity(Intent(this, AboutActivity::class.java)) },
        onRefresh = { refreshDeviceList() },
        onStartAllStreaming = { startAllStreaming() },
        onStopAllStreaming = { stopAllStreaming() },
        onSelectHome = { selectHome() },
        onSelectCamera = { selectCamera(it) },
        onApplyPort = { applyHttpPort() },
        onPortChanged = { value ->
            uiState = uiState.copy(currentPortText = value.filter(Char::isDigit).take(5))
        },
        onTogglePreview = { enabled -> setPreviewEnabled(enabled) },
        onResolutionSelected = { index -> updateResolutionSelection(index) },
        onFpsSelected = { index -> updateFpsSelection(index) },
        onToggleStreaming = { toggleStreaming() },
        onClearLogs = {
            logLines.clear()
            updateUiSnapshot()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("uvc_prefs", Context.MODE_PRIVATE)
        uiState = uiState.copy(currentPortText = prefs.getInt("http_port", 8080).toString())

        setContent {
            UvcComposeTheme {
                MainScreen(state = uiState, callbacks = callbacks)
            }
        }

        val intent = Intent(this, UvcStreamingService::class.java)
        try {
            startService(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to start service", e)
        }
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        registerReceivers()
        mainHandler.post(uiUpdater)
        handleIntent(intent)
    }

    private fun setupServiceListeners() {
        uvcService?.onLogMessage = { msg -> logToUI(msg) }
        uvcService?.onSessionCreatedListener = { session ->
            runOnUiThread {
                if (selectedDeviceName == session.device.deviceName &&
                    uiState.currentDestination is Destination.Camera
                ) {
                    uiState = uiState.copy(currentDestination = Destination.Camera(session.device.deviceName))
                    requestCameraDetailRefresh(session.device.deviceName, showLoading = false)
                }
                updateUiSnapshot()
            }
        }
        uvcService?.onSessionsChangedListener = {
            runOnUiThread {
                updateUiSnapshot()
                (uiState.currentDestination as? Destination.Camera)?.deviceName?.let { deviceName ->
                    if (!uiState.isCameraDetailLoading) {
                        requestCameraDetailRefresh(deviceName)
                    }
                }
            }
        }
        uvcService?.onFrameUpdateListener = { fd, size ->
            val session = uvcService?.sessionsByFd?.get(fd)
            val previewTarget = selectedDeviceName
            val showingCameraPage = uiState.currentDestination is Destination.Camera
            if (
                session != null &&
                showingCameraPage &&
                session.device.deviceName == previewTarget &&
                session.isPreviewEnabled
            ) {
                if (session.isDecoding.compareAndSet(false, true)) {
                    val data = synchronized(session.stateLock) {
                        if (session.previewBuffer.size != size) {
                            session.previewBuffer = ByteArray(size)
                        }
                        session.previewBuffer
                    }
                    uvcService?.lockBuffer(fd)
                    try {
                        session.buffer.rewind()
                        session.buffer.get(data, 0, size)
                    } finally {
                        uvcService?.unlockBuffer(fd)
                    }
                    if (!decodeExecutor.isShutdown) {
                        decodeExecutor.execute {
                            try {
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeByteArray(data, 0, size, options)
                                options.inSampleSize = calculateInSampleSize(options, 1280, 720)
                                options.inJustDecodeBounds = false
                                val bitmap = BitmapFactory.decodeByteArray(data, 0, size, options)
                                runOnUiThread {
                                    if (
                                        uiState.currentDestination is Destination.Camera &&
                                        selectedDeviceName == session.device.deviceName &&
                                        session.isPreviewEnabled
                                    ) {
                                        uiState = uiState.copy(previewBitmap = bitmap)
                                    }
                                }
                            } catch (_: Exception) {
                            } finally {
                                session.isDecoding.set(false)
                            }
                        }
                    } else {
                        session.isDecoding.set(false)
                    }
                }
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun logToUI(msg: String) {
        Log.d("UVC_APP", msg)
        runOnUiThread {
            logLines.add(0, "${System.currentTimeMillis() % 100000}: $msg")
            while (logLines.size > 120) {
                logLines.removeLast()
            }
            updateUiSnapshot()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun updateUiSnapshot() {
        val service = uvcService
        val devices = getUvcDevices()
        val connectedCount = devices.size
        val currentPortText = uiState.currentPortText
        val currentDestination = uiState.currentDestination
        val currentBulkAction = bulkStreamAction

        if (service == null) {
            uiState = uiState.copy(
                currentDestination = Destination.Home,
                cameraNavItems = emptyList(),
                cameraDetail = null,
                isCameraDetailLoading = false,
                previewBitmap = null,
                bandwidthText = "0.0 Mbps",
                avgFpsText = "0.0",
                httpUrlText = "http://0.0.0.0:${currentPortText.toIntOrNull() ?: 8080}",
                streamStatusText = "STREAMS: NONE",
                connectedCount = connectedCount,
                streamingCount = 0,
                bulkStreamAction = currentBulkAction,
                logLines = logLines.toList()
            )
            return
        }

        val streamingCount = service.sessionsByFd.values.count { it.isStreaming }
        val bandwidthText = getString(R.string.bandwidth_label, service.currentBandwidthMbps)
        val avgFpsText = getString(R.string.avg_fps_label, service.currentAvgFps)
        val httpUrlText = getString(R.string.web_url_label, getLocalIpAddress() ?: "0.0.0.0", currentPortText.toIntOrNull() ?: 8080)
        val streamStatusText = buildString {
            append(getString(R.string.streams_prefix))
            val sortedIndices = service.sessionsByIndex.keys().toList().sorted()
            if (sortedIndices.isEmpty()) {
                append(getString(R.string.none))
            } else {
                sortedIndices.forEach { idx ->
                    val session = service.sessionsByIndex[idx] ?: return@forEach
                    val status = if (session.isStreaming) getString(R.string.streaming_status) else getString(R.string.ready_short)
                    append("#$idx[$status] ")
                }
            }
        }

        val navItems = devices.map { device ->
            val session = service.sessionsByFd.values.find { it.device.deviceName == device.deviceName }
            CameraNavItem(
                deviceName = device.deviceName,
                title = session?.let { getString(R.string.camera_title_format, it.index) } ?: (device.productName ?: getString(R.string.camera_generic)),
                subtitle = buildString {
                    append(device.productName ?: device.deviceName.takeLast(8))
                    append(" • ")
                    append(
                        getString(
                            when {
                                session?.isStreaming == true -> CameraUiStatus.Streaming.labelRes
                                session != null -> CameraUiStatus.Ready.labelRes
                                !(getSystemService(Context.USB_SERVICE) as UsbManager).hasPermission(device) -> CameraUiStatus.Locked.labelRes
                                else -> CameraUiStatus.Discovered.labelRes
                            }
                        )
                    )
                },
                cameraIndex = session?.index,
                status = when {
                    session?.isStreaming == true -> CameraUiStatus.Streaming
                    session != null -> CameraUiStatus.Ready
                    !(getSystemService(Context.USB_SERVICE) as UsbManager).hasPermission(device) -> CameraUiStatus.Locked
                    else -> CameraUiStatus.Discovered
                }
            )
        }.sortedWith(compareBy<CameraNavItem> { it.cameraIndex ?: Int.MAX_VALUE }.thenBy { it.title })

        val selectedName = selectedDeviceName
        var destination = currentDestination
        var previewBitmap: Bitmap? = if (currentDestination == Destination.Home) null else uiState.previewBitmap

        if (selectedName != null && devices.none { it.deviceName == selectedName }) {
            selectedDeviceName = null
            previewBitmap = null
            destination = Destination.Home
        }
        val cameraDestination = destination as? Destination.Camera
        val cameraDetail = if (cameraDestination != null && uiState.cameraDetail?.deviceName == cameraDestination.deviceName) {
            uiState.cameraDetail
        } else {
            null
        }

        if (destination == Destination.Home) {
            previewBitmap = null
        }

        uiState = uiState.copy(
            currentDestination = destination,
            cameraNavItems = navItems,
            cameraDetail = cameraDetail,
            isCameraDetailLoading = if (cameraDestination != null) uiState.isCameraDetailLoading else false,
            previewBitmap = previewBitmap,
            bandwidthText = bandwidthText,
            avgFpsText = avgFpsText,
            httpUrlText = httpUrlText,
            streamStatusText = streamStatusText,
            connectedCount = connectedCount,
            streamingCount = streamingCount,
            bulkStreamAction = currentBulkAction,
            logLines = logLines.toList()
        )

        if (cameraDestination != null && !uiState.isCameraDetailLoading) {
            requestCameraDetailRefresh(cameraDestination.deviceName)
        }
    }

    private fun buildCameraDetail(service: UvcStreamingService, deviceName: String): CameraDetailUi? {
        val session = service.sessionsByFd.values.find { it.device.deviceName == deviceName } ?: return null
        if (!session.isStreaming && session.isPreviewEnabled) {
            session.isPreviewEnabled = false
        }
        val resolutionMap = parseResolutionMap(session.supportedFormats)
        val resolutions = resolutionMap.keys.toList()
        val selectedResolutionIndex = session.selectedResPos.coerceIn(0, (resolutions.size - 1).coerceAtLeast(0))
        if (session.selectedResPos != selectedResolutionIndex) {
            session.selectedResPos = selectedResolutionIndex
            session.selectedFpsPos = 0
        }
        val fpsOptions = if (resolutions.isNotEmpty()) resolutionMap[resolutions[selectedResolutionIndex]].orEmpty() else emptyList()
        val selectedFpsIndex = session.selectedFpsPos.coerceIn(0, (fpsOptions.size - 1).coerceAtLeast(0))
        if (session.selectedFpsPos != selectedFpsIndex) {
            session.selectedFpsPos = selectedFpsIndex
        }
        val port = uiState.currentPortText.toIntOrNull() ?: 8080
        val host = getLocalIpAddress() ?: "0.0.0.0"
        val streamUrl = "http://$host:$port/camera/${session.index}"
        return CameraDetailUi(
            deviceName = deviceName,
            title = getString(R.string.camera_title_format, session.index),
            subtitle = session.device.productName ?: session.device.deviceName,
            cameraIndex = session.index,
            status = if (session.isStreaming) CameraUiStatus.Streaming else CameraUiStatus.Ready,
            isStreaming = session.isStreaming,
            isTransitioning = session.state == UvcStreamingService.SessionState.STARTING || session.state == UvcStreamingService.SessionState.STOPPING,
            isStopping = session.state == UvcStreamingService.SessionState.STOPPING,
            isPreviewEnabled = session.isPreviewEnabled,
            resolutionOptions = resolutions,
            selectedResolutionIndex = selectedResolutionIndex,
            fpsOptions = fpsOptions,
            selectedFpsIndex = selectedFpsIndex,
            technicalFacts = listOf(
                getString(R.string.http_stream_url) to streamUrl,
                getString(R.string.usb_path) to session.device.deviceName,
                getString(R.string.vendor_product) to "0x${session.device.vendorId.toString(16)} / 0x${session.device.productId.toString(16)}",
                getString(R.string.fd_label) to session.fd.toString(),
                getString(R.string.preview_buffer) to "${session.buffer.capacity() / (1024 * 1024)} MB"
            )
        )
    }

    private fun parseResolutionMap(formatString: String): LinkedHashMap<String, List<String>> {
        val resolutionMap = linkedMapOf<String, List<String>>()
        formatString.split(";").forEach { part ->
            if (part.contains(":")) {
                val segments = part.split(":")
                if (segments.size == 2) {
                    resolutionMap[segments[0]] = segments[1].split(",").filter { it.isNotBlank() }
                }
            }
        }
        return resolutionMap
    }

    private fun requestCameraDetailRefresh(deviceName: String, showLoading: Boolean = false) {
        val service = uvcService ?: return
        val requestId = detailRequestToken.incrementAndGet()
        val shouldShowLoading = showLoading || uiState.cameraDetail?.deviceName != deviceName

        if (shouldShowLoading) {
            uiState = uiState.copy(isCameraDetailLoading = true)
        }

        detailExecutor.execute {
            val detail = buildCameraDetail(service, deviceName)
            runOnUiThread {
                if (detailRequestToken.get() != requestId || selectedDeviceName != deviceName) return@runOnUiThread

                if (detail == null) {
                    resetSelectionToHome()
                    updateUiSnapshot()
                    return@runOnUiThread
                }

                uiState = uiState.copy(
                    cameraDetail = detail,
                    isCameraDetailLoading = false
                )
            }
        }
    }

    private fun refreshDeviceList() {
        if (uvcService == null) return
        val devices = getUvcDevices()
        if (devices.isEmpty()) {
            resetSelectionToHome()
            updateUiSnapshot()
            return
        }
        processConnectedDevices(devices)
        if (selectedDeviceName != null && devices.none { it.deviceName == selectedDeviceName }) {
            resetSelectionToHome()
        }
        updateUiSnapshot()
    }

    private fun processConnectedDevices(devices: List<UsbDevice>) {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        devices.forEach { device ->
            if (manager.hasPermission(device)) {
                if (uvcService?.sessionsByFd?.values?.none { it.device.deviceName == device.deviceName } == true) {
                    logToUI("Opening device: ${device.deviceName}")
                    manager.openDevice(device)?.let { uvcService?.initCamera(device, it) }
                        ?: logToUI("FAIL: Failed to open connection for ${device.deviceName}")
                }
            } else {
                requestPermission(device)
            }
        }
        updateUiSnapshot()
    }

    private fun openDevice(device: UsbDevice) {
        selectedDeviceName = device.deviceName
        uiState = uiState.copy(
            currentDestination = Destination.Camera(device.deviceName),
            cameraDetail = uiState.cameraDetail?.takeIf { it.deviceName == device.deviceName },
            isCameraDetailLoading = true,
            previewBitmap = null
        )
        logToUI("Selected device: ${device.deviceName}")
        val session = uvcService?.sessionsByFd?.values?.find { it.device.deviceName == device.deviceName }
        if (session != null) {
            requestCameraDetailRefresh(device.deviceName, showLoading = false)
            updateUiSnapshot()
            return
        }
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (manager.hasPermission(device)) {
            manager.openDevice(device)?.let { uvcService?.initCamera(device, it) }
        } else {
            requestPermission(device)
        }
        updateUiSnapshot()
    }

    private fun selectHome() {
        detailRequestToken.incrementAndGet()
        resetSelectionToHome()
        updateUiSnapshot()
    }

    private fun selectCamera(deviceName: String) {
        getUvcDevices().find { it.deviceName == deviceName }?.let { openDevice(it) }
    }

    private fun setPreviewEnabled(enabled: Boolean) {
        val deviceName = selectedDeviceName ?: return
        val session = uvcService?.sessionsByFd?.values?.find { it.device.deviceName == deviceName } ?: return
        if (enabled && !session.isStreaming) {
            showToast(getString(R.string.preview_requires_streaming))
            return
        }
        session.isPreviewEnabled = enabled
        if (!enabled) {
            uiState = uiState.copy(previewBitmap = null)
        }
        updateUiSnapshot()
    }

    private fun updateResolutionSelection(index: Int) {
        val deviceName = selectedDeviceName ?: return
        val session = uvcService?.sessionsByFd?.values?.find { it.device.deviceName == deviceName } ?: return
        if (session.isStreaming) {
            showToast(getString(R.string.stop_stream_before_changing_settings))
            return
        }
        session.selectedResPos = index
        session.selectedFpsPos = 0
        updateUiSnapshot()
    }

    private fun updateFpsSelection(index: Int) {
        val deviceName = selectedDeviceName ?: return
        val session = uvcService?.sessionsByFd?.values?.find { it.device.deviceName == deviceName } ?: return
        if (session.isStreaming) {
            showToast(getString(R.string.stop_stream_before_changing_settings))
            return
        }
        session.selectedFpsPos = index
        updateUiSnapshot()
    }

    private fun toggleStreaming() {
        val service = uvcService ?: run {
            logToUI(getString(R.string.service_not_ready))
            return
        }
        val deviceName = selectedDeviceName ?: run {
            logToUI(getString(R.string.no_device_selected))
            return
        }
        val session = service.sessionsByFd.values.find { it.device.deviceName == deviceName } ?: run {
            logToUI(getString(R.string.camera_initializing_wait))
            return
        }

        val resolutionMap = parseResolutionMap(session.supportedFormats)
        val resolutions = resolutionMap.keys.toList()
        val selectedResolutionIndex = session.selectedResPos.coerceIn(0, (resolutions.size - 1).coerceAtLeast(0))
        if (session.selectedResPos != selectedResolutionIndex) {
            session.selectedResPos = selectedResolutionIndex
            session.selectedFpsPos = 0
        }
        val selectedResolution = resolutions.getOrNull(selectedResolutionIndex)
        val fpsOptions = selectedResolution?.let { resolutionMap[it].orEmpty() }.orEmpty()
        val selectedFpsIndex = session.selectedFpsPos.coerceIn(0, (fpsOptions.size - 1).coerceAtLeast(0))
        if (session.selectedFpsPos != selectedFpsIndex) {
            session.selectedFpsPos = selectedFpsIndex
        }
        val selectedFps = fpsOptions.getOrNull(selectedFpsIndex)

        if (session.isStreaming) {
            session.isPreviewEnabled = false
            service.stopStreaming(session.fd) {
                runOnUiThread {
                    uiState = uiState.copy(previewBitmap = null)
                    updateUiSnapshot()
                }
            }
            return
        }

        if (selectedResolution.isNullOrBlank() || selectedFps.isNullOrBlank()) {
            logToUI(getString(R.string.error_selection_incomplete))
            return
        }

        val wh = selectedResolution.split("x")
        val width = wh.getOrNull(0)?.toIntOrNull() ?: 640
        val height = wh.getOrNull(1)?.toIntOrNull() ?: 480
        val fps = selectedFps.toIntOrNull() ?: 30
        service.startStreaming(session.fd, width, height, fps) {
            runOnUiThread { updateUiSnapshot() }
        }
    }

    private fun startAllStreaming() {
        if (bulkStreamAction != null) return
        bulkStreamAction = BulkStreamAction.Starting
        updateUiSnapshot()
        refreshDeviceList()
        mainHandler.postDelayed({
            val service = uvcService ?: return@postDelayed
            val targetSessions = service.sessionsByFd.values
                .sortedBy { it.index }
                .filter { !it.isStreaming }
                .mapNotNull { session ->
                    val resolutionMap = parseResolutionMap(session.supportedFormats)
                    val resolutions = resolutionMap.keys.toList()
                    val selectedResolutionIndex = session.selectedResPos.coerceIn(0, (resolutions.size - 1).coerceAtLeast(0))
                    if (session.selectedResPos != selectedResolutionIndex) {
                        session.selectedResPos = selectedResolutionIndex
                        session.selectedFpsPos = 0
                    }
                    val selectedResolution = resolutions.getOrNull(selectedResolutionIndex) ?: return@mapNotNull null
                    val fpsOptions = resolutionMap[selectedResolution].orEmpty()
                    val selectedFpsIndex = session.selectedFpsPos.coerceIn(0, (fpsOptions.size - 1).coerceAtLeast(0))
                    if (session.selectedFpsPos != selectedFpsIndex) {
                        session.selectedFpsPos = selectedFpsIndex
                    }
                    val selectedFps = fpsOptions.getOrNull(selectedFpsIndex)?.toIntOrNull() ?: return@mapNotNull null
                    val wh = selectedResolution.split("x")
                    val width = wh.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val height = wh.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                    Triple(session, width to height, selectedFps)
                }
            if (targetSessions.isEmpty()) {
                bulkStreamAction = null
                updateUiSnapshot()
                return@postDelayed
            }
            var remaining = targetSessions.size
            targetSessions.forEach { (session, size, selectedFps) ->
                service.startStreaming(session.fd, size.first, size.second, selectedFps) {
                    runOnUiThread { updateUiSnapshot() }
                    remaining -= 1
                    if (remaining == 0) {
                        runOnUiThread {
                            bulkStreamAction = null
                            updateUiSnapshot()
                        }
                    }
                }
            }
        }, 450L)
    }

    private fun stopAllStreaming() {
        if (bulkStreamAction != null) return
        val service = uvcService ?: return
        val targetSessions = service.sessionsByFd.values
            .sortedBy { it.index }
            .filter { it.isStreaming }
        if (targetSessions.isEmpty()) return
        bulkStreamAction = BulkStreamAction.Stopping
        updateUiSnapshot()
        var remaining = targetSessions.size
        targetSessions.forEach { session ->
                session.isPreviewEnabled = false
                service.stopStreaming(session.fd) {
                    runOnUiThread {
                        if (selectedDeviceName == session.device.deviceName) {
                            uiState = uiState.copy(previewBitmap = null)
                        }
                        remaining -= 1
                        if (remaining == 0) {
                            bulkStreamAction = null
                        }
                        updateUiSnapshot()
                    }
                }
        }
    }

    private fun applyHttpPort() {
        val port = uiState.currentPortText.toIntOrNull() ?: 8080
        if (port !in 1..65535) {
            showToast(getString(R.string.invalid_port))
            return
        }
        getSharedPreferences("uvc_prefs", Context.MODE_PRIVATE).edit().putInt("http_port", port).apply()
        uvcService?.updateHttpPort(port)
        updateUiSnapshot()
    }

    private fun requestPermission(device: UsbDevice) {
        val now = System.currentTimeMillis()
        val lastDenied = permissionCooldowns[device.deviceName] ?: 0L
        if (now - lastDenied < cooldownMs || requestingPermissions.contains(device.deviceName)) return
        requestingPermissions.add(device.deviceName)
        logToUI("Requesting permission for ${device.deviceName}")
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val intent = Intent(actionUsbPermission).setPackage(packageName)
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val permissionIntent = PendingIntent.getBroadcast(this, device.deviceId, intent, flags)
        manager.requestPermission(device, permissionIntent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) refreshDeviceList()
    }

    private val usbDynamicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (intent.action == actionUsbPermission) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                logToUI("Permission Result: ${device?.deviceName} granted=$granted")
                device?.let {
                    requestingPermissions.remove(it.deviceName)
                    if (!granted) permissionCooldowns[it.deviceName] = System.currentTimeMillis()
                }
                if (granted) refreshDeviceList()
            }
        }
    }

    private val usbSystemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    device?.let { detached ->
                        requestingPermissions.remove(detached.deviceName)
                        permissionCooldowns.remove(detached.deviceName)
                        uvcService?.sessionsByFd?.values?.find { it.device.deviceName == detached.deviceName }?.let {
                            uvcService?.releaseCamera(it.fd)
                        }
                        if (selectedDeviceName == detached.deviceName) {
                            resetSelectionToHome()
                        }
                    }
                    refreshDeviceList()
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    uiState = uiState.copy(previewBitmap = null)
                    refreshDeviceList()
                }
                "net.d7z.net.oss.uvc.EXIT" -> finish()
            }
        }
    }

    private fun resetSelectionToHome() {
        detailRequestToken.incrementAndGet()
        selectedDeviceName = null
        uiState = uiState.copy(
            currentDestination = Destination.Home,
            cameraDetail = null,
            isCameraDetailLoading = false,
            previewBitmap = null
        )
    }

    private fun registerReceivers() {
        val systemFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction("net.d7z.net.oss.uvc.EXIT")
        }
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(usbSystemReceiver, systemFilter, RECEIVER_EXPORTED)
            registerReceiver(usbDynamicReceiver, IntentFilter(actionUsbPermission), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbSystemReceiver, systemFilter)
            registerReceiver(usbDynamicReceiver, IntentFilter(actionUsbPermission))
        }
    }

    private fun getUvcDevices(): List<UsbDevice> {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values.filter {
            it.deviceClass == 239 || (0 until it.interfaceCount).any { i -> it.getInterface(i).interfaceClass == 14 }
        }
    }

    private fun getLocalIpAddress(): String? = try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        Collections.list(interfaces).forEach { networkInterface ->
            Collections.list(networkInterface.inetAddresses).forEach { address ->
                if (!address.isLoopbackAddress && address is Inet4Address) {
                    return address.hostAddress
                }
            }
        }
        null
    } catch (_: Exception) {
        null
    }

    override fun onDestroy() {
        uvcService?.onFrameUpdateListener = null
        uvcService?.onSessionCreatedListener = null
        uvcService?.onSessionsChangedListener = null
        uvcService?.onLogMessage = null
        if (isBound) {
            uvcService?.stopIfIdle()
            unbindService(serviceConnection)
        }
        mainHandler.removeCallbacks(uiUpdater)
        decodeExecutor.shutdown()
        detailExecutor.shutdown()
        try {
            unregisterReceiver(usbSystemReceiver)
            unregisterReceiver(usbDynamicReceiver)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
