package net.d7z.net.oss.uvc.ui.model

data class StartGateUiState(
    val runtimePermissionsGranted: Boolean = false,
    val usbPermissionsGranted: Boolean = false,
    val requestingUsb: Boolean = false,
    val showUsbPermissionDialog: Boolean = false
)
