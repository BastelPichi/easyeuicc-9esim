package im.angry.openeuicc.core

import android.telephony.IccOpenLogicalChannelResponse.STATUS_NO_ERROR
import android.telephony.IccOpenLogicalChannelResponse.INVALID_CHANNEL
import android.telephony.TelephonyManager
import android.util.Log
import im.angry.openeuicc.util.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.typeblog.lpac_jni.ApduInterface

class TelephonyManagerApduInterface(
    private val port: UiccPortInfoCompat,
    private val tm: TelephonyManager,
    private val verboseLoggingFlow: Flow<Boolean>
) : ApduInterface, ApduInterfaceAtrProvider {
    companion object {
        const val TAG = "TelephonyManagerApduInterface"
    }

    override val valid: Boolean
        get() = channels.isNotEmpty()

    private var channels = mutableSetOf<Int>()

    override fun connect() {
        // Do nothing
    }

    override fun disconnect() {
        // Do nothing
    }

    private val slotIndex: Int
        get() = port.card.physicalSlotIndex

    private val portIndex: Int
        get() = port.portIndex

    override fun logicalChannelOpen(aid: ByteArray): Int {
        val hex = aid.encodeHex()
        val channel = tm.iccOpenLogicalChannelByPortCompat(slotIndex, portIndex, hex, 0)
        require(channel.status == STATUS_NO_ERROR && channel.channel != INVALID_CHANNEL) {
            "Cannot open logical channel $hex via TelephonyManager on slot $slotIndex port $portIndex"
        }
        channels.add(channel.channel)
        return channel.channel
    }

    override fun logicalChannelClose(handle: Int) {
        check(channels.contains(handle)) {
            "Invalid logical channel handle $handle"
        }
        tm.iccCloseLogicalChannelByPortCompat(slotIndex, portIndex, handle)
        channels.remove(handle)
    }

    override fun transmit(handle: Int, tx: ByteArray): ByteArray {
        check(channels.contains(handle)) {
            "Invalid logical channel handle $handle"
        }
        val verbose = runBlocking { verboseLoggingFlow.first() }
        if (verbose) Log.d(TAG, "TelephonyManager APDU: ${tx.encodeHex()}")
        val result = tm.iccTransmitApduLogicalChannelByPortCompat(slotIndex, portIndex, handle, tx)
        if (verbose) Log.d(TAG, "TelephonyManager APDU response: $result")
        return result?.decodeHex() ?: byteArrayOf()
    }

    override val atr: ByteArray?
        get() = try {
            tm.iccGetAtr(slotIndex)?.decodeHex()
        } catch (e: NoSuchMethodException) {
            try {
                tm.getAtrUsingSlotId(slotIndex)
            } catch (e: Exception) {
                null
            }
        }
}