#include <euicc/es10a.h>
#include <euicc/es10b.h>
#include <euicc/es9p.h>
#include <string.h>
#include <malloc.h>
#include <syslog.h>
#include "utils.h"
#include "lpac-discovery.h"

JNIEXPORT jobject JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10aGetEuiccConfiguredAddresses(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10a_euicc_configured_addresses addresses;
    jobject ret = NULL;
    if (es10a_get_euicc_configured_addresses(ctx, &addresses) < 0) {
        goto out;
    }
    jclass configured_addresses_class = (*env)->FindClass(env, EUICC_CONFIGURED_ADDRESSES_CLASS);
    jmethodID configured_addresses_constructor = (*env)->GetMethodID(
            env, configured_addresses_class, "<init>",
            "("
            "Ljava/lang/String;" // default dp address
            "Ljava/lang/String;" // root ds address
            ")"
            "V" // (returns) void
    );
    ret = (*env)->NewObject(
            env, configured_addresses_class,
            configured_addresses_constructor,
            toJString(env, addresses.defaultDpAddress),
            toJString(env, addresses.rootDsAddress)
    );
    out:
    es10a_euicc_configured_addresses_free(&addresses);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_discoveryProfile(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jstring address,
        jstring imei,
        jobject callback
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;

    jclass callback_class = (*env)->GetObjectClass(env, callback);
    jmethodID on_discovered = (*env)->GetMethodID(env, callback_class, "onDiscovered",
                                                  "(Ljava/util/List;)V");

    const char *_address = (*env)->GetStringUTFChars(env, address, NULL);
    const char *_imei = NULL;

    if (imei != NULL) {
        _imei = (*env)->GetStringUTFChars(env, address, NULL);
    }

    ctx->http.server_address = _address;

    char **smdp_list = NULL;
    int ret;

#define CHECK_INVOKE_RESULT(COND) if (COND) { ret = -ES10B_ERROR_REASON_UNDEFINED; goto out; }

    // region preparing
    ret = es10b_get_euicc_challenge_and_info(ctx);
    syslog(LOG_INFO, "es10b_get_euicc_challenge_and_info %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)
    // endregion

    // region connecting
    ret = es9p_initiate_authentication(ctx);
    syslog(LOG_INFO, "es9p_initiate_authentication %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)
    // endregion

    // region authenticating
    ret = es10b_authenticate_server(ctx, NULL, _imei);
    syslog(LOG_INFO, "es10b_authenticate_server %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)

    ret = es11_authenticate_client(ctx, &smdp_list);
    syslog(LOG_INFO, "es11_authenticate_client %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)
    // endregion

#undef CHECK_INVOKE_RESULT

    (*env)->CallVoidMethod(env, callback, on_discovered, to_string_list(env, smdp_list));

    out:

    if (_imei != NULL) (*env)->ReleaseStringUTFChars(env, imei, _imei);
    (*env)->ReleaseStringUTFChars(env, address, _address);
    es11_smdp_list_free_all(smdp_list);
    return ret;
}