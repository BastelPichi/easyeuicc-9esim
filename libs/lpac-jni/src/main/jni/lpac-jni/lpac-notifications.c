#include "lpac-notifications.h"
#include <euicc/es9p.h>
#include <euicc/es10b.h>
#include <malloc.h>
#include <syslog.h>

jclass local_profile_notification_class;

jobject profile_management_operation_install;
jobject profile_management_operation_enable;
jobject profile_management_operation_disable;
jobject profile_management_operation_delete;
jobject profile_management_operation_unknown;

jmethodID local_profile_notification_constructor;

#define LOCAL_PROFILE_NOTIFICATION_CLASS "net/typeblog/lpac_jni/LocalProfileNotification"
#define PROFILE_MANAGEMENT_OPERATION_CLASS "net/typeblog/lpac_jni/ProfileManagementOperation"

static jobject bind_static_field(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
    jfieldID field = (*env)->GetStaticFieldID(env, clazz, name, sig);
    jobject bound = (*env)->GetStaticObjectField(env, clazz, field);
    return (*env)->NewGlobalRef(env, bound);
}

#define BIND_NOTIFICATION_OPERATION_FIELD(NAME, FIELD) \
    profile_management_operation_##NAME = bind_static_field( \
        env, profile_management_operation_class, FIELD, "L" PROFILE_MANAGEMENT_OPERATION_CLASS ";")

void lpac_notifications_init(JNIEnv *env) {
    local_profile_notification_class = (*env)->FindClass(env, LOCAL_PROFILE_NOTIFICATION_CLASS);
    local_profile_notification_class = (*env)->NewGlobalRef(env, local_profile_notification_class);
    local_profile_notification_constructor = (*env)->GetMethodID(
            env, local_profile_notification_class, "<init>",
            "("
            "J"                                        // seqNumber
            "L" PROFILE_MANAGEMENT_OPERATION_CLASS ";" // profileManagementOperation
            "Ljava/lang/String;"                       // notificationAddress
            "Ljava/lang/String;"                       // iccid
            ")"
            "V"                                        // (returns) void
    );

    jclass profile_management_operation_class = (*env)->FindClass(
            env, PROFILE_MANAGEMENT_OPERATION_CLASS);
    profile_management_operation_class = (*env)->NewGlobalRef(
            env, profile_management_operation_class);

    BIND_NOTIFICATION_OPERATION_FIELD(install, "Install");
    BIND_NOTIFICATION_OPERATION_FIELD(delete, "Delete");
    BIND_NOTIFICATION_OPERATION_FIELD(enable, "Enable");
    BIND_NOTIFICATION_OPERATION_FIELD(disable, "Disable");
    BIND_NOTIFICATION_OPERATION_FIELD(unknown, "Unknown");
}

#undef BIND_NOTIFICATION_OPERATION_FIELD

static jobject to_profile_management_operation(enum es10b_profile_management_operation operation) {
    switch (operation) {
        case ES10B_PROFILE_MANAGEMENT_OPERATION_INSTALL:
            return profile_management_operation_install;
        case ES10B_PROFILE_MANAGEMENT_OPERATION_DELETE:
            return profile_management_operation_delete;
        case ES10B_PROFILE_MANAGEMENT_OPERATION_ENABLE:
            return profile_management_operation_enable;
        case ES10B_PROFILE_MANAGEMENT_OPERATION_DISABLE:
            return profile_management_operation_disable;
        default:
            return profile_management_operation_unknown;
    }
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10bListNotification(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jobject notifications
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10b_notification_metadata_list *metadata = NULL;
    int ret = es10b_list_notification(ctx, &metadata);
    if (ret < 0) {
        goto out;
    }

    jmethodID add_notification = (*env)->GetMethodID(
            env,
            (*env)->GetObjectClass(env, notifications),
            "add", "(Ljava/lang/Object;)Z"
    );

    jobject element;
    while (metadata) {
        jlong sequence_number = metadata->seqNumber;
        jobject operation = to_profile_management_operation(metadata->profileManagementOperation);
        jstring address = toJString(env, metadata->notificationAddress);
        jstring iccid = toJString(env, metadata->iccid);
        element = (*env)->NewObject(
                env, local_profile_notification_class, local_profile_notification_constructor,
                sequence_number, operation, address, iccid);
        (*env)->CallBooleanMethod(env, notifications, add_notification, element);
        metadata = metadata->next;
    }
    out:
    es10b_notification_metadata_list_free_all(metadata);
    return ret;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_handleNotification(JNIEnv *env, jobject thiz, jlong handle,
                                                       jlong seq_number) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10b_pending_notification notification;
    int res;

    res = es10b_retrieve_notifications_list(ctx, &notification, (unsigned long) seq_number);
    syslog(LOG_DEBUG, "es10b_retrieve_notification = %d %s", res, notification.b64_PendingNotification);
    if (res < 0)
        goto out;

    ctx->http.server_address = notification.notificationAddress;

    res = es9p_handle_notification(ctx, notification.b64_PendingNotification);
    syslog(LOG_DEBUG, "es9p_handle_notification = %d", res);
    if (res < 0)
        goto out;

    out:
    euicc_http_cleanup(ctx);
    return res;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10bDeleteNotification(JNIEnv *env, jobject thiz, jlong handle,
                                                            jlong seq_number) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    return es10b_remove_notification_from_list(ctx, (unsigned long) seq_number);
}
