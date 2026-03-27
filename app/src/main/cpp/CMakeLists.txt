cmake_minimum_required(VERSION 3.10)
project(AndroidUVC_Native)

# 1. 包含子目录（会自动执行子目录的 CMakeLists.txt）
add_subdirectory(libusb)
add_subdirectory(libuvc)

# 2. 定义你的 JNI 库
add_library(native-lib SHARED native-lib.cpp)

# 3. 链接顺序：你的库 -> libuvc -> libusb -> 系统 log 库
target_link_libraries(native-lib
    uvc
    usb
    log
    android
)

# 4. 确保头文件路径正确
target_include_directories(native-lib PRIVATE
    libusb
    libuvc/include
)