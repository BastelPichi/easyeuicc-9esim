package net.typeblog.lpac_jni

interface ProfileDownloadCallback {
    companion object {
        fun lookupStateFromProgress(progress: Int): DownloadState = when (progress) {
            0 -> DownloadState.Preparing
            20 -> DownloadState.Connecting
            40 -> DownloadState.Authenticating
            60 -> DownloadState.Downloading
            80 -> DownloadState.Finalizing
            else -> throw IllegalArgumentException("Unknown state")
        }
    }

    enum class DownloadState(val progress: Int) {
        Preparing(0),
        Connecting(20), // Before {server,client} authentication
        Authenticating(40), // {server,client} authentication
        Downloading(60), // prepare download, get bpp from es9p
        Finalizing(80), // load bpp
    }

    data class ProfileMetadata(
        val iccid: String,
        val serviceProviderName: String,
        val profileName: String,
        val iconType: String,
        val icon: String,
        val profileClass: String,
    )

    fun onStateUpdate(state: DownloadState)

    fun onProfileMetadata(metadata: ProfileMetadata)
}