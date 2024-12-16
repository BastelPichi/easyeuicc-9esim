package im.angry.openeuicc.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.DialogFragment
import com.google.android.material.color.DynamicColors
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.EuiccProfilesChangedListener

abstract class BaseMaterialDialogFragment: DialogFragment() {
    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val wrappedContext = ContextThemeWrapper(requireContext(), R.style.Theme_OpenEUICC)
        val dynamicWrappedContext = DynamicColors.wrapContextIfAvailable(wrappedContext)
        return inflater.cloneInContext(dynamicWrappedContext)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also {
            it.window?.requestFeature(Window.FEATURE_NO_TITLE)
            it.window?.setBackgroundDrawableResource(R.drawable.dialog_background)
        }
    }

    /**
     * Trigger a refresh in the parent fragment -- it should wait until
     * any foreground task is completed before actually doing a refresh
     */
    protected fun notifyEuiccProfilesChanged() {
        val fragment = parentFragment
        if (fragment is EuiccProfilesChangedListener) fragment.onEuiccProfilesChanged()
    }
}