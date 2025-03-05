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
                .map(String::trim)
            if (components.size < 2 || components[0] != "1") {
                throw IllegalArgumentException("Invalid activation code format")
            }
            return ActivationCode(
                components[1],
                components.getOrNull(2)?.ifBlank { null },
                components.getOrNull(3)?.ifBlank { null },
                components.getOrNull(4) == "1"
            )
        }
    }

    override fun toString(): String {
        val parts = listOf(
            "1",
            address,
            matchingId ?: "",
            oid ?: "",
            if (confirmationCodeRequired) "1" else ""
        )
        return parts.joinToString("$").trimEnd('$')
    }
}