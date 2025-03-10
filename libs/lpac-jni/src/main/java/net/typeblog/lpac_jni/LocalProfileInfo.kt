package net.typeblog.lpac_jni

data class LocalProfileInfo(
    val iccid: String,
    val state: ProfileState,
    val name: String,
    val nickName: String,
    val providerName: String,
    val isdpAID: String,
    val profileClass: ProfileClass
)

enum class ProfileState { Enabled, Disabled }

enum class ProfileClass { Testing, Provisioning, Operational }
