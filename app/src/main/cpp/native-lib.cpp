#include <jni.h>
#include <string>
#include <android/log.h>
#include <string.h>
#include <malloc.h>
#include <sstream>
#include <atomic>
#include <thread>
#include <map>
#include <mutex>
#include <memory>

extern "C" {
    #include <libusb.h>
    #include <libuvc/libuvc.h>
    #include "libuvc/libuvc_internal.h"
    uvc_error_t uvc_open_internal(uvc_device_t *dev, libusb_device_handle *usb_devh, uvc_device_handle_t **devh);
}

#define TAG "UVC_NATIVE"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

struct CameraContext {
    int fd;
    libusb_context *usb_ctx = nullptr;
    uvc_context_t *uvc_ctx = nullptr;
    uvc_device_handle_t *devh = nullptr;
    uvc_device_t *dev = nullptr;
    
    std::atomic<bool> event_thread_run{false};
    std::thread event_thread;
    
    jobject java_buffer = nullptr;
    uint8_t *buffer_ptr = nullptr;
    std::atomic<uint64_t> frame_count{0};
    std::recursive_mutex buffer_mutex;

    std::atomic<bool> cleaned_up{false};

    void cleanup(JNIEnv* env) {
        bool expected = false;
        if (!cleaned_up.compare_exchange_strong(expected, true)) {
            return;
        }

        // 1. MUST stop UVC streaming BEFORE stopping the event thread to avoid deadlock.
        if (devh) {
            uvc_stop_streaming(devh);
            uvc_close(devh);
            devh = nullptr;
        }

        // 2. NOW safely stop the event thread.
        event_thread_run = false;
        if (event_thread.joinable()) {
            event_thread.join();
        }

        // 3. Free remaining contexts
        if (dev) {
            free(dev);
            dev = nullptr;
        }
        if (uvc_ctx) {
            uvc_exit(uvc_ctx);
            uvc_ctx = nullptr;
        }
        if (usb_ctx) {
            libusb_exit(usb_ctx);
            usb_ctx = nullptr;
        }

        // 4. Release global JVM reference
        if (java_buffer && env) {
            env->DeleteGlobalRef(java_buffer);
            java_buffer = nullptr;
        }
    }

    ~CameraContext() {
        // Fallback in case not explicitly called
        cleanup(nullptr);
    }
};

JavaVM *g_jvm = nullptr;
jobject g_java_callback_obj = nullptr;
jmethodID g_on_frame_mid = nullptr;

std::map<int, std::shared_ptr<CameraContext>> g_camera_map;
std::mutex g_map_mutex;

std::shared_ptr<CameraContext> get_camera_context(int fd) {
    std::lock_guard<std::mutex> lock(g_map_mutex);
    auto it = g_camera_map.find(fd);
    if (it != g_camera_map.end()) {
        return it->second;
    }
    return nullptr;
}

void usb_event_thread(std::shared_ptr<CameraContext> ctx) {
    LOGI("Native[%d]: Event thread started", ctx->fd);
    while (ctx->event_thread_run) {
        struct timeval tv = {0, 100000};
        libusb_handle_events_timeout_completed(ctx->usb_ctx, &tv, NULL);
    }
    LOGI("Native[%d]: Event thread stopped", ctx->fd);
}

