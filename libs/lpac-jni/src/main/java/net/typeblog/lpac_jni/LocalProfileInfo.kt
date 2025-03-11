package net.typeblog.lpac_jni

data class ProfileMetadata(
    val iccid: String,
    val name: String,
    val providerName: String,
    val profileClass: ProfileClass,
    val iconType: IconType?,
    val icon: String,
)

data class LocalProfileInfo(
    val iccid: String,
    val state: ProfileState,
    val name: String,
    val nickName: String,
    val providerName: String,
    val isdpAID: String,
    val profileClass: ProfileClass,
    val iconType: IconType?,
    val icon: String,
)

enum class ProfileState { Enabled, Disabled }

enum class ProfileClass { Testing, Provisioning, Operational }

enum class IconType { JPEG, PNG }
