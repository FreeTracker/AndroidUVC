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
#include <chrono>
#include <set>
#include <tuple>
#include <vector>

extern "C" {
    #include <libusb.h>
    #include <libuvc/libuvc.h>
    #include "libuvc/libuvc_internal.h"
    uvc_error_t uvc_open_internal(uvc_device_t *dev, libusb_device_handle *usb_devh, uvc_device_handle_t **devh);
}

#define TAG "UVC_NATIVE"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

// Global Libusb Context and Event Thread
static libusb_context *g_usb_ctx = nullptr;
static std::thread g_event_thread;
static std::atomic<bool> g_event_thread_run{false};
static std::mutex g_usb_init_mutex;
static std::mutex g_callback_mutex;

void global_usb_event_thread() {
    LOGI("Global USB event thread started");
    while (g_event_thread_run) {
        struct timeval tv = {0, 100000};
        libusb_handle_events_timeout_completed(g_usb_ctx, &tv, NULL);
    }
    LOGI("Global USB event thread stopped");
}

void ensure_usb_ctx() {
    std::lock_guard<std::mutex> lock(g_usb_init_mutex);
    if (!g_usb_ctx) {
        // Option 2 is LIBUSB_OPTION_NO_DEVICE_DISCOVERY in many libusb-1.0.22+ versions
        // On some platforms it might be different, but for Android UVC it's standard.
        libusb_set_option(NULL, (libusb_option)2);
        if (libusb_init(&g_usb_ctx) >= 0) {
            g_event_thread_run = true;
            g_event_thread = std::thread(global_usb_event_thread);
        } else {
            LOGE("Failed to initialize libusb context");
            g_usb_ctx = nullptr;
        }
    }
}

void clear_java_callback(JNIEnv *env);

void shutdown_usb_ctx_if_idle() {
    {
        std::lock_guard<std::mutex> lock(g_usb_init_mutex);
        if (!g_usb_ctx) {
            return;
        }
    }

    g_event_thread_run = false;
    if (g_event_thread.joinable()) {
        g_event_thread.join();
    }

    std::lock_guard<std::mutex> lock(g_usb_init_mutex);
    if (g_usb_ctx) {
        libusb_exit(g_usb_ctx);
        g_usb_ctx = nullptr;
    }
}

void release_claimed_stream_interfaces(uvc_device_handle_t *devh, int fd) {
    if (!devh || !devh->info) {
        return;
    }

    const auto control_if = devh->info->ctrl_if.bInterfaceNumber;
    for (int if_idx = 0; if_idx < 32; ++if_idx) {
        const auto mask = (1u << if_idx);
        if (!(devh->claimed & mask) || if_idx == control_if) {
            continue;
        }

        LOGI("Native[%d]: Releasing leftover claimed stream interface %d", fd, if_idx);
        uvc_release_if(devh, if_idx);
    }
}

struct CameraContext {
    int fd;
    uvc_context_t *uvc_ctx = nullptr;
    uvc_device_handle_t *devh = nullptr;
    uvc_device_t *dev = nullptr;
    
    jobject java_buffer = nullptr;
    uint8_t *buffer_ptr = nullptr;
    jlong buffer_capacity = 0;
    std::atomic<uint64_t> frame_count{0};
    std::recursive_mutex buffer_mutex;
    std::mutex uvc_mutex; // Protects libuvc streaming operations

    std::atomic<bool> cleaned_up{false};

    void cleanup(JNIEnv* env) {
        bool expected = false;
        if (!cleaned_up.compare_exchange_strong(expected, true)) {
            return;
        }

        {
            std::lock_guard<std::mutex> lock(uvc_mutex);
            if (devh) {
                uvc_stop_streaming(devh);
                release_claimed_stream_interfaces(devh, fd);
                uvc_close(devh);
                devh = nullptr;
            }

            if (dev) {
                uvc_unref_device(dev);
                dev = nullptr;
            }
            if (uvc_ctx) {
                uvc_exit(uvc_ctx);
                uvc_ctx = nullptr;
            }
        }

        if (java_buffer && env) {
            env->DeleteGlobalRef(java_buffer);
            java_buffer = nullptr;
        }
        buffer_ptr = nullptr;
        buffer_capacity = 0;
    }

    ~CameraContext() {
        cleanup(nullptr);
    }
};

static JavaVM *g_jvm = nullptr;
static jobject g_java_callback_obj = nullptr;
static jmethodID g_on_frame_mid = nullptr;

static std::map<int, std::shared_ptr<CameraContext>> g_camera_map;
static std::mutex g_map_mutex;

