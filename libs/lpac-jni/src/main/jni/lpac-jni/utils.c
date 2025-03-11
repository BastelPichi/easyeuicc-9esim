#include "utils.h"
#include <malloc.h>
#include <string.h>
#include <syslog.h>

static jobject profile_state_enabled;
static jobject profile_state_disabled;
static jobject profile_class_operational;
static jobject profile_class_provisioning;
static jobject profile_class_testing;
static jobject profile_management_operation_install;
static jobject profile_management_operation_enable;
static jobject profile_management_operation_disable;
static jobject profile_management_operation_delete;
static jobject icon_type_jpeg;
static jobject icon_type_png;

static jclass version_class;
static jmethodID version_constructor;

static jstring empty_string;
static jclass string_class;
static jmethodID string_constructor;

jobject bind_static_field(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
    jfieldID field = (*env)->GetStaticFieldID(env, clazz, name, sig);
    jobject bound = (*env)->GetStaticObjectField(env, clazz, field);
    return (*env)->NewGlobalRef(env, bound);
}

#define BIND_PROFILE_STATE_STATIC_FIELD(NAME, FIELD) \
    profile_state_##NAME = bind_static_field(env, profile_state_class, FIELD, "L" PROFILE_STATE_CLASS ";")

#define BIND_PROFILE_CLASS_STATIC_FIELD(NAME, FIELD) \
    profile_class_##NAME = bind_static_field(env, profile_class_class, FIELD, "L" PROFILE_CLASS_CLASS ";")

#define BIND_NOTIFICATION_OPERATION_FIELD(NAME, FIELD) \
    profile_management_operation_##NAME = bind_static_field(env, profile_management_operation_class, FIELD, "L" PROFILE_MANAGEMENT_OPERATION_CLASS ";")

#define BIND_ICON_TYPE_FIELD(NAME, FIELD) \
    icon_type_##NAME = bind_static_field(env, icon_type_class, FIELD, "L" ICON_TYPE_CLASS ";")

static void init_string_class(JNIEnv *env) {
    string_class = (*env)->FindClass(env, "java/lang/String");
    string_class = (*env)->NewGlobalRef(env, string_class);
    string_constructor = (*env)->GetMethodID(env, string_class, "<init>",
                                             "([BLjava/lang/String;)V");
    const jchar _unused[1];
    empty_string = (*env)->NewString(env, _unused, 0);
    empty_string = (*env)->NewGlobalRef(env, empty_string);
}

static void init_version_class(JNIEnv *env) {
    version_class = (*env)->FindClass(env, VERSION_CLASS);
    version_class = (*env)->NewGlobalRef(env, version_class);
    version_constructor = (*env)->GetMethodID(env, version_class, "<init>", "(Ljava/lang/String;)V");
}

void lpac_convertor_init(JNIEnv *env) {
    init_string_class(env);
    init_version_class(env);

    jclass profile_state_class = (*env)->FindClass(env, PROFILE_STATE_CLASS);
    BIND_PROFILE_STATE_STATIC_FIELD(enabled, "Enabled");
    BIND_PROFILE_STATE_STATIC_FIELD(disabled, "Disabled");

    jclass profile_class_class = (*env)->FindClass(env, PROFILE_CLASS_CLASS);
    BIND_PROFILE_CLASS_STATIC_FIELD(operational, "Operational");
    BIND_PROFILE_CLASS_STATIC_FIELD(provisioning, "Provisioning");
    BIND_PROFILE_CLASS_STATIC_FIELD(testing, "Testing");

    jclass profile_management_operation_class = (*env)->FindClass(env, PROFILE_MANAGEMENT_OPERATION_CLASS);
    BIND_NOTIFICATION_OPERATION_FIELD(install, "Install");
    BIND_NOTIFICATION_OPERATION_FIELD(delete, "Delete");
    BIND_NOTIFICATION_OPERATION_FIELD(enable, "Enable");
    BIND_NOTIFICATION_OPERATION_FIELD(disable, "Disable");

    jclass icon_type_class = (*env)->FindClass(env, ICON_TYPE_CLASS);
    BIND_ICON_TYPE_FIELD(jpeg, "JPEG");
    BIND_ICON_TYPE_FIELD(png, "PNG");
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

jobject to_profile_state(enum es10c_profile_state profile_state) {
    switch (profile_state) {
        case ES10C_PROFILE_STATE_ENABLED:
            return profile_state_enabled;
        case ES10C_PROFILE_STATE_DISABLED:
            return profile_state_disabled;
        default:
            return NULL;
    }
}

jobject to_profile_class(enum es10c_profile_class profile_class) {
    switch (profile_class) {
        case ES10C_PROFILE_CLASS_OPERATIONAL:
            return profile_class_operational;
        case ES10C_PROFILE_CLASS_PROVISIONING:
            return profile_class_provisioning;
        case ES10C_PROFILE_CLASS_TEST:
            return profile_class_testing;
        default:
            return NULL;
    }
}

jobject to_profile_management_operation(enum es10b_profile_management_operation operation) {
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
            return NULL;
    }
}

