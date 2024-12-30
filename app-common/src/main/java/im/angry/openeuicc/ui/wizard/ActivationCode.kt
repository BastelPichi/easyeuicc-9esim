package im.angry.openeuicc.ui.wizard


data class ActivationCode(
    val address: String,
    val matchingId: String? = null,
    val oid: String? = null,
    val requiredConfirmationCode: Boolean = false,
) {
    companion object {
        private const val SCHEME = "LPA:"
        private const val DELIMITER = '$'

        fun fromString(input: String): ActivationCode {
            if (input.isBlank()) {
                throw IllegalArgumentException("Activation code cannot be empty")
            } else if (!input.startsWith(SCHEME)) {
                throw IllegalArgumentException("Invalid activation code format")
            }
            val components = input.removePrefix(SCHEME).split(DELIMITER)
            if (components.size < 2) {
                throw IllegalArgumentException("Invalid activation code format")
            } else if (components[0] != "1") {
                throw IllegalArgumentException("Invalid activation code AC_FORMAT")
            }
            return ActivationCode(
                address = components[1],
                matchingId = components.getOrNull(2),
                oid = components.getOrNull(3),
                requiredConfirmationCode = components.getOrNull(4) == "1"
            )
        }
    }
}
