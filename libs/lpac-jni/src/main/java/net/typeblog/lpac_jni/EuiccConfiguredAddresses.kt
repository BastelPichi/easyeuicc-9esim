package net.typeblog.lpac_jni

import android.util.Patterns

// example address in GSMA SGP.26, some chips use addresses like this
@Suppress("SpellCheckingInspection")
val invalidDPAddresses = setOf(
    "testrootsmds.gsma.com",
    "testrootsmds.example.com",
)

class EuiccConfiguredAddresses(defaultDPAddress: String?, rootDSAddress: String?) {
    val defaultDPAddress: String? = defaultDPAddress.takeUnless(::isInvalidDPAddress)
    val rootDSAddress = rootDSAddress.takeUnless(::isInvalidDSAddress)

    val discoverable: Boolean
        get() = !defaultDPAddress.isNullOrBlank() || !rootDSAddress.isNullOrBlank()
}

private fun isInvalidDPAddress(address: String?): Boolean {
    if (address.isNullOrBlank()) return true
    return !Patterns.DOMAIN_NAME.matcher(address).matches()
}

private fun isInvalidDSAddress(address: String?): Boolean {
    if (address.isNullOrBlank()) return true
    if (address in invalidDPAddresses) return true
    return !Patterns.DOMAIN_NAME.matcher(address).matches()
}
