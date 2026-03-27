# Android UVC Hub - Project Documentation

This project is a high-performance Android application for streaming UVC (USB Video Class) cameras over a local HTTP server. It features a modern Material 3 UI and robust native integration for reliable camera control.

## 🛠 Tech Stack

- **Language**: Kotlin (Android) & C++ (JNI)
- **Library**: `libuvc` for lower-level USB camera access
- **UI Framework**: Material 3 with View Binding
- **Networking**: Built-in HTTP server for multi-client MJPEG streaming

## 🏗 Project Architecture

### 1. UI Layer (Kotlin)

- **`MainActivity.kt`**: The main control center. Manages device selection, resolution/FPS configuration, and live preview. Displays real-time bandwidth and FPS stats.
- **`StartActivity.kt`**: Guided startup flow. Handles runtime permissions (Camera, Notifications) and requests USB hardware access for connected cameras before launching the main UI.

### 2. Service Layer (Kotlin)

- **`UvcStreamingService.kt`**: A Foreground Service that maintains camera sessions (`CameraSession`). It hosts the MJPEG HTTP server and coordinates with the native layer to fetch and distribute frames.

### 3. Native Layer (C++ / JNI)

- **`native-lib.cpp`**: JNI bridge using `libuvc`.
  - Implements `startUVC`, `stopUVC`, and `getFrame`.
  - Includes a **250ms hardware reset delay** and **retry logic** for improved streaming reliability on varied hardware.

### 4. Resources & Configuration

- **`activity_main.xml`**: Modern layout using Material 3 Exposed Dropdown Menus.
- **`strings.xml` & `values-zh/strings.xml`**: Full multi-language support (English/Chinese).
- **USB Filter**: `usb_device_filter.xml` used for automatic app waking upon device attachment.

## 🔑 Key Features

- **Material 3 Design**: Premium Look & Feel with outlined dropdowns.
- **Device Disambiguation**: Identifies unique cameras via USB bus/address shortcuts (e.g., `[001/010]`).
- **Robust Streaming**: Optimized native lifecycle management to prevent "first-click" failures.
- **Guided Setup**: Permission guide ensures all hardware and software requirements are met.

## 📁 Directory Structure

- `app/src/main/java/net/d7z/net/oss/uvc/`: Kotlin source code.
- `app/src/main/cpp/`: Native C++ source using `libuvc`.
- `app/src/main/res/layout/`: Material 3 XML layouts.
- `app/src/main/res/values/`: Default (English) strings and themes.
- `app/src/main/res/values-zh/`: Chinese translations.
