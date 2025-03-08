package net.typeblog.lpac_jni

interface ProfileDiscoveryCallback {
    fun onDiscovered(hosts: Array<String>)
}
