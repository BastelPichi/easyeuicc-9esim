#include "lpac-notifications.h"
#include <euicc/es10a.h>
#include <euicc/es10b.h>
#include <euicc/es9p.h>
#include <string.h>
#include <malloc.h>
#include <syslog.h>

jclass euicc_configured_addresses_class;
jmethodID euicc_configured_addresses_constructor;

jmethodID on_discovered;

#define EUICC_CONFIGURED_ADDRESSES_CLASS "net/typeblog/lpac_jni/EuiccConfiguredAddresses"
#define DISCOVERY_CALLBACK_CLASS "net/typeblog/lpac_jni/ProfileDiscoveryCallback"
#define STRING_CLASS "java/lang/String"

void lpac_discovery_init() {
    LPAC_JNI_SETUP_ENV;

    jclass download_callback_class = (*env)->FindClass(env, DISCOVERY_CALLBACK_CLASS);
    on_discovered = (*env)->GetMethodID(env, download_callback_class, "onDiscovered",
                                        "([L" STRING_CLASS ";)V");

    euicc_configured_addresses_class = (*env)->FindClass(env, EUICC_CONFIGURED_ADDRESSES_CLASS);
    euicc_configured_addresses_class = (*env)->NewGlobalRef(env, euicc_configured_addresses_class);
    euicc_configured_addresses_constructor = (*env)->GetMethodID(
            env, euicc_configured_addresses_class, "<init>",
            "(L" STRING_CLASS ";L" STRING_CLASS ";)V");
}

JNIEXPORT jobject JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10aGetEuiccConfiguredAddresses(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10a_euicc_configured_addresses addresses;
    jobject ret = NULL;
    if (es10a_get_euicc_configured_addresses(ctx, &addresses) == 0) {
        jstring default_dp_address = toJString(env, addresses.defaultDpAddress);
        jstring root_ds_address = toJString(env, addresses.rootDsAddress);
        ret = (*env)->NewObject(env, euicc_configured_addresses_class,
                                euicc_configured_addresses_constructor,
                                default_dp_address, root_ds_address);
    }
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

    const char *_address = (*env)->GetStringUTFChars(env, address, NULL);
    const char *_imei = NULL;

    if (imei != NULL) {
        _imei = (*env)->GetStringUTFChars(env, address, NULL);
    }

    ctx->http.server_address = _address;

    char **smdp_list = NULL;
    jobjectArray addresses = NULL;
    int ret = -1;

    ret = es10b_get_euicc_challenge_and_info(ctx);
    syslog(LOG_INFO, "es10b_get_euicc_challenge_and_info %d", ret);
    if (ret < 0) {
        ret = -ES10B_ERROR_REASON_UNDEFINED;
        goto out;
    }

    ret = es9p_initiate_authentication(ctx);
    syslog(LOG_INFO, "es9p_initiate_authentication %d", ret);
    if (ret < 0) {
        ret = -ES10B_ERROR_REASON_UNDEFINED;
        goto out;
    }

    ret = es10b_authenticate_server(ctx, NULL, _imei);
    syslog(LOG_INFO, "es10b_authenticate_server %d", ret);
    if (ret < 0) {
        ret = -ES10B_ERROR_REASON_UNDEFINED;
        goto out;
    }

    ret = es11_authenticate_client(ctx, &smdp_list);
    syslog(LOG_INFO, "es11_authenticate_client %d", ret);
    if (ret < 0) {
        ret = -ES10B_ERROR_REASON_UNDEFINED;
        goto out;
    }

    jsize n = 0;
    for (n = 0; smdp_list[n] != NULL; n++) continue;

    addresses = (*env)->NewObjectArray(env, n, string_class, NULL);

    for (jsize index = 0; index < n; index++) {
        jstring element = toJString(env, smdp_list[index]);
        (*env)->SetObjectArrayElement(env, addresses, index, element);
    }

    (*env)->CallVoidMethod(env, callback, on_discovered, addresses);

    out:
    if (_imei != NULL) (*env)->ReleaseStringUTFChars(env, imei, _imei);
    (*env)->ReleaseStringUTFChars(env, address, _address);
    es11_smdp_list_free_all(smdp_list);
    return ret;
}