std::shared_ptr<CameraContext> get_camera_context(int fd) {
    std::lock_guard<std::mutex> lock(g_map_mutex);
    auto it = g_camera_map.find(fd);
    if (it != g_camera_map.end()) {
        return it->second;
    }
    return nullptr;
}

uvc_error_t negotiate_mjpeg_stream_ctrl(
        uvc_device_handle_t *devh,
        uvc_stream_ctrl_t *ctrl,
        int fd,
        int width,
        int height,
        int fps) {
    if (!devh || !ctrl) {
        return UVC_ERROR_INVALID_PARAM;
    }

    std::vector<std::tuple<uint8_t, uint8_t, uint8_t, uint32_t>> candidates;
    const uvc_format_desc_t *format_desc = uvc_get_format_descs(devh);
    while (format_desc) {
        char fourcc[5] = {0};
        memcpy(fourcc, format_desc->fourccFormat, 4);
        if (strcmp(fourcc, "MJPG") == 0) {
            const uvc_frame_desc_t *frame_desc = format_desc->frame_descs;
            while (frame_desc) {
                if (frame_desc->wWidth == width && frame_desc->wHeight == height && frame_desc->intervals) {
                    for (uint32_t *interval = frame_desc->intervals; *interval; ++interval) {
                        const auto current_fps = static_cast<int>(10000000 / *interval);
                        if (fps == 0 || current_fps == fps) {
                            candidates.emplace_back(
                                    format_desc->bFormatIndex,
                                    frame_desc->bFrameIndex,
                                    format_desc->parent->bInterfaceNumber,
                                    *interval);
                        }
                    }
                }
                frame_desc = frame_desc->next;
            }
        }
        format_desc = format_desc->next;
    }

    if (candidates.empty()) {
        return UVC_ERROR_INVALID_MODE;
    }

    uvc_error_t last_error = UVC_ERROR_INVALID_MODE;
    for (const auto &candidate : candidates) {
        memset(ctrl, 0, sizeof(*ctrl));
        ctrl->bFormatIndex = std::get<0>(candidate);
        ctrl->bFrameIndex = std::get<1>(candidate);
        ctrl->bInterfaceNumber = std::get<2>(candidate);
        ctrl->dwFrameInterval = std::get<3>(candidate);
        ctrl->bmHint = (1 << 0);

        uvc_claim_if(devh, ctrl->bInterfaceNumber);
        uvc_query_stream_ctrl(devh, ctrl, 1, UVC_GET_MAX);

        ctrl->bFormatIndex = std::get<0>(candidate);
        ctrl->bFrameIndex = std::get<1>(candidate);
        ctrl->bInterfaceNumber = std::get<2>(candidate);
        ctrl->dwFrameInterval = std::get<3>(candidate);
        ctrl->bmHint = (1 << 0);

        LOGI("Native[%d]: Probing MJPEG candidate fmt=%u frame=%u if=%u interval=%u (~%d fps)",
             fd,
             static_cast<unsigned>(ctrl->bFormatIndex),
             static_cast<unsigned>(ctrl->bFrameIndex),
             static_cast<unsigned>(ctrl->bInterfaceNumber),
             static_cast<unsigned>(ctrl->dwFrameInterval),
             ctrl->dwFrameInterval ? static_cast<int>(10000000 / ctrl->dwFrameInterval) : 0);

        const auto res = uvc_probe_stream_ctrl(devh, ctrl);
        if (res == UVC_SUCCESS) {
            return UVC_SUCCESS;
        }

        LOGE("Native[%d]: Candidate fmt=%u frame=%u probe failed: %s",
             fd,
             static_cast<unsigned>(ctrl->bFormatIndex),
             static_cast<unsigned>(ctrl->bFrameIndex),
             uvc_strerror(res));
        uvc_release_if(devh, ctrl->bInterfaceNumber);
        last_error = res;
    }

    return last_error;
}

void refresh_java_callback(JNIEnv *env, jobject thiz) {
    std::lock_guard<std::mutex> lock(g_callback_mutex);
    if (g_java_callback_obj) {
        env->DeleteGlobalRef(g_java_callback_obj);
        g_java_callback_obj = nullptr;
    }
    env->GetJavaVM(&g_jvm);
    g_java_callback_obj = env->NewGlobalRef(thiz);
    jclass service_class = env->GetObjectClass(thiz);
    g_on_frame_mid = env->GetMethodID(service_class, "onFrameReady", "(II)V");
    env->DeleteLocalRef(service_class);
}

