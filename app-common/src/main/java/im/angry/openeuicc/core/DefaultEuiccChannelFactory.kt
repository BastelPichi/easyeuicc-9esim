package im.angry.openeuicc.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.se.omapi.SEService
import android.util.Log
import im.angry.openeuicc.common.R
import im.angry.openeuicc.core.usb.UsbApduInterface
import im.angry.openeuicc.core.usb.getIoEndpoints
import im.angry.openeuicc.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.lang.IllegalArgumentException
import kotlin.math.max
import kotlin.math.min

open class DefaultEuiccChannelFactory(protected val context: Context) : EuiccChannelFactory {
    private var seService: SEService? = null

    private val usbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private suspend fun ensureSEService() {
        if (seService == null || !seService!!.isConnected) {
            seService = connectSEService(context)
        }
    }

    override suspend fun tryOpenEuiccChannel(port: UiccPortInfoCompat): EuiccChannel? {
        if (port.portIndex != 0) {
            Log.w(DefaultEuiccChannelManager.TAG, "OMAPI channel attempted on non-zero portId, this may or may not work.")
        }

        ensureSEService()

        Log.i(DefaultEuiccChannelManager.TAG, "Trying OMAPI for physical slot ${port.card.physicalSlotIndex}")

        val verboseLoggingFlow = context.preferenceRepository.verboseLoggingFlow
        val ignoreTLSCertificateFlow = context.preferenceRepository.ignoreTLSCertificateFlow
        val maxSegmentSizeFlow = context.preferenceRepository.maxSegmentSizeFlow

        try {
            return EuiccChannelImpl(
                context.getString(R.string.omapi),
                port,
                intrinsicChannelName = null,
                OmapiApduInterface(seService!!, port, verboseLoggingFlow),
                verboseLoggingFlow,
                ignoreTLSCertificateFlow,
            ).also {
                // SGP.22 v2.2.2, 2.5.5 Segmented Bound Profile Package (Page 33 of 268)
                // https://www.gsma.com/solutions-and-impact/technologies/esim/wp-content/uploads/2020/06/SGP.22-v2.2.2.pdf#page=33
                //
                // Each segment of this list that is up to 255 bytes is transported in one APDU.
                // Larger TLVs are sent in blocks of 255 bytes for the first blocks and a last block that MAY be shorter.
                val mss = runBlocking {
                    // [32, 255]
                    min(max(maxSegmentSizeFlow.first(), 32), 255)
                }
                Log.i(DefaultEuiccChannelManager.TAG, "Is OMAPI channel, setting MSS to $mss")
                it.lpa.setEs10xMss(mss.toByte())
            }
        } catch (e: IllegalArgumentException) {
            // Failed
            Log.w(
                DefaultEuiccChannelManager.TAG,
                "OMAPI APDU interface unavailable for physical slot ${port.card.physicalSlotIndex}."
            )
        }

        return null
    }

    override fun tryOpenUsbEuiccChannel(usbDevice: UsbDevice, usbInterface: UsbInterface): EuiccChannel? {
        val (bulkIn, bulkOut) = usbInterface.getIoEndpoints()
        if (bulkIn == null || bulkOut == null) return null
        val conn = usbManager.openDevice(usbDevice) ?: return null
        if (!conn.claimInterface(usbInterface, true)) return null
        return EuiccChannelImpl(
            context.getString(R.string.usb),
            FakeUiccPortInfoCompat(FakeUiccCardInfoCompat(EuiccChannelManager.USB_CHANNEL_ID)),
            intrinsicChannelName = usbDevice.productName,
            UsbApduInterface(
                conn,
                bulkIn,
                bulkOut,
                context.preferenceRepository.verboseLoggingFlow
            ),
            context.preferenceRepository.verboseLoggingFlow,
            context.preferenceRepository.ignoreTLSCertificateFlow,
        )
    }

    override fun cleanup() {
        seService?.shutdown()
        seService = null
    }
}