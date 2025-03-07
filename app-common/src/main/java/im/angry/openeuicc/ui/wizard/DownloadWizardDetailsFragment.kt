package im.angry.openeuicc.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputLayout
import im.angry.openeuicc.common.R

class DownloadWizardDetailsFragment : DownloadWizardActivity.DownloadWizardStepFragment() {
    private var inputComplete = false

    override val hasNext: Boolean
        get() = inputComplete
    override val hasPrev: Boolean
        get() = true

    private lateinit var smdp: TextInputLayout
    private lateinit var matchingId: TextInputLayout
    private lateinit var confirmationCode: TextInputLayout
    private lateinit var imei: TextInputLayout

    private fun saveState() {
        state.smdp = smdp.editText!!.text.toString().trim()
        // Treat empty inputs as null -- this is important for the download step
        state.matchingId = matchingId.editText!!.text.toString().trim().ifBlank { null }
        state.confirmationCode = confirmationCode.editText!!.text.toString().trim().ifBlank { null }
        state.imei = imei.editText!!.text.toString().ifBlank { null }
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
    ): View? {
        val view = inflater.inflate(R.layout.fragment_download_details, container, false)
        smdp = view.requireViewById(R.id.profile_download_server)
        matchingId = view.requireViewById(R.id.profile_download_code)
        confirmationCode = view.requireViewById(R.id.profile_download_confirmation_code)
        imei = view.requireViewById(R.id.profile_download_imei)
        smdp.editText!!.addTextChangedListener { updateInputCompleteness() }
        matchingId.editText!!.addTextChangedListener { updateInputCompleteness() }
        imei.editText!!.addTextChangedListener { updateInputCompleteness() }
        return view
    }

    override fun onStart() {
        super.onStart()
        smdp.editText!!.setText(state.smdp)
        matchingId.editText!!.setText(state.matchingId)
        confirmationCode.editText!!.setText(state.confirmationCode)
        imei.editText!!.setText(state.imei)
        updateInputCompleteness()
    }

    override fun onPause() {
        super.onPause()
        saveState()
    }

    private fun updateInputCompleteness() {
        validate()
        val errors = arrayOf(
            smdp.error,
            matchingId.error,
            imei.error,
        )
        inputComplete = errors.all { it == null }
        refreshButtons()
    }

    private fun validate() {
        smdp.error = smdp.editText!!.text?.let {
            when {
                it.isEmpty() -> getString(R.string.download_wizard_error_address_required)
                it.contains("://") -> getString(R.string.download_wizard_error_cannot_url)
                isFQDN(it) -> null
                else -> getString(R.string.download_wizard_error_address_invalid_format)
            }
        }
        matchingId.error = matchingId.editText!!.text?.let {
            when {
                isMatchingID(it) -> null
                else -> getString(R.string.download_wizard_error_matching_id_invalid_format)
            }
        }
        imei.error = imei.editText!!.text?.let {
            when {
                it.isEmpty() -> null
                !it.all(Char::isDigit) -> getString(R.string.download_wizard_error_imei_not_numeric)
                it.length != 15 -> getString(R.string.download_wizard_error_imei_length, it.length)
                luhnValid(it) -> null
                else -> getString(R.string.download_wizard_error_imei_invalid_format)
            }
        }
    }
}

private fun isFQDN(input: CharSequence): Boolean {
    if (input.isEmpty() || input.length > 255) return false
    if (!input.contains('.')) return false
    for (label in input.split('.')) {
        if (label.isEmpty() || label.length > 63) return false
        if (label.all { it.isLetterOrDigit() || it == '-' }) continue
        return false
    }
    return true
}

private fun isMatchingID(input: CharSequence) =
    input.isEmpty() || input.all { it.isLetterOrDigit() || it == '-' }

private fun luhnValid(input: CharSequence) = input.all(Char::isDigit) && input
    .map(Char::digitToInt)
    .mapIndexed { index, digit -> if (index % 2 == 0) digit else digit * 2 }
    .sumOf { if (it > 9) it - 9 else it }
    .rem(10) == 0