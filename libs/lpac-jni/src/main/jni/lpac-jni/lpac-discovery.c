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

void lpac_discovery_init(JNIEnv *env) {
    jclass download_callback_class = (*env)->FindClass(env, DISCOVERY_CALLBACK_CLASS);
    on_discovered = (*env)->GetMethodID(env, download_callback_class, "onDiscovered",
                                        "(Ljava/util/ArrayList;)V");

    euicc_configured_addresses_class = (*env)->FindClass(env, EUICC_CONFIGURED_ADDRESSES_CLASS);
    euicc_configured_addresses_class = (*env)->NewGlobalRef(env, euicc_configured_addresses_class);
    euicc_configured_addresses_constructor = (*env)->GetMethodID(
            env, euicc_configured_addresses_class, "<init>",
            "(Ljava/lang/String;Ljava/lang/String;)V");
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
    if (es10a_get_euicc_configured_addresses(ctx, &addresses) < 0) {
        goto out;
    }
    jstring default_dp_address = toJString(env, addresses.defaultDpAddress);
    jstring root_ds_address = toJString(env, addresses.rootDsAddress);
    ret = (*env)->NewObject(env, euicc_configured_addresses_class,
                            euicc_configured_addresses_constructor,
                            default_dp_address, root_ds_address);
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

    const char *_address = (*env)->GetStringUTFChars(env, address, NULL);
    const char *_imei = NULL;

    if (imei != NULL) {
        _imei = (*env)->GetStringUTFChars(env, address, NULL);
    }

    ctx->http.server_address = _address;

    char **smdp_list = NULL;
    jobjectArray addresses = NULL;
    jclass array_list_class = NULL;
    jmethodID array_list_constructor = NULL;
    jmethodID add_element = NULL;

    int ret;

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

    array_list_class = (*env)->FindClass(env, "java/util/ArrayList");
    array_list_constructor = (*env)->GetMethodID(env, array_list_class, "<init>", "()V");
    add_element = (*env)->GetMethodID(env, array_list_class, "add", "(Ljava/lang/Object;)Z");

    addresses = (*env)->NewObject(env, array_list_class, array_list_constructor);

    for (jsize index = 0; smdp_list[index] != NULL; index++) {
        jstring element = toJString(env, smdp_list[index]);
        (*env)->CallBooleanMethod(env, addresses, add_element, element);
    }

    (*env)->CallVoidMethod(env, callback, on_discovered, addresses);

    out:
    if (array_list_class != NULL) (*env)->DeleteLocalRef(env, array_list_class);
    if (array_list_constructor != NULL) (*env)->DeleteLocalRef(env, array_list_constructor);
    if (add_element != NULL) (*env)->DeleteLocalRef(env, add_element);

    if (_imei != NULL) (*env)->ReleaseStringUTFChars(env, imei, _imei);
    (*env)->ReleaseStringUTFChars(env, address, _address);
    es11_smdp_list_free_all(smdp_list);
    return ret;
}