#include <jni.h>

#include <mpv/client.h>

#include "jni_utils.h"
#include "log.h"
#include "globals.h"
#include "mpv_context.h"

extern "C" {
    jni_func(void, attachSurface, jobject surface_);
    jni_func(void, detachSurface);
};

jni_func(void, attachSurface, jobject surface_) {
    MpvContext *ctx = get_context(env, obj);
    CHECK_CTX(ctx);

    ctx->surface = env->NewGlobalRef(surface_);
    if (!ctx->surface)
        die("invalid surface provided");
    int64_t wid = reinterpret_cast<intptr_t>(ctx->surface);
    int result = mpv_set_option(ctx->mpv, "wid", MPV_FORMAT_INT64, &wid);
    if (result < 0)
         ALOGE("mpv_set_option(wid) returned error %s", mpv_error_string(result));
}

jni_func(void, detachSurface) {
    MpvContext *ctx = get_context(env, obj);
    CHECK_CTX(ctx);

    int64_t wid = 0;
    int result = mpv_set_option(ctx->mpv, "wid", MPV_FORMAT_INT64, &wid);
    if (result < 0)
         ALOGE("mpv_set_option(wid) returned error %s", mpv_error_string(result));

    if (ctx->surface) {
        env->DeleteGlobalRef(ctx->surface);
        ctx->surface = NULL;
    }
}
