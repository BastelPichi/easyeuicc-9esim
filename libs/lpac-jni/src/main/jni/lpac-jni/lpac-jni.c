#include <euicc/euicc.h>
#include <euicc/es10c.h>
#include <euicc/es10c_ex.h>
#include <euicc/interface.h>
#include <malloc.h>
#include <string.h>
#include <syslog.h>
#include "lpac-jni.h"
#include "lpac-download.h"
#include "lpac-notifications.h"
#include "lpac-discovery.h"
#include "utils.h"
#include "interface-wrapper.h"

JavaVM *jvm = NULL;

#define LOCAL_PROFILE_INFO_CLASS "net/typeblog/lpac_jni/LocalProfileInfo"

jint JNI_OnLoad(JavaVM *vm, __attribute__((unused)) void *reserved) {
    jvm = vm;
    LPAC_JNI_SETUP_ENV;

    interface_wrapper_init(env);
    lpac_convertor_init(env);
    lpac_download_init(env);

    return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_createContext(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jbyteArray isdr_aid,
        jobject apdu_interface,
        jobject http_interface
) {
    struct lpac_jni_ctx *jni_ctx = NULL;
    struct euicc_ctx *ctx = NULL;
    jbyte *isdr_java = NULL;
    uint32_t isdr_len = 0;
    uint8_t *isdr_c = NULL;

    ctx = calloc(1, sizeof(struct euicc_ctx));
    jni_ctx = calloc(1, sizeof(struct lpac_jni_ctx));

    isdr_java = (*env)->GetByteArrayElements(env, isdr_aid, JNI_FALSE);
    isdr_len = (*env)->GetArrayLength(env, isdr_aid);
    isdr_c = calloc(isdr_len, sizeof(uint8_t));
    memcpy(isdr_c, isdr_java, isdr_len);
    (*env)->ReleaseByteArrayElements(env, isdr_aid, isdr_java, JNI_ABORT);

    ctx->apdu.interface = &lpac_jni_apdu_interface;
    ctx->http.interface = &lpac_jni_http_interface;
    jni_ctx->apdu_interface = (*env)->NewGlobalRef(env, apdu_interface);
    jni_ctx->http_interface = (*env)->NewGlobalRef(env, http_interface);
    ctx->aid = (const uint8_t *) isdr_c;
    ctx->aid_len = isdr_len;
    ctx->userdata = (void *) jni_ctx;
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_destroyContext(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct lpac_jni_ctx *jni_ctx = LPAC_JNI_CTX(ctx);

    (*env)->DeleteGlobalRef(env, jni_ctx->apdu_interface);
    (*env)->DeleteGlobalRef(env, jni_ctx->http_interface);
    free(jni_ctx);
    free((void *) ctx->aid);
    free(ctx);
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_euiccInit(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    return euicc_init(ctx);
}

JNIEXPORT void JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_euiccFini(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    euicc_fini(ctx);
}

JNIEXPORT void JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_euiccSetMss(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jbyte mss
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    ctx->es10x_mss = (uint8_t) mss;
}

JNIEXPORT jstring JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cGetEid(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    char *buf = NULL;

    if (es10c_get_eid(ctx, &buf) < 0) {
        return NULL;
    }
    jstring ret = toJString(env, buf);
    free(buf);
    return ret;
}

JNIEXPORT jobject JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cGetProfilesInfo(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10c_profile_info_list *info = NULL;
    jobject profile_list = new_array_list(env);
    int ret = es10c_get_profiles_info(ctx, &info);
    if (ret < 0) goto out;

    jclass profile_info_class = (*env)->FindClass(env, LOCAL_PROFILE_INFO_CLASS);
    jmethodID profile_info_class_constructor = (*env)->GetMethodID(
            env, profile_info_class, "<init>",
            "("
            "Ljava/lang/String;" // iccid
            "Lnet/typeblog/lpac_jni/ProfileState;"
            "Ljava/lang/String;" // name
            "Ljava/lang/String;" // nickname
            "Ljava/lang/String;" // provider name
            "Ljava/lang/String;" // ISD-P AID
            "Lnet/typeblog/lpac_jni/ProfileClass;"
            ")"
            "V" // (returns) void
    );

    jclass profile_list_class = (*env)->GetObjectClass(env, profile_list);
    jmethodID add_profile = (*env)->GetMethodID(env, profile_list_class, "add", "(Ljava/lang/Object;)Z");

    jobject element = NULL;
    while (info) {
        element = (*env)->NewObject(
                env, profile_info_class, profile_info_class_constructor,
                toJString(env, info->iccid),
                to_profile_state(info->profileState),
                toJString(env, info->profileName),
                toJString(env, info->profileNickname),
                toJString(env, info->serviceProviderName),
                toJString(env, info->isdpAid),
                to_profile_class(info->profileClass)
        );
        (*env)->CallBooleanMethod(env, profile_list, add_profile, element);
        info = info->next;
    }
    out:
    es10c_profile_info_list_free_all(info);
    return profile_list;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cEnableProfile(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jstring iccid,
        jboolean refresh
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    const char *_iccid = NULL;
    int ret;

    _iccid = (*env)->GetStringUTFChars(env, iccid, NULL);
    ret = es10c_enable_profile(ctx, _iccid, refresh ? 1 : 0);
    (*env)->ReleaseStringUTFChars(env, iccid, _iccid);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cDisableProfile(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jstring iccid,
        jboolean refresh
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    const char *_iccid = NULL;
    int ret;

    _iccid = (*env)->GetStringUTFChars(env, iccid, NULL);
    ret = es10c_disable_profile(ctx, _iccid, refresh ? 1 : 0);
    (*env)->ReleaseStringUTFChars(env, iccid, _iccid);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cSetNickname(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jstring iccid,
        jbyteArray nick
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    const char *_iccid = NULL;
    jbyte *_nick = NULL;
    int ret;

    _iccid = (*env)->GetStringUTFChars(env, iccid, NULL);
    _nick = (*env)->GetByteArrayElements(env, nick, NULL);
    ret = es10c_set_nickname(ctx, _iccid, (const char *) _nick);
    (*env)->ReleaseByteArrayElements(env, nick, _nick, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, iccid, _iccid);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cDeleteProfile(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jstring iccid
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    const char *_iccid = NULL;
    int ret;

    _iccid = (*env)->GetStringUTFChars(env, iccid, NULL);
    ret = es10c_delete_profile(ctx, _iccid);
    (*env)->ReleaseStringUTFChars(env, iccid, _iccid);
    return ret;
}

JNIEXPORT jobject JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cexGetEuiccInfo2(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10c_ex_euiccinfo2 *info = malloc(sizeof(struct es10c_ex_euiccinfo2));
    jobject ret = NULL;
    if (es10c_ex_get_euiccinfo2(ctx, info) < 0) goto out;
    jclass euicc_info_class = (*env)->FindClass(env, PACKAGE_NAME "/EuiccInfo2");
    jmethodID euicc_info_constructor = (*env)->GetMethodID(
            env, euicc_info_class, "<init>",
            "("
            "Lnet/typeblog/lpac_jni/Version;" // sgp22 version
            "Lnet/typeblog/lpac_jni/Version;" // profile version
            "Lnet/typeblog/lpac_jni/Version;" // euicc firmware version
            "Lnet/typeblog/lpac_jni/Version;" // global platform version
            "Ljava/lang/String;" // sas accreditation number
            "Lnet/typeblog/lpac_jni/Version;" // protected profile version
            "I" // freeNvram
            "I" // freeRam
            "Ljava/util/Set;" // euicc ci public id list (for signing)
            "Ljava/util/Set;" // euicc ci public id list (for verification)
            ")"
            "V" // (returns) void
    );

    ret = (*env)->NewObject(
            env, euicc_info_class, euicc_info_constructor,
            to_version(env, info->svn),
            to_version(env, info->profileVersion),
            to_version(env, info->euiccFirmwareVer),
            to_version(env, info->globalplatformVersion),
            toJString(env, info->sasAcreditationNumber),
            to_version(env, info->ppVersion),
            (jint) info->extCardResource.freeNonVolatileMemory,
            (jint) info->extCardResource.freeVolatileMemory,
            to_string_set(env, info->euiccCiPKIdListForSigning),
            to_string_set(env, info->euiccCiPKIdListForVerification)
    );

    out:
    es10c_ex_euiccinfo2_free(info);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cEuiccMemoryReset(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    return es10c_euicc_memory_reset(ctx);
}
