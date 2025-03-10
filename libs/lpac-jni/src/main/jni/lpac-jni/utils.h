#pragma once

#include <jni.h>
#include <euicc/es8p.h>
#include <euicc/es10c.h>

#define PACKAGE_NAME "net/typeblog/lpac_jni"
#define PROFILE_STATE_CLASS PACKAGE_NAME "/ProfileState"
#define PROFILE_CLASS_CLASS PACKAGE_NAME "/ProfileClass"
#define DOWNLOAD_CALLBACK_CLASS PACKAGE_NAME "/ProfileDownloadCallback"
#define DOWNLOAD_STATE_CLASS DOWNLOAD_CALLBACK_CLASS "$DownloadState"
#define PROFILE_METADATA_CLASS DOWNLOAD_CALLBACK_CLASS "$ProfileMetadata"
#define LOCAL_PROFILE_NOTIFICATION_CLASS PACKAGE_NAME "/LocalProfileNotification"
#define PROFILE_MANAGEMENT_OPERATION_CLASS PACKAGE_NAME "/ProfileManagementOperation"
#define EUICC_CONFIGURED_ADDRESSES_CLASS PACKAGE_NAME "/EuiccConfiguredAddresses"
#define VERSION_CLASS PACKAGE_NAME "/Version"
#define HASH_SET_CLASS "java/util/HashSet"
#define ARRAY_LIST_CLASS "java/util/ArrayList"

void lpac_convertor_init(JNIEnv *env);

jstring toJString(JNIEnv *env, const char *pat);

jobject bind_static_field(JNIEnv *env, jclass clazz, const char *name, const char *sig);

jobject to_profile_state(enum es10c_profile_state profile_state);

jobject to_profile_class(enum es10c_profile_class profile_class);

jobject to_version(JNIEnv *env, const char *version);

jobject to_string_set(JNIEnv *env, char **values);

jobject to_string_list(JNIEnv *env, char **values);

jobject build_profile_metadata(JNIEnv *env, struct es8p_metadata *metadata);

jobject to_profile_management_operation(enum es10b_profile_management_operation operation);

jobject new_array_list(JNIEnv *env);
