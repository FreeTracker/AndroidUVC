package net.d7z.net.oss.uvc

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.d7z.net.oss.uvc.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val ACTION_USB_PERMISSION = "net.d7z.net.oss.uvc.USB_PERMISSION"

    private var uvcService: UvcStreamingService? = null
    private var isBound = false
    private var currentDevice: UsbDevice? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val decodeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val requestingPermissions = mutableSetOf<String>()
    
    private val permissionCooldowns = mutableMapOf<String, Long>()
    private val COOLDOWN_MS = 10000L 

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as UvcStreamingService.LocalBinder
            uvcService = binder.getService()
            isBound = true
            setupServiceListeners()
            mainHandler.postDelayed({ refreshDeviceList() }, 500)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            uvcService = null
        }
    }

    private var lastSentBytes: Long = 0
    private var lastCheckTime = System.currentTimeMillis()

    private val uiUpdater = object : Runnable {
        override fun run() {
            updateStatsUI()
            mainHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        initUI()
        
        val intent = Intent(this, UvcStreamingService::class.java)
        try {
            startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)

        registerReceivers()
        mainHandler.post(uiUpdater)
        handleIntent(getIntent())
    }

    override fun onStart() {
        super.onStart()
        Log.d("MainActivity", "onStart")
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume")
    }

    private fun setupServiceListeners() {
        uvcService?.onLogMessage = { msg -> logToUI(msg) }
        uvcService?.onSessionCreatedListener = { session ->
            if (session.device.deviceName == currentDevice?.deviceName) {
                runOnUiThread { restoreCameraUI(session) }
            }
        }
        uvcService?.onFrameUpdateListener = { fd, size ->
            val session = uvcService?.sessionsByFd?.get(fd)
            if (session != null && session.device.deviceName == currentDevice?.deviceName && session.isPreviewEnabled) {
                if (session.isDecoding.compareAndSet(false, true)) {
                    val data = ByteArray(size)
                    uvcService?.lockBuffer(fd)
                    try {
                        session.buffer.rewind()
                        session.buffer.get(data)
                    } finally {
                        uvcService?.unlockBuffer(fd)
                    }
                    if (!decodeExecutor.isShutdown) {
                        try {
                            decodeExecutor.execute {
                                try {
                                    val options = BitmapFactory.Options()
                                    options.inJustDecodeBounds = true
                                    BitmapFactory.decodeByteArray(data, 0, size, options)
                                    
                                    val targetW = binding.ivPreview.width.takeIf { it > 0 } ?: 640
                                    val targetH = binding.ivPreview.height.takeIf { it > 0 } ?: 480
                                    options.inSampleSize = calculateInSampleSize(options, targetW, targetH)
                                    options.inJustDecodeBounds = false
                                    
                                    val bitmap = BitmapFactory.decodeByteArray(data, 0, size, options)
                                    bitmap?.let { runOnUiThread { binding.ivPreview.setImageBitmap(it) } }
                                } catch (e: Exception) {}
                                finally { session.isDecoding.set(false) }
                            }
                        } catch (e: Exception) {
                            session.isDecoding.set(false)
                        }
                    } else {
                        session.isDecoding.set(false)
                    }
                }
            }
        }
    }

    private fun updateStatsUI() {
        val service = uvcService ?: return
        
        val mbps = service.currentBandwidthMbps
        val avgFps = service.currentAvgFps
        
        binding.tvBandwidth.text = getString(R.string.bandwidth_label, mbps)
        binding.tvTotalFps.text = getString(R.string.avg_fps_label, avgFps)
        
        val ip = getLocalIpAddress() ?: "0.0.0.0"
        val port = binding.etHttpPort.text.toString().toIntOrNull() ?: 8080
        binding.tvHttpUrl.text = getString(R.string.web_url_label, ip, port)

        val status = StringBuilder(getString(R.string.streams_prefix))
        val sortedIndices = service.sessionsByIndex.keys().toList().sorted()
        if (sortedIndices.isEmpty()) {
            status.append(getString(R.string.none))
        } else {
            sortedIndices.forEach { idx ->
                val s = service.sessionsByIndex[idx]!!
                val statusStr = if(s.isStreaming) getString(R.string.streaming_status) else getString(R.string.idle_status)
                status.append("#$idx[$statusStr] ")
            }
        }
        binding.tvServerStatus.text = status.toString()
    }

    private fun logToUI(msg: String) {
        Log.d("UVC_APP", msg)
        runOnUiThread {
            binding.tvLog.append("${System.currentTimeMillis() % 100000}: $msg\n")
            binding.svLog.post { binding.svLog.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun initUI() {
        binding.btnRefresh.setOnClickListener { refreshDeviceList() }
        binding.btnStreamToggle.setOnClickListener { toggleStreaming() }
        binding.actvDevices.setOnItemClickListener { _, _, pos, _ ->
            val devices = getUvcDevices()
            if (pos < devices.size) openDevice(devices[pos])
        }
        binding.swPreview.setOnCheckedChangeListener { _, isChecked ->
            val dev = currentDevice ?: return@setOnCheckedChangeListener
            uvcService?.sessionsByFd?.values?.find { it.device.deviceName == dev.deviceName }?.let { session ->
                session.isPreviewEnabled = isChecked
                updatePreviewVisibility(session)
            }
        }
        
        val prefs = getSharedPreferences("uvc_prefs", Context.MODE_PRIVATE)
        val savedPort = prefs.getInt("http_port", 8080)
        binding.etHttpPort.setText(savedPort.toString())
        
        binding.etHttpPort.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val newPort = binding.etHttpPort.text.toString().toIntOrNull() ?: 8080
                prefs.edit().putInt("http_port", newPort).apply()
                uvcService?.updateHttpPort(newPort)
            }
        }
    }

    private fun updatePreviewVisibility(session: UvcStreamingService.CameraSession) {
        if (session.isPreviewEnabled) {
            binding.ivPreview.visibility = View.VISIBLE
            binding.tvPreviewOffHint.visibility = View.GONE
        } else {
            binding.ivPreview.visibility = View.INVISIBLE
            binding.ivPreview.setImageResource(0)
            binding.tvPreviewOffHint.visibility = View.VISIBLE
        }
    }

    private fun refreshDeviceList() {
        if (uvcService == null) return
        val devices = getUvcDevices()
        val deviceNames = devices.map { device ->
            val session = uvcService?.sessionsByFd?.values?.find { it.device.deviceName == device.deviceName }
            val shortPath = device.deviceName.takeLast(7)
            if (session != null) {
                getString(R.string.usb_status_cam_index, session.index, (device.productName ?: "Camera")) + " [$shortPath]"
            } else {
                (device.productName ?: "Camera") + " [$shortPath]"
            }
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, if(deviceNames.isEmpty()) listOf(getString(R.string.no_uvc_device)) else deviceNames)
        binding.actvDevices.setAdapter(adapter)
        
        if (devices.isEmpty()) {
            currentDevice = null
            binding.tilResolution.visibility = View.GONE
            binding.tilFps.visibility = View.GONE
            binding.swPreview.visibility = View.GONE
            binding.btnStreamToggle.visibility = View.GONE
            binding.tvCameraId.visibility = View.GONE
            binding.tvUsbStatus.text = getString(R.string.usb_status_not_found)
            binding.ivPreview.setImageResource(0)
            binding.tvPreviewLabel.text = getString(R.string.preview_label)
            binding.btnStreamToggle.isEnabled = false
            binding.btnStreamToggle.text = getString(R.string.start_streaming)
            binding.tvPreviewOffHint.visibility = View.GONE
        } else {
            processConnectedDevices(devices)
        }
    }

    private fun processConnectedDevices(devices: List<UsbDevice>) {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        devices.forEach { device ->
            val hasPerm = manager.hasPermission(device)
            Log.d("MainActivity", "Checking device: ${device.deviceName}, hasPermission: $hasPerm")
            if (hasPerm) {
                if (uvcService?.sessionsByFd?.values?.none { it.device.deviceName == device.deviceName } == true) {
                    logToUI("Opening device: ${device.deviceName}")
                    val conn = manager.openDevice(device)
                    if (conn != null) {
                        uvcService?.initCamera(device, conn)
                    } else {
                        logToUI("FAIL: Failed to open connection for ${device.deviceName}")
                    }
                }
            } else {
                requestPermission(device)
            }
        }
    }

    private fun openDevice(device: UsbDevice) {
        currentDevice = device
        logToUI("User selected device: ${device.deviceName}")
        val session = uvcService?.sessionsByFd?.values?.find { it.device.deviceName == device.deviceName }
        if (session != null) {
            logToUI("Restoring UI for existing session Cam ${session.index}")
            restoreCameraUI(session)
        } else {
            val manager = getSystemService(Context.USB_SERVICE) as UsbManager
            if (manager.hasPermission(device)) {
                 logToUI("Opening device (no session): ${device.deviceName}")
                 val conn = manager.openDevice(device)
                 if (conn != null) {
                     uvcService?.initCamera(device, conn)
                 }
            } else {
                 logToUI("Requesting permission for user-selected device: ${device.deviceName}")
                 requestPermission(device)
            }
        }
    }

    private fun restoreCameraUI(session: UvcStreamingService.CameraSession) {
        binding.ivPreview.setImageResource(0)
        binding.tvCameraId.visibility = View.VISIBLE
        binding.tvCameraId.text = "ID: ${session.index}"
        binding.tvUsbStatus.text = getString(R.string.usb_status_cam_index, session.index, session.device.productName ?: "")
        
        binding.tilResolution.visibility = View.VISIBLE
        binding.tilFps.visibility = View.VISIBLE
        binding.swPreview.visibility = View.VISIBLE
        binding.btnStreamToggle.visibility = View.VISIBLE
        
        val resolutionMap = mutableMapOf<String, List<String>>()
        session.supportedFormats.split(";").forEach { part ->
            if (part.contains(":")) {
                val resFps = part.split(":")
                resolutionMap[resFps[0]] = resFps[1].split(",").filter { it.isNotEmpty() }
            }
        }
        val resList = resolutionMap.keys.toList()
        if (resList.isEmpty()) {
            logToUI(getString(R.string.no_uvc_device))
            return
        }
        
        val resAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, resList)
        binding.actvResolution.setAdapter(resAdapter)
        val safeResPos = session.selectedResPos.coerceIn(0, Math.max(0, resList.size - 1))
        if (resList.isNotEmpty()) binding.actvResolution.setText(resList[safeResPos], false)
        
        fun updateFpsAdapter(resPos: Int) {
            val fpsList = resolutionMap[resList[resPos]] ?: emptyList()
            val fpsAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, fpsList)
            binding.actvFps.setAdapter(fpsAdapter)
            val safeFpsPos = session.selectedFpsPos.coerceIn(0, Math.max(0, fpsList.size - 1))
            if (fpsList.isNotEmpty()) binding.actvFps.setText(fpsList[safeFpsPos], false)
        }

        if (resList.isNotEmpty()) updateFpsAdapter(safeResPos)

        binding.actvResolution.setOnItemClickListener { _, _, pos, _ ->
            session.selectedResPos = pos
            updateFpsAdapter(pos)
        }

        binding.actvFps.setOnItemClickListener { _, _, pos, _ ->
            session.selectedFpsPos = pos
        }
        
        updateToggleButtonUI(session)
        binding.swPreview.isChecked = session.isPreviewEnabled
        updatePreviewVisibility(session)
    }

    private fun toggleStreaming() {
        val service = uvcService
        if (service == null) {
            logToUI(getString(R.string.service_not_ready))
            return
        }
        val dev = currentDevice
        if (dev == null) {
            logToUI(getString(R.string.no_device_selected))
            return
        }
        val session = service.sessionsByFd.values.find { it.device.deviceName == dev.deviceName }
        if (session == null) {
            logToUI(getString(R.string.camera_initializing_wait))
            return
        }
        
        binding.btnStreamToggle.isEnabled = false
        if (session.isStreaming) {
            service.stopStreaming(session.fd) {
                runOnUiThread {
                    binding.btnStreamToggle.isEnabled = true
                    updateToggleButtonUI(session)
                    binding.ivPreview.setImageResource(0)
                }
            }
        } else {
            val res = binding.actvResolution.text?.toString()
            val fpsStr = binding.actvFps.text?.toString()
            
            if (res.isNullOrEmpty() || fpsStr.isNullOrEmpty()) {
                logToUI(getString(R.string.error_selection_incomplete))
                binding.btnStreamToggle.isEnabled = true
                return
            }
            
            val fps = fpsStr.toIntOrNull() ?: 30
            val wh = res.split("x")
            val width = wh.getOrNull(0)?.toIntOrNull() ?: 640
            val height = wh.getOrNull(1)?.toIntOrNull() ?: 480
            
            service.startStreaming(session.fd, width, height, fps) {
                runOnUiThread {
                    binding.btnStreamToggle.isEnabled = true
                    updateToggleButtonUI(session)
                }
            }
        }
    }

    private fun updateToggleButtonUI(session: UvcStreamingService.CameraSession) {
        val isStreaming = session.isStreaming
        binding.btnStreamToggle.isEnabled = true
        binding.btnStreamToggle.text = if (isStreaming) getString(R.string.stop_streaming) else getString(R.string.start_streaming)
        
        // Correct way to change MaterialButton color without breaking style
        val color = if (isStreaming) Color.parseColor("#B00020") else ContextCompat.getColor(this, R.color.primary_theme_color)
        binding.btnStreamToggle.backgroundTintList = ColorStateList.valueOf(color)
        
        binding.tilResolution.isEnabled = !isStreaming
        binding.tilFps.isEnabled = !isStreaming
        binding.tvPreviewLabel.text = if (isStreaming) getString(R.string.preview_label_with_id, session.index) else getString(R.string.preview_label_id_only, session.index)
    }

    private fun requestPermission(device: UsbDevice) {
        val now = System.currentTimeMillis()
        val lastDenied = permissionCooldowns[device.deviceName] ?: 0L
        if (now - lastDenied < COOLDOWN_MS) {
            logToUI("Skip: Device ${device.deviceName} in cooldown after denial.")
            return
        }

        if (requestingPermissions.contains(device.deviceName)) {
            logToUI("Skip: Permission already requested for ${device.deviceName}")
            return
        }

        logToUI("Action: Requesting permission for ${device.deviceName}")
        logToUI("Quest 3 Hint: Check System Settings > Privacy > Connected Cameras.")
        requestingPermissions.add(device.deviceName)
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
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
        val action = intent?.action
        logToUI("Handling Activity Intent: $action")
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
            refreshDeviceList()
        }
    }

    private val usbDynamicReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (intent.action == ACTION_USB_PERMISSION) {
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                logToUI("Permission Result: ${device?.deviceName} granted=$granted")
                
                device?.let { 
                    requestingPermissions.remove(it.deviceName)
                    if (!granted) {
                        permissionCooldowns[it.deviceName] = System.currentTimeMillis()
                    }
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
            when(intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    logToUI("USB Detached: ${device?.deviceName}")
                    device?.let { dev ->
                        uvcService?.sessionsByFd?.values?.find { it.device.deviceName == dev.deviceName }?.let { 
                            uvcService?.releaseCamera(it.fd)
                        }
                        if (currentDevice?.deviceName == dev.deviceName) {
                            currentDevice = null
                        }
                        refreshDeviceList()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    logToUI("USB Attached: ${device?.deviceName}")
                    refreshDeviceList()
                }
                "net.d7z.net.oss.uvc.EXIT" -> {
                    logToUI("Exit signal received")
                    finish()
                }
            }
        }
    }

    private fun registerReceivers() {
        val systemFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction("net.d7z.net.oss.uvc.EXIT")
        }
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(usbSystemReceiver, systemFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(usbSystemReceiver, systemFilter)
        }

        val dynamicFilter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= 34) {
            registerReceiver(usbDynamicReceiver, dynamicFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbDynamicReceiver, dynamicFilter)
        }
    }

    private fun getUvcDevices(): List<UsbDevice> {
        val manager = getSystemService(Context.USB_SERVICE) as UsbManager
        return manager.deviceList.values.filter { 
            it.deviceClass == 239 || (0 until it.interfaceCount).any { i -> it.getInterface(i).interfaceClass == 14 }
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                val addresses = networkInterface.inetAddresses
                for (address in Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        uvcService?.onFrameUpdateListener = null
        uvcService?.onSessionCreatedListener = null
        uvcService?.onLogMessage = null
        if (isBound) unbindService(serviceConnection)
        mainHandler.removeCallbacks(uiUpdater)
        decodeExecutor.shutdown()
        try {
            unregisterReceiver(usbSystemReceiver)
            unregisterReceiver(usbDynamicReceiver)
        } catch (e: Exception) {}
    }
}
