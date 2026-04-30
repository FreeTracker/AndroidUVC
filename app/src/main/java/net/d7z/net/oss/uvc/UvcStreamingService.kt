package net.d7z.net.oss.uvc

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class UvcStreamingService : Service() {
    private val logTag = "UVC_SERVICE"

    enum class SessionState {
        IDLE,
        STARTING,
        STREAMING,
        STOPPING,
        RELEASING
    }

    // --- 数据结构 ---
    inner class CameraSession(
        val index: Int,
        val fd: Int,
        val device: UsbDevice,
        val connection: UsbDeviceConnection
    ) {
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(FRAME_BUFFER_SIZE_BYTES)
        @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
        val frameLock = java.lang.Object()
        val frameSize = AtomicInteger(0)
        val frameCount = AtomicLong(0)
        val isDecoding = AtomicBoolean(false)
        val stateLock = Any()
        @Volatile var state: SessionState = SessionState.IDLE
        var supportedFormats: String = ""
        var selectedResPos: Int = 0
        var selectedFpsPos: Int = 0
        var isPreviewEnabled: Boolean = false
        var previewBuffer: ByteArray = ByteArray(0)
        val attachedAtMs: Long = SystemClock.elapsedRealtime()

        val isStreaming: Boolean
            get() = state == SessionState.STREAMING
    }

    // --- 成员变量 ---
    val sessionsByIndex = ConcurrentHashMap<Int, CameraSession>()
    val sessionsByFd = ConcurrentHashMap<Int, CameraSession>()
    private var server: MjpegServer? = null
    val totalSentBytes = AtomicLong(0)
    private val initializingDevices = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private var isForeground = false
    private val indexLock = Any()
    private val reservedIndices = mutableSetOf<Int>()

    private val serviceHandler = Handler(Looper.getMainLooper())
    private var lastSentBytes: Long = 0
    private var lastCheckTime = System.currentTimeMillis()
    private val lastServiceFrameCounts = mutableMapOf<Int, Long>()

    companion object {
        const val FRAME_BUFFER_SIZE_BYTES = 16 * 1024 * 1024
        private const val DEVICE_SETTLE_MS = 800L
        private const val FIRST_FRAME_TIMEOUT_MS = 1500L
        private const val MAX_START_ATTEMPTS = 3

        init {
            System.loadLibrary("native-lib")
        }
    }
    
    private val notificationUpdater = object : Runnable {
        override fun run() {
            if (isForeground) {
                updateStatsAndNotification()
                serviceHandler.postDelayed(this, 1000)
            }
        }
    }

    // 回调给 Activity 用于预览
    var onFrameUpdateListener: ((fd: Int, size: Int) -> Unit)? = null
    var onSessionCreatedListener: ((CameraSession) -> Unit)? = null
    var onSessionsChangedListener: (() -> Unit)? = null
    var onLogMessage: ((String) -> Unit)? = null

    private fun log(msg: String) {
        Log.i(logTag, msg)
        onLogMessage?.invoke(msg)
    }

    private fun allocateCameraIndex(): Int {
        synchronized(indexLock) {
            var candidate = 0
            while (candidate in reservedIndices || sessionsByIndex.containsKey(candidate)) {
                candidate++
            }
            reservedIndices.add(candidate)
            return candidate
        }
    }

    private fun releaseCameraIndex(index: Int) {
        synchronized(indexLock) {
            reservedIndices.remove(index)
        }
    }

    private fun describeUsbDevice(device: UsbDevice): String {
        val interfaceSummary = buildString {
            append("[")
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (i > 0) append("; ")
                append(
                    "if#${intf.id}" +
                        "/cls=${intf.interfaceClass}" +
                        "/sub=${intf.interfaceSubclass}" +
                        "/proto=${intf.interfaceProtocol}" +
                        "/ep=${intf.endpointCount}"
                )
            }
            append("]")
        }
        return "name=${device.deviceName}, product=${device.productName}, manufacturer=${device.manufacturerName}, " +
            "vid=0x${device.vendorId.toString(16)}, pid=0x${device.productId.toString(16)}, " +
            "class=${device.deviceClass}, subclass=${device.deviceSubclass}, proto=${device.deviceProtocol}, " +
            "interfaces=$interfaceSummary"
    }

    // --- Service 生命周期 ---
    inner class LocalBinder : Binder() {
        fun getService(): UvcStreamingService = this@UvcStreamingService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        attachServiceCallback()
        val prefs = getSharedPreferences("uvc_prefs", Context.MODE_PRIVATE)
        val port = prefs.getInt("http_port", 8080)
        startHttpServer(port)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        sessionsByFd.keys.toList().forEach { releaseCamera(it) }
        server?.stop()
        detachServiceCallback()
        serviceHandler.removeCallbacks(notificationUpdater)
        super.onDestroy()
    }

    private fun startHttpServer(port: Int) {
        log("Starting HTTP server on port $port")
        server = MjpegServer(port)
        try {
            server?.start()
        } catch (e: Exception) {
            log("HTTP Server Error: ${e.message}")
            e.printStackTrace()
        }
    }

    fun updateHttpPort(port: Int) {
        if (server?.listeningPort == port) return
        log("Restarting HTTP server on new port: $port")
        server?.stop()
        startHttpServer(port)
    }

    fun stopIfIdle() {
        log("Checking for idle resources before activity exit...")
        // Release all cameras that are NOT streaming
        sessionsByFd.values.filter { !it.isStreaming }.forEach { session ->
            log("Auto-releasing idle Cam ${session.index}")
            releaseCamera(session.fd)
        }
        
        // If no cameras are left (meaning no one was streaming), stop the service
        if (sessionsByFd.isEmpty()) {
            log("No active streams found. Stopping service.")
            stopAllAndExit()
        } else {
            log("Active streams remain (${sessionsByFd.size}). Keeping service alive in background.")
        }
    }

    private fun stopAllAndExit() {
        sessionsByFd.keys.toList().forEach { releaseCamera(it) }
        server?.stop()
        if (isForeground) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            isForeground = false
        }
        stopSelf()
        // 发送广播通知 Activity 退出（可选）
        sendBroadcast(Intent("net.d7z.net.oss.uvc.EXIT"))
    }

    // --- Real-time Stats ---
    var currentBandwidthMbps: Double = 0.0
        private set
    var currentAvgFps: Double = 0.0
        private set

    private fun updateStatsAndNotificationAsync() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateStatsAndNotification()
        } else {
            serviceHandler.post { updateStatsAndNotification() }
        }
    }

    private fun updateStatsAndNotification() {
        val streamingSessions = sessionsByFd.values.filter { it.isStreaming }
        val activeCount = streamingSessions.size

        if (activeCount == 0 && sessionsByFd.isEmpty()) {
            // No cameras at all, just stop foreground if it was running
            if (isForeground) {
                stopForegroundIfRunning()
                isForeground = false
                serviceHandler.removeCallbacks(notificationUpdater)
            }
            currentBandwidthMbps = 0.0
            currentAvgFps = 0.0
            return
        }

        val now = System.currentTimeMillis()
        val deltaSec = (now - lastCheckTime) / 1000.0
        val currentBytes = totalSentBytes.get()
        val diffBytes = currentBytes - lastSentBytes
        lastSentBytes = currentBytes
        lastCheckTime = now
        currentBandwidthMbps = if (deltaSec > 0) (diffBytes * 8.0 / 1000000.0 / deltaSec) else 0.0

        var totalFpsDiff = 0L
        streamingSessions.forEach { session ->
            val current = session.frameCount.get()
            val last = lastServiceFrameCounts[session.fd] ?: current
            totalFpsDiff += (current - last)
            lastServiceFrameCounts[session.fd] = current
        }
        currentAvgFps = if (activeCount > 0 && deltaSec > 0) (totalFpsDiff / deltaSec) / activeCount else 0.0

        val statusText = if (activeCount > 0) {
            String.format("Streaming: %d | Avg FPS: %.1f | BW: %.2f Mbps", activeCount, currentAvgFps, currentBandwidthMbps)
        } else {
            "UVC Hub: Idle (No active streams)"
        }

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "uvc_channel")
            .setContentTitle("UVC Hub Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        try {
            if (!isForeground) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    }
                    startForeground(1, notification, type)
                } else {
                    startForeground(1, notification)
                }
                isForeground = true
                serviceHandler.postDelayed(notificationUpdater, 1000)
            } else {
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(1, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            log("Foreground service error: ${e.message}")
        }
    }

    private fun stopForegroundIfRunning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("uvc_channel", "UVC Streaming", NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // --- 对外接口 ---
    fun initCamera(device: UsbDevice, connection: UsbDeviceConnection) {
        if (!initializingDevices.add(device.deviceName)) {
            connection.close()
            return
        }
        log("USB device opening: ${describeUsbDevice(device)}")
        Thread {
            try {
                createSession(device, connection)
            } finally {
                initializingDevices.remove(device.deviceName)
            }
        }.start()
    }

    fun startStreaming(fd: Int, width: Int, height: Int, fps: Int, onComplete: (() -> Unit)? = null) {
        Thread {
            val session = sessionsByFd[fd] ?: return@Thread
            val shouldStart = synchronized(session.stateLock) {
                if (session.state != SessionState.IDLE) {
                    false
                } else {
                    session.state = SessionState.STARTING
                    true
                }
            }

            if (!shouldStart) {
                onComplete?.invoke()
                return@Thread
            }

            onSessionsChangedListener?.invoke()

            val (activeSession, started) = startStreamingWithRecovery(session, width, height, fps)

            synchronized(activeSession.stateLock) {
                if (activeSession.state == SessionState.STARTING) {
                    activeSession.state = if (started) SessionState.STREAMING else SessionState.IDLE
                }
            }

            if (activeSession !== session) {
                synchronized(session.stateLock) {
                    if (session.state == SessionState.STARTING) {
                        session.state = SessionState.IDLE
                    }
                }
            }

            updateStatsAndNotificationAsync()
            if (started) {
                log("Cam ${activeSession.index} Streaming Started.")
            } else {
                log("FAIL: Cam ${activeSession.index} failed to start streaming.")
            }
            onSessionsChangedListener?.invoke()
            onComplete?.invoke()
        }.start()
    }

    private fun startStreamingWithRecovery(
        initialSession: CameraSession,
        width: Int,
        height: Int,
        fps: Int
    ): Pair<CameraSession, Boolean> {
        var activeSession = initialSession

        repeat(MAX_START_ATTEMPTS) { attempt ->
            waitForDeviceSettle(activeSession)

            log(
                "Cam ${activeSession.index} start requested: ${width}x$height@$fps, " +
                    "bufferCap=${activeSession.buffer.capacity()}, attempt=${attempt + 1}/$MAX_START_ATTEMPTS"
            )

            val started = startUVC(activeSession.fd, activeSession.buffer, width, height, fps)
            if (started) {
                if (awaitFirstFrame(activeSession, FIRST_FRAME_TIMEOUT_MS)) {
                    return activeSession to true
                }

                log("FAIL: Cam ${activeSession.index} start returned success but no frame arrived within ${FIRST_FRAME_TIMEOUT_MS}ms.")
                stopUVC(activeSession.fd)
            }

            if (attempt == MAX_START_ATTEMPTS - 1) {
                return activeSession to false
            }

            val recoveredSession = recoverSessionForStreaming(activeSession) ?: return activeSession to false
            activeSession = recoveredSession
        }

        return activeSession to false
    }

    private fun waitForDeviceSettle(session: CameraSession) {
        val remaining = session.attachedAtMs + DEVICE_SETTLE_MS - SystemClock.elapsedRealtime()
        if (remaining > 0) {
            log("Cam ${session.index} waiting ${remaining}ms for USB settle.")
            Thread.sleep(remaining)
        }
    }

    private fun awaitFirstFrame(session: CameraSession, timeoutMs: Long): Boolean {
        if (session.frameCount.get() > 0) {
            return true
        }

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        synchronized(session.frameLock) {
            while (session.frameCount.get() <= 0) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    return false
                }
                try {
                    session.frameLock.wait(remaining)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return true
    }

    fun stopStreaming(fd: Int, onComplete: (() -> Unit)? = null) {
        Thread {
            val session = sessionsByFd[fd] ?: return@Thread
            val shouldStop = synchronized(session.stateLock) {
                when (session.state) {
                    SessionState.STREAMING, SessionState.STARTING -> {
                        session.state = SessionState.STOPPING
                        true
                    }
                    else -> false
                }
            }

            if (!shouldStop) {
                onComplete?.invoke()
                return@Thread
            }

            onSessionsChangedListener?.invoke()

            stopUVC(fd)
            synchronized(session.stateLock) {
                if (session.state == SessionState.STOPPING) {
                    session.state = SessionState.IDLE
                }
            }
            updateStatsAndNotificationAsync()
            log("Cam ${session.index} stopped.")
            onSessionsChangedListener?.invoke()
            onComplete?.invoke()
        }.start()
    }

    fun releaseCamera(fd: Int) {
        val session = sessionsByFd[fd] ?: return
        val shouldRelease = synchronized(session.stateLock) {
            if (session.state == SessionState.RELEASING) {
                false
            } else {
                session.state = SessionState.RELEASING
                true
            }
        }
        if (!shouldRelease) return

        releaseSession(session, releaseIndex = true, notify = true)
    }

    private fun createSession(
        device: UsbDevice,
        connection: UsbDeviceConnection,
        fixedIndex: Int? = null,
        selectedResPos: Int = 0,
        selectedFpsPos: Int = 0
    ): CameraSession? {
        val fd = connection.fileDescriptor
        log("USB connection opened: device=${device.deviceName}, fd=$fd, rawDescriptors=${connection.rawDescriptors?.size ?: 0}")
        val formatStr = getDeviceSupportedFormats(fd)
        if (formatStr.isEmpty()) {
            connection.close()
            log("FAIL: Could not load formats for ${device.productName}.")
            return null
        }

        val index = fixedIndex ?: allocateCameraIndex()
        val session = CameraSession(index, fd, device, connection).apply {
            supportedFormats = formatStr
            this.selectedResPos = selectedResPos
            this.selectedFpsPos = selectedFpsPos
        }
        sessionsByFd[fd] = session
        sessionsByIndex[index] = session
        updateStatsAndNotificationAsync()
        log("Cam $index assigned and ready: ${device.productName}. URL: /camera/$index")
        log("Cam $index formats: $formatStr")
        onSessionCreatedListener?.invoke(session)
        onSessionsChangedListener?.invoke()
        return session
    }

    private fun recoverSessionForStreaming(session: CameraSession): CameraSession? {
        log("Cam ${session.index} start failed, rebuilding native session for ${session.device.deviceName}")
        releaseSession(session, releaseIndex = false, notify = false)

        val reopenedConnection = (getSystemService(Context.USB_SERVICE) as UsbManager).openDevice(session.device)
        if (reopenedConnection == null) {
            log("FAIL: Cam ${session.index} could not reopen USB connection after start failure.")
            releaseCameraIndex(session.index)
            return null
        }

        val replacement = createSession(
            device = session.device,
            connection = reopenedConnection,
            fixedIndex = session.index,
            selectedResPos = session.selectedResPos,
            selectedFpsPos = session.selectedFpsPos
        )

        if (replacement == null) {
            releaseCameraIndex(session.index)
            return null
        }

        synchronized(replacement.stateLock) {
            replacement.state = SessionState.STARTING
        }
        return replacement
    }

    private fun releaseSession(session: CameraSession, releaseIndex: Boolean, notify: Boolean) {
        log("Cam ${session.index} releasing: state=${session.state}, fd=${session.fd}, device=${session.device.deviceName}")
        releaseUVC(session.fd)
        sessionsByFd.remove(session.fd)
        if (sessionsByIndex[session.index]?.fd == session.fd) {
            sessionsByIndex.remove(session.index)
        }
        if (releaseIndex) {
            releaseCameraIndex(session.index)
        }
        lastServiceFrameCounts.remove(session.fd)
        session.connection.close()
        updateStatsAndNotificationAsync()
        log("Detached Cam ${session.index}")
        if (notify) {
            onSessionsChangedListener?.invoke()
        }
    }

    // Native 回调
    fun onFrameReady(fd: Int, size: Int) {
        val session = sessionsByFd[fd] ?: return
        if (size <= 0 || size > session.buffer.capacity()) {
            log("Drop frame for Cam ${session.index}: invalid size=$size cap=${session.buffer.capacity()}")
            return
        }
        session.frameCount.incrementAndGet()
        synchronized(session.frameLock) {
            session.frameSize.set(size)
            session.frameLock.notifyAll()
        }
        onFrameUpdateListener?.invoke(fd, size)
    }

    // --- HTTP 服务器 ---
    private inner class MjpegServer(port: Int) : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri
            if (uri == "/" || uri == "/index.html") {
                val sb = StringBuilder()
                sb.append("<html><head>")
                sb.append("<title>UVC Camera - Hub</title>")
                sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
                sb.append("<style>")
                sb.append("body { font-family: system-ui, -apple-system, sans-serif; line-height: 1.5; max-width: 800px; margin: 0 auto; padding: 2rem; background: #f4f4f9; color: #333; }")
                sb.append("h1 { color: #1a73e8; border-bottom: 2px solid #1a73e8; padding-bottom: 0.5rem; }")
                sb.append(".card { background: white; padding: 1.5rem; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-top: 1.5rem; }")
                sb.append("ul { list-style: none; padding: 0; }")
                sb.append("li { margin: 1rem 0; padding: 1rem; background: #e8f0fe; border-radius: 6px; display: flex; justify-content: space-between; align-items: center; }")
                sb.append("a { color: #1a73e8; text-decoration: none; font-weight: bold; background: white; padding: 0.5rem 1rem; border-radius: 4px; transition: background 0.2s; }")
                sb.append("a:hover { background: #f1f3f4; }")
                sb.append(".status { font-size: 0.8rem; color: #666; }")
                sb.append("</style>")
                sb.append("</head><body>")
                sb.append("<h1>UVC Camera Hub</h1>")
                sb.append("<p>Welcome to the UVC Camera Distributed Streaming Hub. This device is currently sharing USB camera streams over the network.</p>")
                
                sb.append("<div class='card'>")
                sb.append("<h2>Active Streams</h2>")
                val sortedIndices = sessionsByIndex.keys().toList().sorted()
                if (sortedIndices.isEmpty()) {
                    sb.append("<p>No cameras connected.</p>")
                } else {
                    sb.append("<ul>")
                    sortedIndices.forEach { idx ->
                        val cam = sessionsByIndex[idx]!!
                        val status = if (cam.isStreaming) "STREAMING" else "IDLE"
                        sb.append("<li>")
                        sb.append("<div><strong>Cam $idx</strong><br><span class='status'>${cam.device.productName} ($status)</span></div>")
                        if (cam.isStreaming) {
                            sb.append("<a href='/camera/$idx' target='_blank'>Watch Stream</a>")
                        } else {
                            sb.append("<span class='status'>Start in App</span>")
                        }
                        sb.append("</li>")
                    }
                    sb.append("</ul>")
                }
                sb.append("</div>")
                
                sb.append("<div class='card'>")
                sb.append("<h2>Project Info</h2>")
                sb.append("<p>This project allows any Android device with USB Host support to become a multi-camera streaming server for UVC-compliant devices (Webcams, Capture Cards, etc.).</p>")
                sb.append("</div>")
                
                sb.append("</body></html>")
                return newFixedLengthResponse(Response.Status.OK, NanoHTTPD.MIME_HTML, sb.toString())
            }

            if (uri.startsWith("/camera/")) {
                try {
                    val index = uri.substringAfter("/camera/").toInt()
                    val camSession = sessionsByIndex[index]
                    if (camSession != null && camSession.isStreaming) {
                        return newFixedLengthResponse(Response.Status.OK, "multipart/x-mixed-replace; boundary=--boundary", MjpegInputStream(camSession), -1)
                    }
                } catch (e: Exception) {}
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Camera not found or not streaming")
        }
    }

    private inner class MjpegInputStream(val session: CameraSession) : InputStream() {
        private var frameSnapshot: ByteArray? = null
        private var headerBytes: ByteArray? = null
        private var headerPointer = 0
        private var dataPointer = 0
        private var isFirst = true
        private var isClosed = false
        private var lastReadFrameId = -1L

        override fun read(): Int {
            val b = ByteArray(1)
            return if (read(b, 0, 1) <= 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (isClosed) return -1
            
            // Check if we need a new frame
            if (frameSnapshot == null || (headerBytes == null && dataPointer >= frameSnapshot!!.size)) {
                synchronized(session.frameLock) {
                    while (session.frameCount.get() <= lastReadFrameId) {
                        try {
                            session.frameLock.wait(500)
                        } catch (e: Exception) {
                            return -1
                        }
                        if (isClosed || !session.isStreaming) return -1
                    }
                    
                    val size = session.frameSize.get()
                    if (frameSnapshot == null || frameSnapshot!!.size != size) {
                        frameSnapshot = ByteArray(size)
                    }
                    
                    // Synchronized copy from native buffer
                    lockBuffer(session.fd)
                    try {
                        session.buffer.rewind()
                        session.buffer.get(frameSnapshot!!)
                    } finally {
                        unlockBuffer(session.fd)
                    }
                    
                    lastReadFrameId = session.frameCount.get()
                    
                    val header = "${if(isFirst){isFirst=false;""}else{"\r\n"}}--boundary\r\nContent-Type: image/jpeg\r\nContent-Length: $size\r\n\r\n"
                    headerBytes = header.toByteArray()
                    headerPointer = 0
                    dataPointer = 0
                }
            }

            var totalRead = 0
            if (headerBytes != null) {
                val avail = headerBytes!!.size - headerPointer
                val toRead = Math.min(len, avail)
                System.arraycopy(headerBytes!!, headerPointer, b, off, toRead)
                headerPointer += toRead
                totalRead += toRead
                if (headerPointer >= headerBytes!!.size) {
                    headerBytes = null
                }
            }

            if (totalRead < len && frameSnapshot != null) {
                val avail = frameSnapshot!!.size - dataPointer
                val toRead = Math.min(len - totalRead, avail)
                System.arraycopy(frameSnapshot!!, dataPointer, b, off + totalRead, toRead)
                dataPointer += toRead
                totalRead += toRead
                totalSentBytes.addAndGet(toRead.toLong())
            }

            return if (totalRead == 0) -1 else totalRead
        }

        override fun close() {
            super.close()
            isClosed = true
            synchronized(session.frameLock) {
                session.frameLock.notifyAll()
            }
        }
    }

    external fun lockBuffer(fd: Int)
    external fun unlockBuffer(fd: Int)
    external fun attachServiceCallback()
    external fun detachServiceCallback()
    external fun getDeviceSupportedFormats(fd: Int): String
    external fun startUVC(fd: Int, buffer: ByteBuffer, width: Int, height: Int, fps: Int): Boolean
    external fun stopUVC(fd: Int)
    external fun releaseUVC(fd: Int)
}
