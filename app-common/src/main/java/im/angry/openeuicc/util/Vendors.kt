package im.angry.openeuicc.util

import android.util.Log
import im.angry.openeuicc.core.ApduInterfaceAtrProvider
import im.angry.openeuicc.core.EuiccChannel
import net.typeblog.lpac_jni.Version

data class EuiccVendorInfo(
    val skuName: String?,
    val serialNumber: String?,
    val bootloaderVersion: String?,
    val firmwareVersion: String?,
)

private val EUICC_VENDORS: Array<EuiccVendor> = arrayOf(ESTKme(), SIMLink9(), Eastcompeace())

fun EuiccChannel.tryParseEuiccVendorInfo(): EuiccVendorInfo? =
    EUICC_VENDORS.firstNotNullOfOrNull { it.tryParseEuiccVendorInfo(this) }

interface EuiccVendor {
    fun tryParseEuiccVendorInfo(channel: EuiccChannel): EuiccVendorInfo?
}

private class ESTKme : EuiccVendor {
    companion object {
        private val TAG = ESTKme::class.java.simpleName
        private val PRODUCT_AID = "A06573746B6D65FFFFFFFFFFFF6D6774".decodeHex()
        private val PRODUCT_ATR_FPR = "estk.me".encodeToByteArray()
    }

    private fun checkAtr(channel: EuiccChannel): Boolean {
        val iface = channel.apduInterface
        if (iface !is ApduInterfaceAtrProvider) return false
        val atr = iface.atr ?: return false
        for (index in atr.indices) {
            if (atr.size - index < PRODUCT_ATR_FPR.size) break
            if (atr.sliceArray(index until index + PRODUCT_ATR_FPR.size)
                    .contentEquals(PRODUCT_ATR_FPR)
            ) return true
        }
        return false
    }

    private fun decodeAsn1String(b: ByteArray): String? {
        if (b.size < 2) return null
        if (b[b.size - 2] != 0x90.toByte() || b[b.size - 1] != 0x00.toByte()) return null
        return b.sliceArray(0 until b.size - 2).decodeToString()
    }

    override fun tryParseEuiccVendorInfo(channel: EuiccChannel): EuiccVendorInfo? {
        if (!checkAtr(channel)) return null

        val iface = channel.apduInterface
        return try {
            iface.withLogicalChannel(PRODUCT_AID) { transmit ->
                fun invoke(p1: Byte) =
                    decodeAsn1String(transmit(byteArrayOf(0x00, 0x00, p1, 0x00, 0x00)))
                EuiccVendorInfo(
                    skuName = invoke(0x03),
                    serialNumber = invoke(0x00),
                    bootloaderVersion = invoke(0x01),
                    firmwareVersion = invoke(0x02),
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get ESTKmeInfo", e)
            null
        }
    }
}

private class SIMLink9 : EuiccVendor {
    companion object {
        private val EID_PATTERN = Regex("^89044045(84|21)67274948")
    }

    override fun tryParseEuiccVendorInfo(channel: EuiccChannel): EuiccVendorInfo? {
        val eid = channel.lpa.eID
        val version = channel.lpa.euiccInfo2?.euiccFirmwareVersion
        if (version == null || EID_PATTERN.find(eid, 0) == null) return null
        val versionName = when {
            // @formatter:off
            version >= Version(37,  1, 41) -> "v3.1 (beta 1)"
            version >= Version(36, 18,  5) -> "v3 (final)"
            version >= Version(36, 17, 39) -> "v3 (beta)"
            version >= Version(36, 17,  4) -> "v2s"
            version >= Version(36,  9,  3) -> "v2.1"
            version >= Version(36,  7,  2) -> "v2"
            // @formatter:on
            else -> null
        }

        val skuName = if (versionName == null) {
            "9eSIM"
        } else {
            "9eSIM $versionName"
        }

        return EuiccVendorInfo(
            skuName = skuName,
            serialNumber = null,
            bootloaderVersion = null,
            firmwareVersion = null
        )
    }
}

@Suppress("SpellCheckingInspection")
private class Eastcompeace : EuiccVendor {
    companion object {
        private val TAG = Eastcompeace::class.java.simpleName
        private const val EID_PREFIX = "89086030"
        private val PRODUCT_AID = "A000000533C000FF860000000427".decodeHex()
        private val COMMAND = "80CA000050".decodeHex()
    }

    class SCID(val scid: ByteArray) {
        val bootloaderVersion: ByteArray
            get() = scid.sliceArray(12 until 14)
        val cosVersion: ByteArray
            get() = scid.sliceArray(14 until 16)
        val uniquelyIdentify: ByteArray
            get() = scid.sliceArray(32 until 56)

        override fun toString() = scid.encodeHex()
    }

    private fun decodeResponse(b: ByteArray): ByteArray? {
        if (b.size < 2) return null
        if (b[b.size - 2] != 0x90.toByte() || b[b.size - 1] != 0x00.toByte()) return null
        return b.sliceArray(0 until b.size - 2)
    }

    override fun tryParseEuiccVendorInfo(channel: EuiccChannel): EuiccVendorInfo? {
        if (!channel.lpa.eID.startsWith(EID_PREFIX)) return null
        return try {
            channel.apduInterface.withLogicalChannel(PRODUCT_AID, ::parseSCID)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get EastcompeaceInfo", e)
            null
        }
    }

    fun parseSCID(transmit: (ByteArray) -> ByteArray): EuiccVendorInfo? {
        val scid = decodeResponse(transmit(COMMAND))?.let(::SCID) ?: return null
        Log.i(TAG, "Eastcompeace SCID: $scid")
        return EuiccVendorInfo(
            skuName = "Eastcompeace",
            serialNumber = scid.uniquelyIdentify.encodeHex(),
            bootloaderVersion = scid.bootloaderVersion.encodeHex(),
            firmwareVersion = scid.cosVersion.encodeHex(),
        )
    }
}