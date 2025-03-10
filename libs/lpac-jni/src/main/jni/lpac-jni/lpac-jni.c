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
#include "interface-wrapper.h"
#include "lpac-convertor.h"

JavaVM *jvm = NULL;

jstring empty_string;

jclass string_class;
jmethodID string_constructor;

#define LOCAL_PROFILE_INFO_CLASS "net/typeblog/lpac_jni/LocalProfileInfo"

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    jvm = vm;
    LPAC_JNI_SETUP_ENV;

    interface_wrapper_init();
    lpac_convertor_init(env);
    lpac_download_init();

    string_class = (*env)->FindClass(env, "java/lang/String");
    string_class = (*env)->NewGlobalRef(env, string_class);
    string_constructor = (*env)->GetMethodID(env, string_class, "<init>",
                                             "([BLjava/lang/String;)V");

    const jchar _unused[1];
    empty_string = (*env)->NewString(env, _unused, 0);
    empty_string = (*env)->NewGlobalRef(env, empty_string);

    return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_createContext(JNIEnv *env, jobject thiz,
                                                  jbyteArray isdr_aid,
                                                  jobject apdu_interface,
                                                  jobject http_interface) {
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
Java_net_typeblog_lpac_1jni_LpacJni_destroyContext(JNIEnv *env, jobject thiz, jlong handle) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct lpac_jni_ctx *jni_ctx = LPAC_JNI_CTX(ctx);

    (*env)->DeleteGlobalRef(env, jni_ctx->apdu_interface);
    (*env)->DeleteGlobalRef(env, jni_ctx->http_interface);
    free(jni_ctx);
    free((void *) ctx->aid);
    free(ctx);
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_euiccInit(JNIEnv *env, jobject thiz, jlong handle) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    return euicc_init(ctx);
}

JNIEXPORT void JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_euiccFini(JNIEnv *env, jobject thiz, jlong handle) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    euicc_fini(ctx);
}

JNIEXPORT void JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_euiccSetMss(JNIEnv *env, jobject thiz, jlong handle,
                                                jbyte mss) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    ctx->es10x_mss = (uint8_t) mss;
}

jstring toJString(JNIEnv *env, const char *pat) {
    jbyteArray bytes = NULL;
    jstring encoding = NULL;
    jstring jstr = NULL;
    jsize len;

    if (pat == NULL)
        return (*env)->NewLocalRef(env, empty_string);

    len = (jsize) strlen(pat);
    bytes = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (jbyte *) pat);
    encoding = (*env)->NewStringUTF(env, "utf-8");
    jstr = (jstring) (*env)->NewObject(env, string_class,
                                       string_constructor, bytes, encoding);
    (*env)->DeleteLocalRef(env, encoding);
    (*env)->DeleteLocalRef(env, bytes);
    return jstr;
}

JNIEXPORT jstring JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cGetEid(JNIEnv *env, jobject thiz, jlong handle) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    char *buf = NULL;

    if (es10c_get_eid(ctx, &buf) < 0) {
        return NULL;
    }
    jstring ret = toJString(env, buf);
    free(buf);
    return ret;
}

