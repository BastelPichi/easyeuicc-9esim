package im.angry.openeuicc.core

import net.typeblog.lpac_jni.LocalProfileAssistant

class EuiccChannelWrapper(orig: EuiccChannel) : EuiccChannel by orig {
    private var _inner: EuiccChannel? = orig

    private val channel: EuiccChannel
        get() {
            if (_inner == null) {
                throw IllegalStateException("This wrapper has been invalidated")
            }

            return _inner!!
        }

    private val lpaDelegate = lazy {
        LocalProfileAssistantWrapper(channel.lpa)
    }

    override val lpa: LocalProfileAssistant by lpaDelegate

    fun invalidateWrapper() {
        _inner = null

        if (lpaDelegate.isInitialized()) {
            (lpa as LocalProfileAssistantWrapper).invalidateWrapper()
        }
    }
}