void frame_callback(uvc_frame_t *frame, void *ptr) {
    CameraContext *ctx = (CameraContext*)ptr;
    ctx->frame_count++;
    
    if (frame->frame_format != UVC_FRAME_FORMAT_MJPEG || !ctx->buffer_ptr) return;
    
    {
        std::lock_guard<std::recursive_mutex> lock(ctx->buffer_mutex);
        if (ctx->cleaned_up || !ctx->buffer_ptr) return;
        memcpy(ctx->buffer_ptr, frame->data, frame->data_bytes);
    }

    JNIEnv *env;
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) return;
    }
    
    if (g_java_callback_obj && g_on_frame_mid) {
        env->CallVoidMethod(g_java_callback_obj, g_on_frame_mid, (jint)ctx->fd, (jint)frame->data_bytes);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_lockBuffer(JNIEnv *env, jobject thiz, jint fd) {
    auto ctx = get_camera_context(fd);
    if (ctx) {
        ctx->buffer_mutex.lock();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_unlockBuffer(JNIEnv *env, jobject thiz, jint fd) {
    auto ctx = get_camera_context(fd);
    if (ctx) {
        ctx->buffer_mutex.unlock();
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_getDeviceSupportedFormats(JNIEnv *env, jobject thiz, jint fd) {
    if (!g_java_callback_obj) {
        std::lock_guard<std::mutex> lock(g_map_mutex);
        if (!g_java_callback_obj) {
            env->GetJavaVM(&g_jvm);
            g_java_callback_obj = env->NewGlobalRef(thiz);
            g_on_frame_mid = env->GetMethodID(env->GetObjectClass(thiz), "onFrameReady", "(II)V");
        }
    }

    std::shared_ptr<CameraContext> old_ctx;
    {
        std::lock_guard<std::mutex> lock(g_map_mutex);
        auto it = g_camera_map.find(fd);
        if (it != g_camera_map.end()) {
            old_ctx = it->second;
            g_camera_map.erase(it);
        }
    }
    
    if (old_ctx) {
        old_ctx->cleanup(env);
    }

    auto ctx = std::make_shared<CameraContext>();
    ctx->fd = fd;

    libusb_set_option(NULL, (libusb_option)2);
    if (libusb_init(&ctx->usb_ctx) < 0) { return env->NewStringUTF(""); }
    libusb_set_option(ctx->usb_ctx, (libusb_option)3, 32);

    libusb_device_handle *usb_handle = NULL;
    if (libusb_wrap_sys_device(ctx->usb_ctx, (intptr_t)fd, &usb_handle) < 0) {
        ctx->cleanup(env); return env->NewStringUTF("");
    }

    ctx->event_thread_run = true;
    ctx->event_thread = std::thread(usb_event_thread, ctx);

    if (uvc_init(&ctx->uvc_ctx, ctx->usb_ctx) < 0) {
        libusb_close(usb_handle); ctx->cleanup(env); return env->NewStringUTF("");
    }

    ctx->dev = (uvc_device_t *)calloc(1, sizeof(uvc_device_t));
    ctx->dev->ctx = ctx->uvc_ctx;
    ctx->dev->usb_dev = libusb_get_device(usb_handle);
    ctx->dev->ref = 1;

    if (uvc_open_internal(ctx->dev, usb_handle, &ctx->devh) != UVC_SUCCESS) {
        libusb_close(usb_handle); ctx->cleanup(env); return env->NewStringUTF("");
    }

    {
        std::lock_guard<std::mutex> lock(g_map_mutex);
        g_camera_map[fd] = ctx;
    }

    std::stringstream ss;
    const uvc_format_desc_t *format_desc = uvc_get_format_descs(ctx->devh);
    while (format_desc) {
        char fourcc[5] = {0};
        memcpy(fourcc, format_desc->fourccFormat, 4);
        if (strcmp(fourcc, "MJPG") == 0) {
            const uvc_frame_desc_t *frame_desc = format_desc->frame_descs;
            while (frame_desc) {
                ss << frame_desc->wWidth << "x" << frame_desc->wHeight << ":";
                if (frame_desc->intervals) {
                    for (uint32_t *interval = frame_desc->intervals; *interval; ++interval) {
                        ss << (10000000 / *interval) << ( (*(interval+1)) ? "," : "" );
                    }
                }
                ss << ";";
                frame_desc = frame_desc->next;
            }
        }
        format_desc = format_desc->next;
    }
    return env->NewStringUTF(ss.str().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_startUVC(JNIEnv *env, jobject thiz, jint fd, jobject buffer, jint width, jint height, jint fps) {
    auto ctx = get_camera_context(fd);
    if (!ctx) return;

    {
        std::lock_guard<std::recursive_mutex> lock(ctx->buffer_mutex);
        if (ctx->java_buffer) env->DeleteGlobalRef(ctx->java_buffer);
        ctx->java_buffer = env->NewGlobalRef(buffer);
        ctx->buffer_ptr = (uint8_t*)env->GetDirectBufferAddress(buffer);
    }

    uvc_stop_streaming(ctx->devh);
    std::this_thread::sleep_for(std::chrono::milliseconds(250));

    uvc_stream_ctrl_t ctrl;
    uvc_error_t res = uvc_get_stream_ctrl_format_size(ctx->devh, &ctrl, UVC_FRAME_FORMAT_MJPEG, width, height, fps);
    
    if (res != UVC_SUCCESS) {
        LOGE("Native[%d]: Failed to get stream ctrl (%s), retrying...", fd, uvc_strerror(res));
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
        res = uvc_get_stream_ctrl_format_size(ctx->devh, &ctrl, UVC_FRAME_FORMAT_MJPEG, width, height, fps);
    }

    if (res == UVC_SUCCESS) {
        res = uvc_start_streaming(ctx->devh, &ctrl, frame_callback, (void*)ctx.get(), 0);
        if (res == UVC_SUCCESS) {
            LOGI("Native[%d]: Streaming started.", fd);
        } else {
            LOGE("Native[%d]: Failed to start streaming: %s", fd, uvc_strerror(res));
        }
    } else {
        LOGE("Native[%d]: Final fail to get stream ctrl: %s", fd, uvc_strerror(res));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_stopUVC(JNIEnv *env, jobject thiz, jint fd) {
    auto ctx = get_camera_context(fd);
    if (ctx) {
        uvc_stop_streaming(ctx->devh);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_releaseUVC(JNIEnv *env, jobject thiz, jint fd) {
    std::shared_ptr<CameraContext> target_ctx;
    {
        std::lock_guard<std::mutex> lock(g_map_mutex);
        auto it = g_camera_map.find(fd);
        if (it != g_camera_map.end()) {
            target_ctx = it->second;
            // Intentionally DO NOT erase yet. We want unlockBuffer to still find it.
        }
    }
    
    if (target_ctx) {
        target_ctx->cleanup(env);
        
        // Wait gracefully for any ongoing lockBuffer/unlockBuffer operations from Kotlin
        std::lock_guard<std::recursive_mutex> buffer_lock(target_ctx->buffer_mutex);
        
        {
            // Fully safe to erase now
            std::lock_guard<std::mutex> lock(g_map_mutex);
            g_camera_map.erase(fd);
        }
    }
}
