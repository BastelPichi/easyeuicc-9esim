#include "lpac-convertor.h"

static jobject profile_state_enabled;
static jobject profile_state_disabled;
static jobject profile_class_operational;
static jobject profile_class_provisioning;
static jobject profile_class_testing;

static jobject bind_static_field(JNIEnv *env, jclass clazz, const char *name, const char *sig) {
    jfieldID field = (*env)->GetStaticFieldID(env, clazz, name, sig);
    jobject bound = (*env)->GetStaticObjectField(env, clazz, field);
    return (*env)->NewGlobalRef(env, bound);
}

#define BIND_PROFILE_STATE_STATIC_FIELD(NAME, FIELD) \
    profile_state_##NAME = bind_static_field(env, profile_state_class, FIELD, "L" PROFILE_STATE_CLASS ";")

#define BIND_PROFILE_CLASS_STATIC_FIELD(NAME, FIELD) \
    profile_class_##NAME = bind_static_field(env, profile_class_class, FIELD, "L" PROFILE_CLASS_CLASS ";")

void lpac_convertor_init(JNIEnv *env) {
    jclass profile_state_class = (*env)->FindClass(env, PROFILE_STATE_CLASS);
    BIND_PROFILE_STATE_STATIC_FIELD(enabled, "Enabled");
    BIND_PROFILE_STATE_STATIC_FIELD(disabled, "Disabled");

    jclass profile_class_class = (*env)->FindClass(env, PROFILE_CLASS_CLASS);
    BIND_PROFILE_CLASS_STATIC_FIELD(operational, "Operational");
    BIND_PROFILE_CLASS_STATIC_FIELD(provisioning, "Provisioning");
    BIND_PROFILE_CLASS_STATIC_FIELD(testing, "Testing");
}

#undef BIND_PROFILE_STATE_STATIC_FIELD
#undef BIND_PROFILE_CLASS_STATIC_FIELD

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