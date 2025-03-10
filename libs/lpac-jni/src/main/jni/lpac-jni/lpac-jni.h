#pragma once

#include <euicc/euicc.h>
#include <pthread.h>
#include <jni.h>

_Static_assert(sizeof(void *) <= sizeof(jlong),
               "jlong must be big enough to hold a platform raw pointer");

struct lpac_jni_ctx {
    jint logical_channel_id;
    jobject apdu_interface;
    jobject http_interface;
};

#define LPAC_JNI_CTX(ctx) ((struct lpac_jni_ctx *) ctx->userdata)
#define LPAC_JNI_SETUP_ENV \
    JNIEnv *env; \
    (*jvm)->AttachCurrentThread(jvm, &env, NULL)

extern JavaVM *jvm;

