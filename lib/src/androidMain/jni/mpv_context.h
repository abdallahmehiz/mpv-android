#pragma once

#include <jni.h>
#include <mpv/client.h>
#include <pthread.h>
#include <atomic>

struct MpvContext {
    mpv_handle *mpv;
    std::atomic<bool> event_thread_request_exit;
    pthread_t event_thread_id;
    jobject surface;
    // java instance reference
    jobject java_instance;

    MpvContext() : mpv(nullptr), event_thread_request_exit(false),
                   event_thread_id(0), surface(nullptr), java_instance(nullptr) {}
};

MpvContext* get_context(JNIEnv *env, jobject obj);

void set_context(JNIEnv *env, jobject obj, MpvContext *ctx);

#define CHECK_CTX(ctx) do { \
    if (__builtin_expect(!(ctx) || !(ctx)->mpv, 0)) \
        die("libmpv is not initialized"); \
    } while (0)

