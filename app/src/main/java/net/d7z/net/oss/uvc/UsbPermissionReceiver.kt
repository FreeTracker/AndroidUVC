package net.d7z.net.oss.uvc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log

class UsbPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("USB_DEBUG", "UsbPermissionReceiver onReceive: $action")
        
        if ("net.d7z.net.oss.uvc.USB_PERMISSION" == action) {
            val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            
            var isGranted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            Log.d("USB_DEBUG", "Static Receiver: isGranted from intent = $isGranted")

            // 如果 Intent 里是 false，尝试通过 manager 二次确认
            if (!isGranted && device != null) {
                val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                isGranted = manager.hasPermission(device)
                Log.d("USB_DEBUG", "Static Receiver: Secondary check = $isGranted")
            }

            if (isGranted && device != null) {
                // 权限正式到手，唤起 MainActivity 继续流程
                Log.d("USB_DEBUG", "Permission GRANTED. Launching MainActivity...")
                val mainIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("USB_PERMISSION_GRANTED", true)
                    putExtra(UsbManager.EXTRA_DEVICE, device)
                }
                context.startActivity(mainIntent)
            } else {
                Log.e("USB_DEBUG", "Permission DENIED in Static Receiver.")
            }
        }
    }
}