package im.angry.openeuicc.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.common.R
import im.angry.openeuicc.service.EuiccChannelManagerService.Companion.waitDone
import im.angry.openeuicc.util.*
import kotlinx.coroutines.launch

class ProfileRenameFragment : BaseMaterialDialogFragment(), EuiccChannelFragmentMarker {
    companion object {
        const val TAG = "ProfileRenameFragment"
        const val FIELD_ICCID = "iccid"
        const val FIELD_CURRENT_NAME = "currentName"

        fun newInstance(slotId: Int, portId: Int, iccid: String, currentName: String): ProfileRenameFragment {
            val instance = newInstanceEuicc(ProfileRenameFragment::class.java, slotId, portId)
            instance.requireArguments().apply {
                putString(FIELD_ICCID, iccid)
                putString(FIELD_CURRENT_NAME, currentName)
            }
            return instance
        }
    }

    private lateinit var toolbar: Toolbar
    private lateinit var editText: EditText
    private lateinit var progress: ProgressBar

    private var toast: Toast? = null
        set(toast) {
            field?.cancel()
            field = toast
            field?.show()
        }

    private val iccid: String
        get() = requireArguments().getString(FIELD_ICCID)!!

    private val currentName: String
        get() = requireArguments().getString(FIELD_CURRENT_NAME)!!

    private val editedName: String
        get() = editText.text.toString().trim()

    private var renaming = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile_rename, container, false)

        toolbar = view.requireViewById(R.id.toolbar)
        editText = view.requireViewById<TextInputLayout>(R.id.profile_rename_new_name).let {
            it.editText!!
        }
        progress = view.requireViewById(R.id.progress)

        toolbar.inflateMenu(R.menu.fragment_profile_rename)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.apply {
            setTitle(R.string.rename)
            setNavigationOnClickListener {
                if (!renaming) dismiss()
            }
            setOnMenuItemClickListener {
                if (!renaming) rename()
                true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        editText.setText(currentName)
    }

    override fun onResume() {
        super.onResume()
        setWidthPercent(95)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also {
            it.setCanceledOnTouchOutside(false)
        }
    }

    private fun rename() {
        // SGP.22 v2.2.2 (Page 205 of 268)
        // https://www.gsma.com/solutions-and-impact/technologies/esim/wp-content/uploads/2020/06/SGP.22-v2.2.2.pdf
        // ASN.1 definition is `profileNickname [16] UTF8String (SIZE(0..64))`
        // code points <= 64 or encoded bytes <= 64?
        try {
            val length = editedName.toByteArray(Charsets.UTF_8).size
            if (length > 64) {
                toast = Toast.makeText(
                    requireContext(),
                    R.string.toast_profile_name_too_long,
                    Toast.LENGTH_LONG
                )
                return
            }
        } catch (e: CharacterCodingException) {
            toast = Toast.makeText(
                requireContext(),
                R.string.toast_profile_name_encode_failed,
                Toast.LENGTH_LONG
            )
            return
        }
        val toastMessage = when {
            editedName.isEmpty() -> getString(R.string.toast_profile_name_restore_defaults)
            editedName == currentName -> getString(R.string.toast_profile_name_not_changed)
            else -> getString(R.string.toast_profile_name_changed, currentName, editedName)
        }
        toast = Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG)

        renaming = true
        progress.isIndeterminate = true
        progress.isVisible = true

        lifecycleScope.launch {
            ensureEuiccChannelManager()
            euiccChannelManagerService.waitForForegroundTask()
            euiccChannelManagerService.launchProfileRenameTask(slotId, portId, iccid, editedName)
                .waitDone()

            if (parentFragment is EuiccProfilesChangedListener) {
                (parentFragment as EuiccProfilesChangedListener).onEuiccProfilesChanged()
            }

            try {
                dismiss()
            } catch (e: IllegalStateException) {
                // Ignored
            }
        }
    }
}