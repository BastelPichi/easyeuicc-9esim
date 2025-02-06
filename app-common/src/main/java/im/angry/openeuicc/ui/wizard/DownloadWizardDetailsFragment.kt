package im.angry.openeuicc.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.common.R

class DownloadWizardDetailsFragment : DownloadWizardActivity.DownloadWizardStepFragment() {
    private var inputComplete = false

    override val hasNext: Boolean
        get() = inputComplete
    override val hasPrev: Boolean
        get() = true

    private lateinit var address: EditText
    private lateinit var matchingId: EditText
    private lateinit var confirmationCode: EditText
    private lateinit var imei: EditText

    private fun saveState() {
        state.activationCode.let {
            it.address = address.text.toString().trim()
            // Treat empty inputs as null -- this is important for the download step
            it.matchingId = matchingId.text.toString().trim()
            it.confirmationCode = confirmationCode.text.toString().trim()
            it.imei = imei.text.toString().trim()
        }
    }

    override fun beforeNext() = saveState()

    override fun createNextFragment(): DownloadWizardActivity.DownloadWizardStepFragment =
        DownloadWizardProgressFragment()

    override fun createPrevFragment(): DownloadWizardActivity.DownloadWizardStepFragment =
        DownloadWizardMethodSelectFragment()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_download_details, container, false).apply {
        address = requireViewById<TextInputLayout>(R.id.profile_download_server).editText!!
        matchingId = requireViewById<TextInputLayout>(R.id.profile_download_code).editText!!
        confirmationCode = requireViewById<TextInputLayout>(R.id.profile_download_confirmation_code).editText!!
        imei = requireViewById<TextInputLayout>(R.id.profile_download_imei).editText!!

        address.addTextChangedListener { updateInputCompleteness() }
    }

    override fun onStart() {
        super.onStart()
        state.activationCode.let {
            address.setText(it.address)
            matchingId.setText(it.matchingId)
            confirmationCode.setText(it.confirmationCode)
            imei.setText(it.imei)
        }
        updateInputCompleteness()
    }

    override fun onPause() {
        super.onPause()
        saveState()
    }

    private fun updateInputCompleteness() {
        saveState()
        inputComplete = try {
            state.activationCode.validate()
            true
        } catch (e: IllegalArgumentException) {
            false
        }
        refreshButtons()
    }
}