package im.angry.openeuicc.ui

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import im.angry.openeuicc.common.R
import im.angry.openeuicc.service.EuiccChannelManagerService.Companion.waitDone
import im.angry.openeuicc.util.*
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

class ProfileDeleteFragment : DialogFragment(), EuiccChannelFragmentMarker {
    companion object {
        const val TAG = "ProfileDeleteFragment"
        private const val FIELD_ICCID = "iccid"
        private const val FIELD_NAME = "name"

        fun newInstance(slotId: Int, portId: Int, iccid: String, name: String): ProfileDeleteFragment {
            val instance = newInstanceEuicc(ProfileDeleteFragment::class.java, slotId, portId)
            instance.requireArguments().apply {
                putString(FIELD_ICCID, iccid)
                putString(FIELD_NAME, name)
            }
            return instance
        }
    }

    private val iccid: String
        get() = requireArguments().getString(FIELD_ICCID)!!

    private val name: String
        get() = requireArguments().getString(FIELD_NAME)!!

    private val editText by lazy {
        EditText(requireContext()).apply {
            hint = Editable.Factory.getInstance()
                .newEditable(getString(R.string.profile_delete_confirm_input, name))
        }
    }

    private val inputMatchesName: Boolean
        get() = editText.text.toString() == name

    private var toast: Toast? = null

    private var deleting = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme).apply {
            setMessage(getString(R.string.profile_delete_confirm, name))
            setView(editText)
            setPositiveButton(android.R.string.ok, null) // Set listener to null to prevent auto closing
            setNegativeButton(android.R.string.cancel, null)
        }.create()

    override fun onResume() {
        super.onResume()
        val alertDialog = dialog!! as AlertDialog
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!deleting) delete()
        }
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            if (!deleting) dismiss()
        }
    }

    private fun delete() {
        if (!inputMatchesName) {
            toast?.cancel()
            toast = Toast.makeText(
                requireContext(),
                getString(R.string.toast_profile_delete_unmatched, name),
                Toast.LENGTH_LONG
            )
            toast!!.show()
            return
        }
        deleting = true
        val alertDialog = dialog!! as AlertDialog
        alertDialog.setCanceledOnTouchOutside(false)
        alertDialog.setCancelable(false)
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false

        requireParentFragment().lifecycleScope.launch {
            ensureEuiccChannelManager()
            euiccChannelManagerService.waitForForegroundTask()
            euiccChannelManagerService.launchProfileDeleteTask(slotId, portId, iccid).onStart {
                if (parentFragment is EuiccProfilesChangedListener) {
                    // Trigger a refresh in the parent fragment -- it should wait until
                    // any foreground task is completed before actually doing a refresh
                    (parentFragment as EuiccProfilesChangedListener).onEuiccProfilesChanged()
                }

                try {
                    dismiss()
                } catch (e: IllegalStateException) {
                    // Ignored
                }
            }.waitDone()
        }
    }
}