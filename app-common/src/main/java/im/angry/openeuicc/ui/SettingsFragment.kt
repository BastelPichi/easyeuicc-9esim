package im.angry.openeuicc.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import im.angry.openeuicc.common.R
import im.angry.openeuicc.util.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking


open class SettingsFragment: PreferenceFragmentCompat() {
    private val developerPref by lazy {
        findPreference<PreferenceCategory>("pref_developer")!!
    }

    // Hidden developer options switch
    private var numClicks = 0
    private var lastClickTimestamp = -1L
    private var lastToast: Toast? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_settings, rootKey)

        lifecycleScope.launch {
            // Show / hide developer preference based on whether it is enabled
            preferenceRepository.developerOptionsEnabledFlow
                .onEach { developerPref.isVisible = it }
                .collect()
        }

        findPreference<Preference>("pref_info_app_version")?.apply {
            summary = requireContext().selfAppVersion

            // Enable developer options when this is clicked for 7 times
            setOnPreferenceClickListener(::onAppVersionClicked)
        }

        findPreference<Preference>("pref_advanced_language")?.apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@apply
            isVisible = true
            intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
        }

        findPreference<Preference>("pref_advanced_logs")?.apply {
            intent = Intent(requireContext(), LogsActivity::class.java)
        }

        findPreference<EditTextPreference>("pref_developer_max_segment_size")?.apply {
            val mssFlow = preferenceRepository.maxSegmentSizeFlow

            lifecycleScope.launch {
                mssFlow.onEach { text = it.toString() }.collect()
            }

            setOnPreferenceChangeListener { _, newValue ->
                // SGP.22 v2.2.2, 2.5.5 Segmented Bound Profile Package (Page 33 of 268)
                // https://www.gsma.com/solutions-and-impact/technologies/esim/wp-content/uploads/2020/06/SGP.22-v2.2.2.pdf#page=33
                //
                // Each segment of this list that is up to 255 bytes is transported in one APDU.
                // Larger TLVs are sent in blocks of 255 bytes for the first blocks and a last block that MAY be shorter.
                text = runBlocking {
                    val value = newValue as String
                    when {
                        value.isEmpty() -> mssFlow.removePreference()
                        value.toInt() in 32..255 -> mssFlow.updatePreference(value.toInt())
                    }
                    mssFlow.first().toString()
                }
                false
            }

            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_NUMBER
                it.selectAll()
            }
        }

        findPreference<CheckBoxPreference>("pref_notifications_download")
            ?.bindBooleanFlow(preferenceRepository.notificationDownloadFlow)

        findPreference<CheckBoxPreference>("pref_notifications_delete")
            ?.bindBooleanFlow(preferenceRepository.notificationDeleteFlow)

        findPreference<CheckBoxPreference>("pref_notifications_switch")
            ?.bindBooleanFlow(preferenceRepository.notificationSwitchFlow)

        findPreference<CheckBoxPreference>("pref_advanced_disable_safeguard_removable_esim")
            ?.bindBooleanFlow(preferenceRepository.disableSafeguardFlow)

        findPreference<CheckBoxPreference>("pref_advanced_verbose_logging")
            ?.bindBooleanFlow(preferenceRepository.verboseLoggingFlow)

        findPreference<CheckBoxPreference>("pref_developer_unfiltered_profile_list")
            ?.bindBooleanFlow(preferenceRepository.unfilteredProfileListFlow)

        findPreference<CheckBoxPreference>("pref_ignore_tls_certificate")
            ?.bindBooleanFlow(preferenceRepository.ignoreTLSCertificateFlow)
    }

    override fun onStart() {
        super.onStart()
        setupRootViewInsets(requireView().requireViewById(R.id.recycler_view))
    }

    @Suppress("UNUSED_PARAMETER")
    private fun onAppVersionClicked(pref: Preference): Boolean {
        if (developerPref.isVisible) return false
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp >= 1000) {
            numClicks = 1
        } else {
            numClicks++
        }
        lastClickTimestamp = now

        if (numClicks == 7) {
            lifecycleScope.launch {
                preferenceRepository.developerOptionsEnabledFlow.updatePreference(true)

                lastToast?.cancel()
                Toast.makeText(
                    requireContext(),
                    R.string.developer_options_enabled,
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else if (numClicks > 1) {
            lastToast?.cancel()
            lastToast = Toast.makeText(
                requireContext(),
                getString(R.string.developer_options_steps, 7 - numClicks),
                Toast.LENGTH_SHORT
            )
            lastToast!!.show()
        }

        return true
    }

    private fun CheckBoxPreference.bindBooleanFlow(flow: PreferenceFlowWrapper<Boolean>) {
        lifecycleScope.launch {
            flow.collect { isChecked = it }
        }

        setOnPreferenceChangeListener { _, newValue ->
            runBlocking {
                flow.updatePreference(newValue as Boolean)
            }
            true
        }
    }

    protected fun mergePreferenceOverlay(overlayKey: String, targetKey: String) {
        val overlayCat = findPreference<PreferenceCategory>(overlayKey)!!
        val targetCat = findPreference<PreferenceCategory>(targetKey)!!

        val prefs = buildList {
            for (i in 0..<overlayCat.preferenceCount) {
                add(overlayCat.getPreference(i))
            }
        }

        prefs.forEach {
            overlayCat.removePreference(it)
            targetCat.addPreference(it)
        }

        overlayCat.parent?.removePreference(overlayCat)
    }
}