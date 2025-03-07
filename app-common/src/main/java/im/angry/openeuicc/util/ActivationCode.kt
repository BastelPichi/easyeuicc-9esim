package im.angry.openeuicc.util

data class ActivationCode(
    val address: String,
    val matchingId: String? = null,
    val oid: String? = null,
    val confirmationCodeRequired: Boolean = false,
) {
    companion object {
        fun fromString(token: String): ActivationCode {
            val input = if (token.startsWith("LPA:", true)) token.drop(4) else token
            val components = input.split('$').map { it.trim().ifEmpty { null } }
            check(components.size >= 2) { "Invalid activation code format" }
            check(components[0] == "1") { "Invalid activation code version" }
            return ActivationCode(
                checkNotNull(components[1]) { "Invalid SM-DP+ address" },
                components.getOrNull(2),
                components.getOrNull(3),
                components.getOrNull(4) == "1",
            )
        }
    }

    init {
        check(isFQDN(address)) { "Invalid SM-DP+ address" }
        check(matchingId == null || isMatchingID(matchingId)) { "Invalid Matching ID" }
        check(oid == null || isObjectIdentifier(oid)) { "Invalid OID" }
    }

    override fun toString(): String {
        val parts = arrayOf(
            "1",
            address,
            matchingId ?: "",
            oid ?: "",
            if (confirmationCodeRequired) "1" else ""
        )
        return parts.joinToString("$").trimEnd('$')
    }
}

/**
 * SGP.22 4.1 Activation Code (v2.2.2, p111)
 *
 * FQDN (Fully Qualified Domain Name) of the SM-DP+ (e.g., SMDP.GSMA.COM)
 * restricted to the Alphanumeric mode character set defined in table 5 of ISO/IEC 18004 [15]
 * excluding '$'
 */
private fun isFQDN(input: CharSequence): Boolean {
    if (input.isEmpty() || input.length > 255) return false
    val parts = input.split('.')
    if (parts.size < 2) return false
    for (part in parts) {
        if (part.isEmpty() || part.length > 63) return false
        if (part.all { it.isLetterOrDigit() || it == '-' }) continue
        return false
    }
    return true
}

/**
 * SGP.22 4.1.1 Matching ID (v2.2.2, p112)
 *
 * Matching ID is a string of alphanumeric characters and hyphens.
 */
private fun isMatchingID(input: CharSequence) =
    input.all { it.isLetterOrDigit() || it == '-' }

/**
 * SGP.22 4.1 Activation Code (v2.2.2, p111)
 *
 * SM-DP+ OID in the CERT.DPauth.ECDSA
 */
private fun isObjectIdentifier(input: CharSequence) = input.length < 255 &&
        input.count { it == '.' } > 2 &&
        input.split('.').all { it.isNotEmpty() && it.all(Char::isDigit) }
