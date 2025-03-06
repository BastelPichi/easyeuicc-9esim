package im.angry.openeuicc.util

data class ActivationCode(
    val address: String,
    val matchingId: String? = null,
    val oid: String? = null,
    val confirmationCodeRequired: Boolean = false,
) {
    companion object {
        fun fromString(input: String): ActivationCode {
            val components = input.removePrefix("LPA:").split('$')
                .map(String::trim).map { it.ifBlank { null } }
            if (components.size < 2 || components[0] != "1" || components[1] == null) {
                throw IllegalArgumentException("Invalid activation code format")
            }
            return ActivationCode(
                components[1]!!,
                components.getOrNull(2),
                components.getOrNull(3),
                components.getOrNull(4) == "1",
            )
        }
    }

    init {
        check(isFQDN(address)) { "Invalid SM-DP+ address" }
        check(isMatchingID(matchingId)) { "Invalid Matching ID" }
        check(isObjectIdentifier(oid)) { "Invalid OID" }
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
private fun isFQDN(input: String): Boolean {
    if (input.isEmpty() || input.length > 255) return false
    for (part in input.split('.')) {
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
private fun isMatchingID(input: String?): Boolean {
    if (input == null) return true
    return input.all { it.isLetterOrDigit() || it == '-' }
}

/**
 * SM-DP+ OID in the CERT.DPauth.ECDSA
 */
private fun isObjectIdentifier(input: String?): Boolean {
    if (input == null) return true
    if (input.length > 255) return false
    return input.split('.').all { it.all(Char::isDigit) }
}
