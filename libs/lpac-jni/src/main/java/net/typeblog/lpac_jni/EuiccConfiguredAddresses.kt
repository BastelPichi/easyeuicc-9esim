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
        get() = isValidDefaultDPAddress || isValidRootDSAddress

    val isValidDefaultDPAddress: Boolean
        get() {
            if (defaultDPAddress.isBlank()) return false
            return Patterns.DOMAIN_NAME.matcher(defaultDPAddress).matches()
        }

    val isValidRootDSAddress: Boolean
        get() {
            if (rootDSAddress.isBlank()) return false
            if (rootDSAddress in invalidDPAddresses) return false
            return Patterns.DOMAIN_NAME.matcher(rootDSAddress).matches()
        }
}