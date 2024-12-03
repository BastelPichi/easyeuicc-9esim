import im.angry.openeuicc.build.*

plugins {
    id("com.android.application")
}

apply {
    plugin<MySigningPlugin>()
}

android {
    namespace = "im.angry.openeuicc"
    compileSdk = 35

    defaultConfig {
        applicationId = "im.angry.openeuicc"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
    }

    buildTypes {
        all {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
}
