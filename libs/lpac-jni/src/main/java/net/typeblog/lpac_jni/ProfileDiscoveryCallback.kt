package net.typeblog.lpac_jni

interface ProfileDiscoveryCallback {
    fun onDiscovered(servers: List<String>)
}
