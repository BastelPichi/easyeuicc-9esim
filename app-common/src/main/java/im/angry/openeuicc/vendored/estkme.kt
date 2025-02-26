package im.angry.openeuicc.vendored

import android.util.Log
import im.angry.openeuicc.core.ApduInterfaceAtrProvider
import im.angry.openeuicc.util.TAG
import im.angry.openeuicc.util.decodeHex
import im.angry.openeuicc.util.encodeHex
import net.typeblog.lpac_jni.ApduInterface

data class ESTKmeInfo(
    val serialNumber: String?,
    val bootloaderVersion: String?,
    val firmwareVersion: String?,
    val skuName: String?,
)

fun isESTKmeATR(atr: ByteArray?): Boolean =
    atr != null && atr.encodeHex().contains("estk.me".encodeToByteArray().encodeHex())

fun getESTKmeInfo(iface: ApduInterface): ESTKmeInfo? {
    if (!isESTKmeATR((iface as ApduInterfaceAtrProvider?)?.atr)) return null
    fun decode(b: ByteArray): String? {
        if (b.size < 2) return null
        if (b[b.size - 2] != 0x90.toByte() || b[b.size - 1] != 0x00.toByte()) return null
        return b.sliceArray(0 until b.size - 2).decodeToString()
    }
    return try {
        iface.openChannel("A06573746B6D65FFFFFFFFFFFF6D6774".decodeHex()) {
            fun invoke(p1: Byte) = decode(it.transmit(byteArrayOf(0x00, 0x00, p1, 0x00, 0x00)))
            ESTKmeInfo(
                invoke(0x00), // serial number
                invoke(0x01), // bootloader version
                invoke(0x02), // firmware version
                invoke(0x03), // sku name
            )
        }
    } catch (e: Exception) {
        Log.d(TAG, "Failed to get ESTKmeInfo", e)
        null
    }
}

