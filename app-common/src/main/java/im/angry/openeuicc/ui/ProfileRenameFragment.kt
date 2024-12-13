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
import net.typeblog.lpac_jni.LocalProfileAssistant.ProfileNicknameException as NicknameException
import net.typeblog.lpac_jni.LocalProfileAssistant.ProfileNicknameException.Kind as SetFailedKind

class ProfileRenameFragment : BaseMaterialDialogFragment(), EuiccChannelFragmentMarker {
    companion object {
        val SPACE_PATTERN = Regex("\\s", RegexOption.MULTILINE)

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
        toast?.cancel()
        val editedName = editText.text.toString().trim()
            // replace \s as space (inc. new line and spaces)
            .replace(SPACE_PATTERN, "\u0020")
        val toastMessage = when {
            editedName.isEmpty() -> getString(R.string.toast_profile_name_restore_defaults)
            editedName == currentName -> getString(R.string.toast_profile_name_is_unchanged)
            else -> getString(R.string.toast_profile_name_changed, currentName, editedName)
        }
        toast = Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG).also {
            it.show()
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
                val resId = when (e.kind) {
                    SetFailedKind.NicknameTooLong -> R.string.toast_profile_name_too_long
                    SetFailedKind.InvalidUTF8Sequence -> R.string.toast_profile_name_encode_failed
                }
                toast?.cancel()
                toast = Toast.makeText(requireContext(), resId, Toast.LENGTH_LONG).also {
                    it.show()
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