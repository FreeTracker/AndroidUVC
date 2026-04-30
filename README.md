# UVC Camera (AndroidUVC)

Modern, High-performance UVC Camera Hub for Android.

Connect any UVC-compliant USB camera to your Android device and stream it via MJPEG to multiple browsers or network clients.

## ✨ Key Features

- **Modern Material Design 3 UI**: Fully responsive dashboard supporting both Phones and Tablets (adaptive 2-column layout).
- **High-performance Streaming**: Optimized C++ backend using `libusb` and `libuvc` for low-latency MJPEG capture.
- **Advanced Networking**:
  - **Custom HTTP Port**: User-configurable server port through the app settings.
  - **Multi-client Support**: Parallel streaming for multiple concurrent clients using Sequence ID tracking (no starvation).
  - **Deadlock-free Sync**: Native recursive mutex implementation for zero image tearing and stable performance.
- **Multi-language**: Official support for **English (EN)**, **简体中文 (ZH)**, **日本語 (JA)**, and **한국어 (KO)**.
- **Rich Dashboard**: Real-time FPS, Bandwidth monitoring, and easy-access server URL sharing.
- **Modern Build System**: Dependencies (`libusb` and `libuvc`) managed via **Git Subtree** for stability and maintainability.

## 🚀 Getting Started

### Prerequisites

- Android 8.0 (API 26) or higher.
- A USB OTG-compatible Android device.
- A UVC-compliant USB Camera.

### Building

1. Clone the repository:
   ```bash
   git clone https://github.com/FreeTracker/AndroidUVC.git
   cd AndroidUVC
   ```
2. Open with Android Studio (Koala or newer recommended).
3. Connect your device and run `assembleDebug`.

## 🛠️ Architecture

- **Native Tier**: `libusb` + `libuvc` (linked as Git Subtrees) provide the raw YUYV/MJPEG frame capture.
- **Service Tier**: `UvcStreamingService` (Foreground Service) manages device lifecycle, statistics calculation, and MJPEG server dispatching.
- **Server Tier**: Integrated `NanoHTTPD` provides the HTTP landing page and MJPEG stream endpoints.
- **UI Tier**: Activity/Fragment-based dashboard featuring ViewBinding and Material 3 adaptive layouts.

## 🌐 HTTP Endpoints

- `http://<device-ip>:<port>/`: Modern dashboard listing all active cameras and project info.
- `http://<device-ip>:<port>/camera/{id}`: Direct MJPEG stream for a specific camera (e.g., `/camera/0`).

## 📜 License

This project is licensed under GPL-3.0-or-later. Bundled third-party components, including `libusb` and `libuvc`, remain under their respective original licenses. See `LICENSE` and the in-app open source notices for details.
