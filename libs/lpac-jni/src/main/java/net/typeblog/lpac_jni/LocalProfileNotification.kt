package net.typeblog.lpac_jni

data class LocalProfileNotification(
    val seqNumber: Long,
    val profileManagementOperation: ProfileManagementOperation,
    val notificationAddress: String,
    val iccid: String,
)

enum class ProfileManagementOperation {
    Install,
    Enable,
    Disable,
    Delete,
    Unknown,
}
