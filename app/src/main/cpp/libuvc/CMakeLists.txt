cmake_minimum_required(VERSION 3.10)

# 手动指定核心源文件，跳过 example.c, test.c, frame-mjpeg.c
set(LIBUVC_SOURCES
    src/ctrl.c
    src/ctrl-gen.c
    src/device.c
    src/diag.c
    src/frame.c
    src/init.c
    src/misc.c
    src/stream.c
)

add_library(uvc STATIC ${LIBUVC_SOURCES})

target_include_directories(uvc PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ../libusb  # 确保能找到 libusb.h
)

# 链接 libusb
target_link_libraries(uvc usb)