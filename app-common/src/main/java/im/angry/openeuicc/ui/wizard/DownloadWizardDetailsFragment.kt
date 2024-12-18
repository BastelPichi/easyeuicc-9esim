package im.angry.openeuicc.ui.wizard

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.common.R

class DownloadWizardDetailsFragment : DownloadWizardActivity.DownloadWizardStepFragment() {
    companion object {
        const val FIELD_SMDP = "smdp"
        const val FIELD_MATCHING_ID = "matchingId"
        const val FIELD_CONFIRMATION_CODE = "confirmationCode"
        const val FIELD_IMEI = "imei"
    }

    private var inputComplete = false

    override val hasNext: Boolean
        get() = inputComplete
    override val hasPrev: Boolean
        get() = true

    private lateinit var smdp: EditText
    private lateinit var matchingId: EditText
    private lateinit var confirmationCode: EditText
    private lateinit var imei: EditText

    override fun beforeNext() {
        state.smdp = smdp.text.toString().trim()
        // Treat empty inputs as null -- this is important for the download step
        state.matchingId = matchingId.text.toString().trim().ifBlank { null }
        state.confirmationCode = confirmationCode.text.toString().trim().ifBlank { null }
        state.imei = imei.text.toString().ifBlank { null }
    }

    override fun createNextFragment(): DownloadWizardActivity.DownloadWizardStepFragment =
        DownloadWizardProgressFragment()

    override fun createPrevFragment(): DownloadWizardActivity.DownloadWizardStepFragment =
        DownloadWizardMethodSelectFragment()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_download_details, container, false).apply {
        smdp = requireViewById<TextInputLayout>(R.id.profile_download_server).editText!!
        matchingId = requireViewById<TextInputLayout>(R.id.profile_download_code).editText!!
        confirmationCode = requireViewById<TextInputLayout>(R.id.profile_download_confirmation_code).editText!!
        imei = requireViewById<TextInputLayout>(R.id.profile_download_imei).editText!!
        smdp.addTextChangedListener { updateInputCompleteness() }
    }

    private fun updateInputCompleteness() {
        inputComplete = Patterns.DOMAIN_NAME.matcher(smdp.text).matches()
        refreshButtons()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(FIELD_SMDP, smdp.text.toString())
        outState.putString(FIELD_MATCHING_ID, matchingId.text.toString())
        outState.putString(FIELD_CONFIRMATION_CODE, confirmationCode.text.toString())
        outState.putString(FIELD_IMEI, imei.text.toString())
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        smdp.setText(savedInstanceState?.getString(FIELD_SMDP) ?: state.smdp)
        matchingId.setText(savedInstanceState?.getString(FIELD_MATCHING_ID) ?: state.matchingId)
        confirmationCode.setText(savedInstanceState?.getString(FIELD_CONFIRMATION_CODE) ?: state.confirmationCode)
        imei.setText(savedInstanceState?.getString(FIELD_IMEI) ?: state.imei)
        updateInputCompleteness()
    }
}