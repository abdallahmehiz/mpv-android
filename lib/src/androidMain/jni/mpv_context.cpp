#define MPV_CONTEXT_IMPL
#include "mpv_context.h"
#include "jni_utils.h"
#include "log.h"

static jfieldID nativeHandleField = nullptr;

void init_native_handle_field(JNIEnv *env, jobject obj) {
    if (nativeHandleField == nullptr) {
        jclass clazz = env->GetObjectClass(obj);
        if (!clazz) die("Failed to get MPV class from object");
        nativeHandleField = env->GetFieldID(clazz, "nativeHandle", "J");
        env->DeleteLocalRef(clazz);
        if (!nativeHandleField) die("Failed to get nativeHandle field ID");
    }
}

MpvContext* get_context(JNIEnv *env, jobject obj) {
    init_native_handle_field(env, obj);
    jlong handle = env->GetLongField(obj, nativeHandleField);
    return reinterpret_cast<MpvContext*>(handle);
}

void set_context(JNIEnv *env, jobject obj, MpvContext *ctx) {
    init_native_handle_field(env, obj);
    env->SetLongField(obj, nativeHandleField, reinterpret_cast<jlong>(ctx));
}

