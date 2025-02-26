package net.typeblog.lpac_jni

import java.io.Closeable

/*
 * Should reflect euicc_apdu_interface in lpac/euicc/interface.h
 */
interface ApduInterface {
    fun connect()
    fun disconnect()
    fun logicalChannelOpen(aid: ByteArray): Int
    fun logicalChannelClose(handle: Int)
    fun transmit(handle: Int, tx: ByteArray): ByteArray

    /**
     * Is this APDU connection still valid?
     * Note that even if this returns true, the underlying connection might be broken anyway;
     * callers should further check with the LPA to fully determine the validity of a channel
     */
    val valid: Boolean

    fun <T> withLogicalChannel(aid: ByteArray, callback: (ApduLogicalChannelHandle) -> T): T {
        val handle = logicalChannelOpen(aid)
        return ApduLogicalChannelHandle(handle, this).use(callback)
    }
}

data class ApduLogicalChannelHandle(
    private val handle: Int,
    private val apduInterface: ApduInterface,
) : Closeable {
    private var closed: Boolean = false

    fun transmit(tx: ByteArray) {
        check(closed) { "Logical channel is already closed" }
        apduInterface.transmit(handle, tx)
    }

    override fun close() {
        if (closed) return
        apduInterface.logicalChannelClose(handle)
        closed = true
    }
}