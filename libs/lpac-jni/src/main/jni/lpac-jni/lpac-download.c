#include <euicc/es9p.h>
#include <euicc/es10b.h>
#include <stdlib.h>
#include <string.h>
#include <syslog.h>
#include "utils.h"
#include "lpac-download.h"

static jobject download_state_preparing;
static jobject download_state_connecting;
static jobject download_state_authenticating;
static jobject download_state_downloading;
static jobject download_state_finalizing;

#define BIND_DOWNLOAD_STATE_STATIC_FIELD(NAME, FIELD) \
    download_state_##NAME = bind_static_field(env, download_state_class, FIELD, "L" DOWNLOAD_STATE_CLASS ";")

void lpac_download_init(JNIEnv *env) {
    jclass download_state_class = (*env)->FindClass(env, DOWNLOAD_STATE_CLASS);

    BIND_DOWNLOAD_STATE_STATIC_FIELD(preparing, "Preparing");
    BIND_DOWNLOAD_STATE_STATIC_FIELD(connecting, "Connecting");
    BIND_DOWNLOAD_STATE_STATIC_FIELD(authenticating, "Authenticating");
    BIND_DOWNLOAD_STATE_STATIC_FIELD(downloading, "Downloading");
    BIND_DOWNLOAD_STATE_STATIC_FIELD(finalizing, "Finalizing");
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_downloadProfile(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jstring smdp,
        jstring matching_id,
        jstring imei,
        jstring confirmation_code,
        jobject callback
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10b_load_bound_profile_package_result es10b_load_bound_profile_package_result;
    struct es8p_metadata *profile_metadata = NULL;
    const char *_confirmation_code = NULL;
    const char *_matching_id = NULL;
    const char *_smdp = NULL;
    const char *_imei = NULL;
    jobject bound_profile_metadata = NULL;
    int ret;

    jclass callback_class = (*env)->GetObjectClass(env, callback);
    jmethodID on_state_update = (*env)->GetMethodID(env, callback_class, "onStateUpdate", "(L" DOWNLOAD_STATE_CLASS ";)V");
    jmethodID on_profile_metadata = (*env)->GetMethodID(env, callback_class, "onProfileMetadata", "(L" PROFILE_METADATA_CLASS ";)V");
    jmethodID is_cancelled = (*env)->GetMethodID(env, callback_class, "isCancelled", "()Z");
    jmethodID set_cancelled = (*env)->GetMethodID(env, callback_class, "setCancelled", "(Z)V");

#define IS_CANCELLED() (*env)->CallBooleanMethod(env, callback, is_cancelled)
#define CHECK_INVOKE_RESULT(COND) if (COND) { ret = -ES10B_ERROR_REASON_UNDEFINED; goto out; }
#define EMIT_STATE_UPDATE(STATE) (*env)->CallVoidMethod(env, callback, on_state_update, STATE)

    if (confirmation_code != NULL)
        _confirmation_code = (*env)->GetStringUTFChars(env, confirmation_code, NULL);
    if (matching_id != NULL)
        _matching_id = (*env)->GetStringUTFChars(env, matching_id, NULL);
    _smdp = (*env)->GetStringUTFChars(env, smdp, NULL);
    if (imei != NULL)
        _imei = (*env)->GetStringUTFChars(env, imei, NULL);

    ctx->http.server_address = _smdp;

    // region preparing
    CHECK_INVOKE_RESULT(IS_CANCELLED())
    EMIT_STATE_UPDATE(download_state_preparing);
    ret = es10b_get_euicc_challenge_and_info(ctx);
    syslog(LOG_INFO, "es10b_get_euicc_challenge_and_info %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)
    // endregion

    // region connecting
    CHECK_INVOKE_RESULT(IS_CANCELLED())
    EMIT_STATE_UPDATE(download_state_connecting);
    ret = es9p_initiate_authentication(ctx);
    syslog(LOG_INFO, "es9p_initiate_authentication %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)
    // endregion

    // region authenticating
    CHECK_INVOKE_RESULT(IS_CANCELLED())
    EMIT_STATE_UPDATE(download_state_authenticating);
    ret = es10b_authenticate_server(ctx, _matching_id, _imei);
    syslog(LOG_INFO, "es10b_authenticate_server %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)

    CHECK_INVOKE_RESULT(IS_CANCELLED())
    ret = es9p_authenticate_client(ctx);
    syslog(LOG_INFO, "es9p_authenticate_client %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)
    // endregion

    // region emit profile metadata
    const char *b64_profileMetadata = ctx->http._internal.prepare_download_param->b64_profileMetadata;
    if (b64_profileMetadata != NULL) {
        ret = es8p_metadata_parse(&profile_metadata, b64_profileMetadata);
        if (ret < 0) {
            ret = -ES10B_ERROR_REASON_UNDEFINED;
            goto out;
        }
        bound_profile_metadata = build_profile_metadata(env, profile_metadata);
        (*env)->CallVoidMethod(env, callback, on_profile_metadata, bound_profile_metadata);
        CHECK_INVOKE_RESULT((*env)->ExceptionCheck(env) == JNI_TRUE)
    }
    // endregion

    // region downloading
    CHECK_INVOKE_RESULT(IS_CANCELLED())
    EMIT_STATE_UPDATE(download_state_downloading);
    ret = es10b_prepare_download(ctx, _confirmation_code);
    syslog(LOG_INFO, "es10b_prepare_download %d", ret);
    CHECK_INVOKE_RESULT(ret < 0)

    CHECK_INVOKE_RESULT(IS_CANCELLED())
    ret = es9p_get_bound_profile_package(ctx);
    syslog(LOG_INFO, "es9p_get_bound_profile_package %d", ret);
    if (ret < 0) goto out;
    // endregion

    // region finalizing
    CHECK_INVOKE_RESULT(IS_CANCELLED())
    EMIT_STATE_UPDATE(download_state_finalizing);
    ret = es10b_load_bound_profile_package(ctx, &es10b_load_bound_profile_package_result);
    syslog(LOG_INFO, "es10b_load_bound_profile_package %d, reason %d", ret,
           es10b_load_bound_profile_package_result.errorReason);
    if (ret < 0) {
        ret = -(int) es10b_load_bound_profile_package_result.errorReason;
        goto out;
    }
    // endregion

    euicc_http_cleanup(ctx);

    out:
    // isCancelled() == false, but ret is an error, the set to cancelled
    if (IS_CANCELLED() == 0 && ret == -ES10B_ERROR_REASON_UNDEFINED) {
        (*env)->CallVoidMethod(env, callback, set_cancelled, JNI_TRUE);
    }
    es8p_metadata_free(&profile_metadata);

#undef IS_CANCELLED
#undef CHECK_INVOKE_RESULT
#undef EMIT_STATE_UPDATE
    if (bound_profile_metadata != NULL)
        (*env)->DeleteLocalRef(env, bound_profile_metadata);
    // We expect Java side to call cancelSessions after any error -- thus, `euicc_http_cleanup` is done there
    // This is so that Java side can access the last HTTP and/or APDU errors when we return.
    if (_confirmation_code != NULL)
        (*env)->ReleaseStringUTFChars(env, confirmation_code, _confirmation_code);
    if (_matching_id != NULL)
        (*env)->ReleaseStringUTFChars(env, matching_id, _matching_id);
    (*env)->ReleaseStringUTFChars(env, smdp, _smdp);
    if (_imei != NULL)
        (*env)->ReleaseStringUTFChars(env, imei, _imei);
    return ret;
}

JNIEXPORT void JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_cancelSessions(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    es9p_cancel_session(ctx);
    es10b_cancel_session(ctx, ES10B_CANCEL_SESSION_REASON_UNDEFINED);
    euicc_http_cleanup(ctx);
}

#define ERRCODE_ENUM_TO_STRING(VARIANT) case VARIANT: return toJString(env, #VARIANT)

JNIEXPORT jstring JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_downloadErrCodeToString(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jint code
) {
    switch (code) {
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INCORRECT_INPUT_VALUES);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INVALID_SIGNATURE);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INVALID_TRANSACTION_ID);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_UNSUPPORTED_CRT_VALUES);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_UNSUPPORTED_REMOTE_OPERATION_TYPE);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_UNSUPPORTED_PROFILE_CLASS);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_SCP03T_STRUCTURE_ERROR);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_SCP03T_SECURITY_ERROR);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INSTALL_FAILED_DUE_TO_ICCID_ALREADY_EXISTS_ON_EUICC);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INSTALL_FAILED_DUE_TO_INSUFFICIENT_MEMORY_FOR_PROFILE);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INSTALL_FAILED_DUE_TO_INTERRUPTION);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INSTALL_FAILED_DUE_TO_PE_PROCESSING_ERROR);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INSTALL_FAILED_DUE_TO_ICCID_MISMATCH);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_TEST_PROFILE_INSTALL_FAILED_DUE_TO_INVALID_NAA_KEY);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_PPR_NOT_ALLOWED);
        ERRCODE_ENUM_TO_STRING(ES10B_ERROR_REASON_INSTALL_FAILED_DUE_TO_UNKNOWN_ERROR);
        default:
            return toJString(env, "ES10B_ERROR_REASON_UNDEFINED");
    }
}