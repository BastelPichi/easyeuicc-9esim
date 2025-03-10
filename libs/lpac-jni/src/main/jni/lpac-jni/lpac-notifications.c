#include <euicc/es9p.h>
#include <euicc/es10b.h>
#include <malloc.h>
#include <syslog.h>
#include "utils.h"
#include "lpac-notifications.h"

JNIEXPORT jobject JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_es10bListNotification(
        JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    struct es10b_notification_metadata_list *metadata = NULL;
    jobject notification_list = new_array_list(env);
    int ret = es10b_list_notification(ctx, &metadata);
    if (ret < 0) goto out;

    jclass local_profile_notification_class = (*env)->FindClass(
            env, LOCAL_PROFILE_NOTIFICATION_CLASS);
    jmethodID local_profile_notification_constructor = (*env)->GetMethodID(
            env, local_profile_notification_class, "<init>",
            "("
            "J" // seqNumber
            "L" PROFILE_MANAGEMENT_OPERATION_CLASS ";"
            "Ljava/lang/String;" // notificationAddress
            "Ljava/lang/String;" // iccid
            ")"
            "V" // (returns) void
    );

    jclass notification_list_class = (*env)->GetObjectClass(env, notification_list);
    jmethodID add_notification = (*env)->GetMethodID(env, notification_list_class, "add", "(Ljava/lang/Object;)Z");

    jobject element;
    while (metadata) {
        element = (*env)->NewObject(
                env, local_profile_notification_class, local_profile_notification_constructor,
                (jlong) metadata->seqNumber,
                to_profile_management_operation(metadata->profileManagementOperation),
                toJString(env, metadata->notificationAddress),
                toJString(env, metadata->iccid)
        );
        (*env)->CallBooleanMethod(env, notification_list, add_notification, element);
        metadata = metadata->next;
    }

    out:
    es10b_notification_metadata_list_free_all(metadata);
    return notification_list;
}

JNIEXPORT jint JNICALL
Java_net_typeblog_lpac_1jni_LpacJni_handleNotification(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jlong seq_number
) {
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
Java_net_typeblog_lpac_1jni_LpacJni_es10bDeleteNotification(
        __attribute__((unused)) JNIEnv *env,
        __attribute__((unused)) jobject thiz,
        jlong handle,
        jlong seq_number
) {
    struct euicc_ctx *ctx = (struct euicc_ctx *) handle;
    return es10b_remove_notification_from_list(ctx, (unsigned long) seq_number);
}