void clear_java_callback(JNIEnv *env) {
    std::lock_guard<std::mutex> lock(g_callback_mutex);
    if (g_java_callback_obj && env) {
        env->DeleteGlobalRef(g_java_callback_obj);
    }
    g_java_callback_obj = nullptr;
    g_on_frame_mid = nullptr;
}

void frame_callback(uvc_frame_t *frame, void *ptr) {
    CameraContext *ctx = (CameraContext*)ptr;
    ctx->frame_count++;
    
    if (frame->frame_format != UVC_FRAME_FORMAT_MJPEG || !ctx->buffer_ptr) return;
    
    {
        std::lock_guard<std::recursive_mutex> lock(ctx->buffer_mutex);
        if (ctx->cleaned_up || !ctx->buffer_ptr) return;
        if (frame->data_bytes == 0 || static_cast<jlong>(frame->data_bytes) > ctx->buffer_capacity) {
            LOGE("Native[%d]: Dropping oversized or empty frame (%zu > %lld)",
                 ctx->fd, frame->data_bytes, static_cast<long long>(ctx->buffer_capacity));
            return;
        }
        memcpy(ctx->buffer_ptr, frame->data, frame->data_bytes);
    }

    JNIEnv *env = nullptr;
    bool did_attach = false;
    if (g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) return;
        did_attach = true;
    }

    {
        std::lock_guard<std::mutex> lock(g_callback_mutex);
        if (g_java_callback_obj && g_on_frame_mid) {
            env->CallVoidMethod(g_java_callback_obj, g_on_frame_mid, (jint)ctx->fd, (jint)frame->data_bytes);
            if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        } else {
            LOGE("Native[%d]: Frame arrived but Java callback is not attached", ctx->fd);
        }
    }

    if (did_attach) {
        g_jvm->DetachCurrentThread();
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
    ensure_usb_ctx();
    if (!g_usb_ctx) {
        LOGE("libusb_context not available");
        return env->NewStringUTF("");
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

    libusb_device_handle *usb_handle = NULL;
    if (libusb_wrap_sys_device(g_usb_ctx, (intptr_t)fd, &usb_handle) < 0) {
        LOGE("libusb_wrap_sys_device failed for FD %d", fd);
        ctx->cleanup(env); return env->NewStringUTF("");
    }

    if (uvc_init(&ctx->uvc_ctx, g_usb_ctx) < 0) {
        LOGE("uvc_init failed for FD %d", fd);
        libusb_close(usb_handle); ctx->cleanup(env); return env->NewStringUTF("");
    }

    ctx->dev = (uvc_device_t *)calloc(1, sizeof(uvc_device_t));
    ctx->dev->ctx = ctx->uvc_ctx;
    ctx->dev->usb_dev = libusb_get_device(usb_handle);
    libusb_ref_device(ctx->dev->usb_dev);
    ctx->dev->ref = 1;

    libusb_device_descriptor desc{};
    if (libusb_get_device_descriptor(ctx->dev->usb_dev, &desc) == 0) {
        LOGI("Native[%d]: USB device descriptor vid=0x%04x pid=0x%04x class=%u subclass=%u proto=%u",
             fd, desc.idVendor, desc.idProduct, desc.bDeviceClass, desc.bDeviceSubClass, desc.bDeviceProtocol);
    } else {
        LOGE("Native[%d]: Failed to read USB device descriptor", fd);
    }

    if (uvc_open_internal(ctx->dev, usb_handle, &ctx->devh) != UVC_SUCCESS) {
        LOGE("uvc_open_internal failed for FD %d", fd);
        ctx->cleanup(env); return env->NewStringUTF("");
    }

    {
        std::lock_guard<std::mutex> lock(g_map_mutex);
        g_camera_map[fd] = ctx;
    }

    std::stringstream ss;
    std::set<std::string> seen_modes;
    const uvc_format_desc_t *format_desc = uvc_get_format_descs(ctx->devh);
    while (format_desc) {
        char fourcc[5] = {0};
        memcpy(fourcc, format_desc->fourccFormat, 4);
        LOGI("Native[%d]: format index=%u fourcc=%s bpp=%u",
             fd, format_desc->bFormatIndex, fourcc, format_desc->bBitsPerPixel);
        if (strcmp(fourcc, "MJPG") == 0) {
            const uvc_frame_desc_t *frame_desc = format_desc->frame_descs;
            while (frame_desc) {
                std::stringstream frame_log;
                frame_log << "Native[" << fd << "]: MJPEG frame "
                          << frame_desc->wWidth << "x" << frame_desc->wHeight << " fps=";
                std::stringstream mode_ss;
                mode_ss << frame_desc->wWidth << "x" << frame_desc->wHeight << ":";
                if (frame_desc->intervals) {
                    for (uint32_t *interval = frame_desc->intervals; *interval; ++interval) {
                        const auto current_fps = (10000000 / *interval);
                        mode_ss << current_fps << ( (*(interval+1)) ? "," : "" );
                        frame_log << current_fps << ( (*(interval+1)) ? "," : "" );
                    }
                }
                LOGI("%s", frame_log.str().c_str());
                const auto mode = mode_ss.str();
                if (seen_modes.insert(mode).second) {
                    ss << mode << ";";
                }
                frame_desc = frame_desc->next;
            }
        }
        format_desc = format_desc->next;
    }
    return env->NewStringUTF(ss.str().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_attachServiceCallback(JNIEnv *env, jobject thiz) {
    refresh_java_callback(env, thiz);
}

extern "C" JNIEXPORT void JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_detachServiceCallback(JNIEnv *env, jobject thiz) {
    clear_java_callback(env);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_startUVC(JNIEnv *env, jobject thiz, jint fd, jobject buffer, jint width, jint height, jint fps) {
    auto ctx = get_camera_context(fd);
    if (!ctx || !buffer) return JNI_FALSE;

    auto *buffer_ptr = (uint8_t*)env->GetDirectBufferAddress(buffer);
    const jlong buffer_capacity = env->GetDirectBufferCapacity(buffer);
    if (!buffer_ptr || buffer_capacity <= 0) {
        LOGE("Native[%d]: Invalid direct buffer", fd);
        return JNI_FALSE;
    }

    LOGI("Native[%d]: startUVC request width=%d height=%d fps=%d bufferCapacity=%lld",
         fd, width, height, fps, static_cast<long long>(buffer_capacity));

    {
        std::lock_guard<std::recursive_mutex> lock(ctx->buffer_mutex);
        if (ctx->java_buffer) env->DeleteGlobalRef(ctx->java_buffer);
        ctx->java_buffer = env->NewGlobalRef(buffer);
        ctx->buffer_ptr = buffer_ptr;
        ctx->buffer_capacity = buffer_capacity;
    }

    std::unique_lock<std::mutex> uvc_lock(ctx->uvc_mutex);
    if (ctx->cleaned_up || !ctx->devh) return JNI_FALSE;

    uvc_stop_streaming(ctx->devh);
    
    // Unlock to allow UI detach (cleanup) while we afford hardware settle time
    uvc_lock.unlock();
    std::this_thread::sleep_for(std::chrono::milliseconds(250));
    uvc_lock.lock();

    if (ctx->cleaned_up || !ctx->devh) return JNI_FALSE;

    uvc_stream_ctrl_t ctrl;
    uvc_error_t res = negotiate_mjpeg_stream_ctrl(ctx->devh, &ctrl, fd, width, height, fps);
    
    if (res != UVC_SUCCESS) {
        LOGE("Native[%d]: Failed to get stream ctrl (%s), retrying...", fd, uvc_strerror(res));
        uvc_lock.unlock();
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
        uvc_lock.lock();
        if (ctx->cleaned_up || !ctx->devh) return JNI_FALSE;
        res = negotiate_mjpeg_stream_ctrl(ctx->devh, &ctrl, fd, width, height, fps);
    }

    if (res == UVC_SUCCESS) {
        res = uvc_start_streaming(ctx->devh, &ctrl, frame_callback, (void*)ctx.get(), 0);
        if (res == UVC_SUCCESS) {
            LOGI("Native[%d]: Streaming started at %dx%d@%d", fd, width, height, fps);
            return JNI_TRUE;
        } else {
            LOGE("Native[%d]: Failed to start streaming: %s", fd, uvc_strerror(res));
        }
    } else {
        LOGE("Native[%d]: Final fail to get stream ctrl: %s", fd, uvc_strerror(res));
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_net_d7z_net_oss_uvc_UvcStreamingService_stopUVC(JNIEnv *env, jobject thiz, jint fd) {
    auto ctx = get_camera_context(fd);
    if (ctx) {
        std::lock_guard<std::mutex> lock(ctx->uvc_mutex);
        if (ctx->cleaned_up || !ctx->devh) return;
        LOGI("Native[%d]: stopUVC requested", fd);
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
        }
    }
    
    if (target_ctx) {
        LOGI("Native[%d]: releaseUVC requested", fd);
        target_ctx->cleanup(env);
        std::lock_guard<std::recursive_mutex> buffer_lock(target_ctx->buffer_mutex);
        {
            std::lock_guard<std::mutex> lock(g_map_mutex);
            g_camera_map.erase(fd);
        }
    }

}
