package net.typeblog.lpac_jni

import java.util.ArrayList

interface ProfileDiscoveryCallback {
    fun onDiscovered(servers: ArrayList<String>)
}
