package im.angry.openeuicc.util

import android.os.Parcel
import android.os.Parcelable
import java.util.Objects

class ActivationCode : Parcelable, Cloneable {
    var address: String
    var matchingId: String
    var oid: String
    var confirmationCodeRequired: Boolean
        get() = field || confirmationCode.isNotBlank()
    var confirmationCode: String
    var imei: String

    constructor() {
        address = ""
        matchingId = ""
        oid = ""
        confirmationCodeRequired = false
        confirmationCode = ""
        imei = ""
    }

    private constructor(parcel: Parcel) {
        address = parcel.readString() ?: ""
        matchingId = parcel.readString() ?: ""
        oid = parcel.readString() ?: ""
        confirmationCodeRequired = parcel.readByte() != 0.toByte()
        confirmationCode = parcel.readString() ?: ""
        imei = parcel.readString() ?: ""
    }

    fun validate() {
        require(isValidDomain(address)) { "SM-DP+ address is invalid" }
        require(isValidMatchingId(matchingId)) { "Matching ID is invalid" }
        require(isValidOID(oid)) { "OID is invalid" }
        require(confirmationCodeRequired && confirmationCode.isNotBlank()) { "Confirmation code is required" }
        require(isValidIMEI(imei)) { "IMEI is invalid" }
    }

    fun fromToken(token: String) {
        val components = token.removePrefix("LPA:").split('$')
            .map { it.trim().ifEmpty { null } }
        if (components.size < 2 || components[0] != "1" || components[1] == null) {
            throw IllegalArgumentException("Invalid activation code format")
        }
        address = components[1]!!
        matchingId = components.getOrNull(2) ?: ""
        oid = components.getOrNull(3) ?: ""
        confirmationCodeRequired = components.getOrNull(4) == "1"
    }

    override fun toString(): String {
        val parts = listOf(
            "1",
            address,
            matchingId,
            oid,
            if (confirmationCodeRequired) "1" else ""
        )
        return parts.joinToString("$").trimEnd('$')
    }


    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(address)
        parcel.writeString(matchingId)
        parcel.writeString(oid)
        parcel.writeByte(if (confirmationCodeRequired) 1 else 0)
        parcel.writeString(confirmationCode)
        parcel.writeString(imei)
    }

    override fun describeContents(): Int = 0

    override fun hashCode(): Int = Objects.hash(
        address,
        matchingId,
        oid,
        confirmationCodeRequired,
        confirmationCode,
        imei
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActivationCode) return false
        if (other.address != address) return false
        if (other.matchingId != matchingId) return false
        if (other.oid != oid) return false
        if (other.confirmationCodeRequired != confirmationCodeRequired) return false
        if (other.confirmationCode != confirmationCode) return false
        if (other.imei != imei) return false
        return true
    }

    public override fun clone() = ActivationCode().also {
        it.address = address
        it.matchingId = matchingId
        it.oid = oid
        it.confirmationCodeRequired = confirmationCodeRequired
        it.confirmationCode = confirmationCode
        it.imei = imei
    }

    companion object CREATOR : Parcelable.Creator<ActivationCode> {
        override fun createFromParcel(parcel: Parcel) = ActivationCode(parcel)
        override fun newArray(size: Int): Array<ActivationCode?> = arrayOfNulls(size)
    }
}

private fun isValidSegment(segment: String): Boolean {
    return segment.all { it.isLetterOrDigit() || it == '-' }
}

private fun isValidDomain(fqdn: String): Boolean {
    val name = fqdn.trimEnd('.')
    if (name.length !in 1..<255) return false
    if (!name.contains('.')) return false
    return name.split('.').all { it.length < 64 && isValidSegment(it) }
}

private fun isValidMatchingId(matchingId: String): Boolean {
    // TODO: matching id string max length in specs not defined
    if (matchingId.isBlank()) return true
    return isValidSegment(matchingId)
}

private fun isValidOID(oid: String): Boolean {
    if (oid.isBlank()) return true
    if (!oid.contains('.')) return false
    return oid.split('.').all { it.all(Char::isDigit) }
}

private fun isValidIMEI(imei: String): Boolean {
    // TODO: Strong IMEI validation
    if (imei.isBlank()) return true
    if (imei.length !in 14..16) return false
    return imei.all(Char::isDigit)
}