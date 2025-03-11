package net.typeblog.lpac_jni

import android.util.Patterns

// example address in GSMA SGP.26, some chips use addresses like this
@Suppress("SpellCheckingInspection")
private val invalidDPAddresses = setOf(
    "testrootsmds.gsma.com",
    "testrootsmds.example.com",
)

data class EuiccConfiguredAddresses(
    val defaultDPAddress: String,
    val rootDSAddress: String
) {
    val discoverable: Boolean
        get() = isValidDPAddress(defaultDPAddress) ||
                isValidDSAddress(rootDSAddress)
}

private fun isValidDPAddress(address: String): Boolean {
    return address.isNotBlank() && Patterns.DOMAIN_NAME.matcher(address).matches()
}

private fun isValidDSAddress(address: String): Boolean {
    if (address.isBlank()) return false
    if (address in invalidDPAddresses) return false
    return Patterns.DOMAIN_NAME.matcher(address).matches()
}