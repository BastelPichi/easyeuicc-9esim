package im.angry.openeuicc.ui.wizard

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
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
        confirmationCode.editText!!.addTextChangedListener { updateInputCompleteness() }
        imei.editText!!.addTextChangedListener { updateInputCompleteness() }
        confirmationCode.setEndIconOnClickListener {
            onConfirmationCodeEndIconClick(confirmationCode)
            validate()
        }
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
        val layouts = arrayOf(smdp, matchingId, imei)
        for (layout in layouts) layout.isErrorEnabled = layout.error != null
        inputComplete = layouts.all { it.error == null }
        refreshButtons()
    }

    private fun validate() {
        smdp.error = smdp.editText!!.text?.let {
            if (it.isEmpty()) return@let getString(R.string.download_wizard_error_address_required)
            if (it.contains("://")) return@let getString(R.string.download_wizard_error_cannot_url)
            if (!isFQDN(it)) return@let getString(R.string.download_wizard_error_address_incorrect_format)
            null
        }
        matchingId.error = matchingId.editText!!.text?.let {
            if (isMatchingID(it)) return@let null
            getString(R.string.download_wizard_error_matching_id_incorrect_format)
        }
        confirmationCode.error = confirmationCode.editText!!.let {
            if (it.text.isEmpty()) return@let null
            val passed = when (it.inputType and EditorInfo.TYPE_MASK_CLASS) {
                EditorInfo.TYPE_CLASS_NUMBER -> it.text.all(Char::isDigit)
                EditorInfo.TYPE_CLASS_TEXT -> true
                else -> false
            }
            if (passed) return@let null
            getString(R.string.download_wizard_error_confirmation_code_incorrect_format)
        }
        imei.error = imei.editText!!.text?.let {
            if (it.isEmpty()) return@let null
            if (it.length == 15 && it.all(Char::isDigit) && luhnValid(it)) return@let null
            getString(R.string.download_wizard_error_imei_incorrect_format)
        }
    }

    private fun onConfirmationCodeEndIconClick(layout: TextInputLayout) {
        val editText = layout.editText ?: return
        fun getDrawable(@DrawableRes resId: Int) =
            ContextCompat.getDrawable(requireActivity(), resId)
        editText.inputType = when (editText.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            EditorInfo.TYPE_CLASS_TEXT -> InputType.TYPE_CLASS_NUMBER
            else -> EditorInfo.TYPE_NULL
        }
        layout.endIconDrawable = when (editText.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER -> getDrawable(R.drawable.ic_format_number)
            EditorInfo.TYPE_CLASS_TEXT -> getDrawable(R.drawable.ic_format_text)
            else -> null
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

private fun luhnValid(input: CharSequence) = input
    .map(Char::digitToInt)
    .mapIndexed { index, digit -> if (index % 2 == 0) digit else digit * 2 }
    .sumOf { if (it > 9) it - 9 else it }
    .rem(10) == 0