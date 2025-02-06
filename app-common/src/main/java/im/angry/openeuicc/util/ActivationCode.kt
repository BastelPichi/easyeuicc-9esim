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
                .map { it.trim().ifEmpty { null } }
            if (components.size < 2 || components[0] != "1" || components[1].isNullOrEmpty()) {
                throw IllegalArgumentException("Invalid activation code format")
            }
            return ActivationCode(
                components[1]!!,
                components.getOrNull(2),
                components.getOrNull(3),
                components.getOrNull(4) == "1"
            )
        }
    }

    override fun toString(): String {
        val parts = buildList {
            add("1")
            add(address)
            add(matchingId ?: "")
            add(oid ?: "")
            add(if (confirmationCodeRequired) "1" else "")
        }
        return parts.joinToString("$").trimEnd('$')
    }
}