jstring to_icon_type(enum es10c_icon_type icon_type) {
    switch (icon_type) {
        case ES10C_ICON_TYPE_JPEG:
            return icon_type_jpeg;
        case ES10C_ICON_TYPE_PNG:
            return icon_type_png;
        default:
            return NULL;
    }
}

jobject to_version(JNIEnv *env, const char *version) {
    jstring value = toJString(env, version);
    return (*env)->NewObject(env, version_class, version_constructor, value);
}

jobject to_string_set(JNIEnv *env, char **values) {
    jclass set_class = (*env)->FindClass(env, HASH_SET_CLASS);
    jmethodID set_constructor = (*env)->GetMethodID(env, set_class, "<init>", "()V");
    jobject elements = (*env)->NewObject(env, set_class, set_constructor);
    jmethodID add_element = (*env)->GetMethodID(env, set_class, "add", "(Ljava/lang/Object;)Z");
    jstring element = NULL;
    for (jsize index = 0; values[index] != NULL; index++) {
        element = toJString(env, values[index]);
        (*env)->CallBooleanMethod(env, elements, add_element, element);
    }
    return elements;
}

jobject to_string_list(JNIEnv *env, char **values) {
    jclass list_class = (*env)->FindClass(env, ARRAY_LIST_CLASS);
    jmethodID list_constructor = (*env)->GetMethodID(env, list_class, "<init>", "()V");
    jobject elements = (*env)->NewObject(env, list_class, list_constructor);
    jmethodID add_element = (*env)->GetMethodID(env, list_class, "add", "(Ljava/lang/Object;)Z");
    jstring element = NULL;
    for (jsize index = 0; values[index] != NULL; index++) {
        element = toJString(env, values[index]);
        (*env)->CallBooleanMethod(env, elements, add_element, element);
    }
    return elements;
}

jobject build_profile_metadata(JNIEnv *env, struct es8p_metadata *metadata) {
    if (metadata == NULL) return NULL;

    jclass profile_metadata_class = (*env)->FindClass(env, PROFILE_METADATA_CLASS);
    jmethodID profile_metadata_constructor = (*env)->GetMethodID(
            env, profile_metadata_class, "<init>",
            "("
            "Ljava/lang/String;" // iccid
            "Ljava/lang/String;" // name
            "Ljava/lang/String;" // provider name
            "L" PROFILE_CLASS_CLASS ";"
            "L" ICON_TYPE_CLASS ";"
            "Ljava/lang/String;" // icon (base64-encoded)
            ")"
            "V" // (returns) void
    );

    return (*env)->NewObject(
            env, profile_metadata_class, profile_metadata_constructor,
            toJString(env, metadata->iccid),
            toJString(env, metadata->profileName),
            toJString(env, metadata->serviceProviderName),
            to_profile_class(metadata->profileClass),
            to_icon_type(metadata->iconType),
            toJString(env, metadata->icon)
    );
}

jobject new_array_list(JNIEnv *env) {
    jclass array_list_class = (*env)->FindClass(env, ARRAY_LIST_CLASS);
    jmethodID array_list_constructor = (*env)->GetMethodID(env, array_list_class, "<init>", "()V");
    return (*env)->NewObject(env, array_list_class, array_list_constructor);
}
