package net.typeblog.lpac_jni

import android.util.Patterns

data class EuiccConfiguredAddresses(
    val defaultDPAddress: String,
    val rootDSAddress: String
) {
    val isValidDefaultDPAddress: Boolean
        get() = isValid(defaultDPAddress)

    val isValidRootDSAddress: Boolean
        get() = isValid(rootDSAddress) || rootDSAddress == "lpa.ds.gsma.com"
}

private fun isValid(address: String): Boolean {
    if (address.isBlank()) return false
    if (address.endsWith(".gsma.com")) return false
    if (address.endsWith(".example.com")) return false
    return Patterns.DOMAIN_NAME.matcher(address).matches()
}