JNIEXPORT jlong JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cGetProfilesInfo(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jobject profiles
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10c_profile_info_list *info = NULL;
    int ret = es10c_get_profiles_info(ctx, &info);
    if (ret < 0) return ret;

    jclass profile_list_class = (*env)->GetObjectClass(env, profiles);
    jmethodID add_profile = (*env)->GetMethodID(env, profile_list_class, "add",
                                                "(Ljava/lang/Object;)Z");

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
        (*env)->CallBooleanMethod(env, profiles, add_profile, element);
        info = info->next;
    }

    es10c_profile_info_list_free_all(info);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cEnableProfile(JNIEnv *env, jobject thiz, jlong handle,
                                                       jstring iccid, jboolean refresh) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    const char *_iccid = NULL;
    int ret;

    _iccid = (*env)->GetStringUTFChars(env, iccid, NULL);
    ret = es10c_enable_profile(ctx, _iccid, refresh ? 1 : 0);
    (*env)->ReleaseStringUTFChars(env, iccid, _iccid);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cDisableProfile(JNIEnv *env, jobject thiz, jlong handle,
                                                        jstring iccid, jboolean refresh) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    const char *_iccid = NULL;
    int ret;

    _iccid = (*env)->GetStringUTFChars(env, iccid, NULL);
    ret = es10c_disable_profile(ctx, _iccid, refresh ? 1 : 0);
    (*env)->ReleaseStringUTFChars(env, iccid, _iccid);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cSetNickname(JNIEnv *env, jobject thiz, jlong handle,
                                                     jstring iccid, jbyteArray nick) {
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
Java_net_typeblog_lpac_1jni_LpacJni_es10cDeleteProfile(JNIEnv *env, jobject thiz, jlong handle,
                                                       jstring iccid) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    const char *_iccid = NULL;
    int ret;

    _iccid = (*env)->GetStringUTFChars(env, iccid, NULL);
    ret = es10c_delete_profile(ctx, _iccid);
    (*env)->ReleaseStringUTFChars(env, iccid, _iccid);
    return ret;
}

JNIEXPORT jlong JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cexGetEuiccInfo2(JNIEnv *env, jobject thiz, jlong handle) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10c_ex_euiccinfo2 *info = malloc(sizeof(struct es10c_ex_euiccinfo2));

    if (es10c_ex_get_euiccinfo2(ctx, info) < 0) {
        free(info);
        return 0;
    }

    return (jlong) info;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10cEuiccMemoryReset(JNIEnv *env, jobject thiz, jlong handle) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    int ret;
    ret = es10c_euicc_memory_reset(ctx);
    return ret;
}

JNIEXPORT jstring JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_stringDeref(JNIEnv *env, jobject thiz, jlong curr) {
    return toJString(env, *((char **) curr));
}

void lpac_jni_euiccinfo2_free(struct es10c_ex_euiccinfo2 *info) {
    es10c_ex_euiccinfo2_free(info);
    free(info);
}

LPAC_JNI_STRUCT_GETTER_NULL_TERM_LIST_NEXT(char*, stringArr)
LPAC_JNI_STRUCT_FREE(struct es10c_ex_euiccinfo2, euiccInfo2, lpac_jni_euiccinfo2_free)
LPAC_JNI_STRUCT_GETTER_STRING(struct es10c_ex_euiccinfo2, euiccInfo2, svn, SGP22Version)
LPAC_JNI_STRUCT_GETTER_STRING(struct es10c_ex_euiccinfo2, euiccInfo2, profileVersion, ProfileVersion)
LPAC_JNI_STRUCT_GETTER_STRING(struct es10c_ex_euiccinfo2, euiccInfo2, euiccFirmwareVer, EuiccFirmwareVersion)
LPAC_JNI_STRUCT_GETTER_STRING(struct es10c_ex_euiccinfo2, euiccInfo2, globalplatformVersion, GlobalPlatformVersion)
LPAC_JNI_STRUCT_GETTER_STRING(struct es10c_ex_euiccinfo2, euiccInfo2, sasAcreditationNumber, SasAcreditationNumber)
LPAC_JNI_STRUCT_GETTER_STRING(struct es10c_ex_euiccinfo2, euiccInfo2, ppVersion, PpVersion)
LPAC_JNI_STRUCT_GETTER_LONG(struct es10c_ex_euiccinfo2, euiccInfo2, extCardResource.freeNonVolatileMemory, FreeNonVolatileMemory)
LPAC_JNI_STRUCT_GETTER_LONG(struct es10c_ex_euiccinfo2, euiccInfo2, extCardResource.freeVolatileMemory, FreeVolatileMemory)

LPAC_JNI_STRUCT_GETTER_LONG(struct es10c_ex_euiccinfo2, euiccInfo2, euiccCiPKIdListForSigning, EuiccCiPKIdListForSigning)
LPAC_JNI_STRUCT_GETTER_LONG(struct es10c_ex_euiccinfo2, euiccInfo2, euiccCiPKIdListForVerification, EuiccCiPKIdListForVerification)
