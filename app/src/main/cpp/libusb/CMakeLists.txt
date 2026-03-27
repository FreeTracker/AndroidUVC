cmake_minimum_required(VERSION 3.10)
project(libusb_internal C)

# 定义 libusb 核心源码文件（根据版本可能略有差异）
set(LIBUSB_SOURCES
    libusb/core.c
    libusb/descriptor.c
    libusb/hotplug.c
    libusb/io.c
    libusb/strerror.c
    libusb/sync.c
    # Android/Linux 特定后端
    libusb/os/linux_usbfs.c
    libusb/os/linux_netlink.c
    libusb/os/threads_posix.c
    libusb/os/events_posix.c    # <--- 检查是否有这个文件，处理事件通知
    
)

add_library(usb STATIC ${LIBUSB_SOURCES})

# 关键：Android 需要链接 log 库且定义特定的宏
target_compile_definitions(usb PRIVATE
    PLATFORM_POSIX
    OS_LINUX
    HAVE_TIMERFD
    HAVE_EVENTFD
)
target_include_directories(usb PUBLIC . ./libusb)
target_include_directories(usb PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}  # 这样编译器才能找到刚才写的 config.h
    ${CMAKE_CURRENT_SOURCE_DIR}/libusb
)
