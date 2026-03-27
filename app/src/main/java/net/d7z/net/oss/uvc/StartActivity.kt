package net.d7z.net.oss.uvc

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.d7z.net.oss.uvc.databinding.ActivityStartBinding

class StartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartBinding
    private val REQUEST_CODE_PERMISSIONS = 1001
    private val ACTION_USB_PERMISSION = "net.d7z.net.oss.uvc.USB_PERMISSION"
    private var isRequestingUsb = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                isRequestingUsb = false
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                Log.d("StartActivity", "USB Permission result: $granted")
                checkAllAndNavigate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. 立即检查：如果权限都 OK，直接静默跳转，不加载 UI
        if (allPermissionsGranted() && allUsbPermissionsGranted()) {
            startMainActivity()
            return
        }

        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        binding.btnGrant.setOnClickListener {
            if (!allPermissionsGranted()) {
                requestRuntimePermissions()
            } else {
                requestUsbPermissions()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (e: Exception) {}
    }

    private fun allPermissionsGranted(): Boolean {
        return getRequiredRuntimePermissions().all {
            // Some permissions might be platform-specific and return denied on others
            if (it == "horizonos.permission.USB_CAMERA" && !isQuestDevice()) return@all true
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isQuestDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return manufacturer.contains("Oculus", ignoreCase = true) || 
               manufacturer.contains("Meta", ignoreCase = true) ||
               model.contains("Quest", ignoreCase = true)
    }

    private fun allUsbPermissionsGranted(): Boolean {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val uvcDevices = getUvcDevices()
        if (uvcDevices.isEmpty()) return true 
        return uvcDevices.all { usbManager.hasPermission(it) }
    }

    private fun getRequiredRuntimePermissions(): Array<String> {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        
        if (isQuestDevice()) {
            permissions.add("horizonos.permission.USB_CAMERA")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    private fun requestRuntimePermissions() {
        ActivityCompat.requestPermissions(this, getRequiredRuntimePermissions(), REQUEST_CODE_PERMISSIONS)
    }

    private fun requestUsbPermissions() {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val uvcDevices = getUvcDevices()
        val firstUnauthorized = uvcDevices.find { !usbManager.hasPermission(it) }
        
        if (firstUnauthorized != null) {
            if (isRequestingUsb) return
            isRequestingUsb = true
            
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            
            val permissionIntent = PendingIntent.getBroadcast(this, firstUnauthorized.deviceId, intent, flags)
            Log.d("StartActivity", "Requesting USB permission for: ${firstUnauthorized.deviceName}")
            usbManager.requestPermission(firstUnauthorized, permissionIntent)
        } else {
            // 没有待授权的 USB 设备，直接尝试跳转
            checkAllAndNavigate()
        }
    }

    private fun getUvcDevices(): List<UsbDevice> {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.filter { device ->
            (0 until device.interfaceCount).any { i ->
                val intf = device.getInterface(i)
                intf.interfaceClass == 14 // VIDEO
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // 如果运行权限通过，且没有待授权 USB 或已全部授权 USB，则跳转
                if (allUsbPermissionsGranted()) {
                    startMainActivity()
                } else {
                    requestUsbPermissions()
                }
            }
        }
    }

    private fun checkAllAndNavigate() {
        if (allPermissionsGranted() && allUsbPermissionsGranted()) {
            startMainActivity()
        }
    }

    private fun startMainActivity() {
        Log.d("StartActivity", "Navigating to MainActivity")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
