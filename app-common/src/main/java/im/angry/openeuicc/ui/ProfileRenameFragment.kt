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
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.common.R
import im.angry.openeuicc.service.EuiccChannelManagerService.Companion.waitDone
import im.angry.openeuicc.util.*
import kotlinx.coroutines.launch
import net.typeblog.lpac_jni.LocalProfileAssistant

class ProfileRenameFragment : BaseMaterialDialogFragment(), EuiccChannelFragmentMarker {
    companion object {
        const val TAG = "ProfileRenameFragment"
        const val FIELD_ICCID = "iccid"
        const val FIELD_CURRENT_NAME = "currentName"
        const val FIELD_EDITED_NAME = "editedName"

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

    private val iccid by lazy {
        requireArguments().getString(FIELD_ICCID)!!
    }

    private val currentName by lazy {
        requireArguments().getString(FIELD_CURRENT_NAME)!!
    }

    private val editedName: String
        get() = editText.text.toString().trim()

    private var renaming = false
        set(value) {
            progress.isIndeterminate = value
            progress.visibility = if (value) View.VISIBLE else View.GONE
            field = value
        }

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

        editText.addTextChangedListener {
            val isUnchanged = currentName == editedName
            dialog!!.setCancelable(isUnchanged)
            dialog!!.setCanceledOnTouchOutside(isUnchanged)
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
                if (!renaming) lifecycleScope.launch {
                    renaming = true
                    invokeRename()
                    renaming = false
                }
                true
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(FIELD_EDITED_NAME, editedName)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        editText.setText(savedInstanceState?.getString(FIELD_EDITED_NAME) ?: currentName)
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

    private suspend fun invokeRename() {
        ensureEuiccChannelManager()
        euiccChannelManagerService.waitForForegroundTask()

        val throwable = euiccChannelManagerService
            .launchProfileRenameTask(slotId, portId, iccid, editedName)
            .waitDone()

        val toastResId = when (throwable) {
            is LocalProfileAssistant.ProfileNameTooLongException ->
                R.string.profile_rename_too_long
            is LocalProfileAssistant.ProfileNameIsInvalidUTF8Exception ->
                R.string.profile_rename_encoding_error
            is Throwable ->
                R.string.profile_rename_failure
            else -> {
                notifyEuiccProfilesChanged()
                try {
                    dismiss()
                } catch (e: IllegalStateException) {
                    // Ignored
                }
                when {
                    editedName.isEmpty() ->
                        R.string.profile_rename_restore_defaults
                    editedName == currentName ->
                        R.string.profile_rename_unchanged
                    else -> null
                }
            }
        }

        if (toastResId != null) Toast
            .makeText(requireContext(), toastResId, Toast.LENGTH_LONG)
            .show()
    }
}