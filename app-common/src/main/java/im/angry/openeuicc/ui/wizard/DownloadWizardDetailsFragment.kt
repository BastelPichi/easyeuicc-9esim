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
                it.isEmpty() -> null
                isMatchingID(it) -> null
                else -> getString(R.string.download_wizard_error_matching_id_invalid_format)
            }
        }
        imei.error = imei.editText!!.text?.let {
            when {
                it.isEmpty() -> null
                it.length == 15 && luhnValid(it) -> null
                else -> getString(R.string.download_wizard_error_imei_invalid_format)
            }
        }
    }
}

private fun isFQDN(input: CharSequence) =
    input.length < 255 && input.count { it == '.' } > 2 && input.split('.').all { part ->
        part.isNotEmpty() && part.length < 64 && part.all { it.isLetterOrDigit() || it == '-' }
    }

private fun isMatchingID(input: CharSequence) =
    input.all { it.isLetterOrDigit() || it == '-' }

private fun luhnValid(number: CharSequence, mod: Int = 10): Boolean {
    if (!number.all(Char::isDigit)) return false
    var checksum = 0
    for (i in number.length - 1 downTo 0 step 2) {
        checksum += number[i] - '0'
    }
    for (i in number.length - 2 downTo 0 step 2) {
        val n: Int = (number[i] - '0') * 2
        checksum += if (n > 9) n - 9 else n
    }
    return checksum % mod == 0
}