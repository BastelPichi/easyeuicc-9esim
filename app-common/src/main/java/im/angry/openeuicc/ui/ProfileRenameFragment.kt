package im.angry.openeuicc.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.common.R
import im.angry.openeuicc.service.EuiccChannelManagerService.Companion.waitDone
import im.angry.openeuicc.util.*
import kotlinx.coroutines.launch
import net.typeblog.lpac_jni.LocalProfileAssistant.ProfileNicknameException as NicknameException
import net.typeblog.lpac_jni.LocalProfileAssistant.ProfileNicknameException.Kind as SetFailedKind

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

    private val editedName: String
        get() = editText.text.toString().trim()

    private val iccid by lazy {
        requireArguments().getString(FIELD_ICCID)!!
    }

    private val currentName by lazy {
        requireArguments().getString(FIELD_CURRENT_NAME)!!
    }

    private var renaming = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile_rename, container, false).apply {
            toolbar = requireViewById(R.id.toolbar)
            editText = requireViewById<TextInputLayout>(R.id.profile_rename_new_name).editText!!
            progress = requireViewById(R.id.progress)
        }
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
        editText.addTextChangedListener {
            val isUnchanged = it.toString().trim() == currentName
            dialog!!.setCancelable(isUnchanged)
            dialog!!.setCanceledOnTouchOutside(isUnchanged)
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

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        toast = Toast.makeText(
            requireContext(),
            R.string.toast_profile_name_is_unchanged,
            Toast.LENGTH_LONG
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).also {
            it.setCanceledOnTouchOutside(false)
        }
    }

    private fun rename() {
        toast = when {
            editedName.isEmpty() -> Toast.makeText(
                requireContext(),
                R.string.toast_profile_name_restore_defaults,
                Toast.LENGTH_LONG
            )

            editedName == currentName -> Toast.makeText(
                requireContext(),
                R.string.toast_profile_name_is_unchanged,
                Toast.LENGTH_LONG
            )

            else -> Toast.makeText(
                requireContext(),
                getString(R.string.toast_profile_name_changed, currentName, editedName),
                Toast.LENGTH_LONG
            )
        }

        renaming = true
        progress.isIndeterminate = true
        progress.isVisible = true

        lifecycleScope.launch {
            ensureEuiccChannelManager()
            euiccChannelManagerService.waitForForegroundTask()
            try {
                if (editedName != currentName) euiccChannelManagerService
                    .launchProfileRenameTask(slotId, portId, iccid, editedName)
                    .waitDone()
            } catch (e: NicknameException) {
                toast = when (e.kind) {
                    SetFailedKind.NicknameTooLong -> Toast.makeText(
                        requireContext(),
                        R.string.toast_profile_name_too_long,
                        Toast.LENGTH_LONG
                    )

                    SetFailedKind.InvalidUTF8Sequence -> Toast.makeText(
                        requireContext(),
                        R.string.toast_profile_name_encode_failed,
                        Toast.LENGTH_LONG
                    )
                }
                return@launch
            }

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