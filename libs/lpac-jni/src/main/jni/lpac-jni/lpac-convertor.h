#pragma once

#include <jni.h>
#include <euicc/es10c.h>

#define PROFILE_STATE_CLASS "net/typeblog/lpac_jni/ProfileState"
#define PROFILE_CLASS_CLASS "net/typeblog/lpac_jni/ProfileClass"

void lpac_convertor_init(JNIEnv *env);

jobject to_profile_state(enum es10c_profile_state profile_state);

jobject to_profile_class(enum es10c_profile_class profile_class);
