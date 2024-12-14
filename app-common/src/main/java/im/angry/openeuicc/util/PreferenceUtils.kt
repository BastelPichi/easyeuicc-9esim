package im.angry.openeuicc.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.Fragment
import im.angry.openeuicc.OpenEuiccApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prefs")

val Context.preferenceRepository: PreferenceRepository
    get() = (applicationContext as OpenEuiccApplication).appContainer.preferenceRepository

val Fragment.preferenceRepository: PreferenceRepository
    get() = requireContext().preferenceRepository

class PreferenceRepository(private val context: Context) {
    // Expose flows so that we can also handle default values
    // ---- Profile Notifications ----
    val notificationDownloadFlow = bindFlow("notification_download", true)
    val notificationDeleteFlow = bindFlow("notification_delete", true)
    val notificationSwitchFlow = bindFlow("notification_switch", false)

    // ---- Advanced ----
    val disableSafeguardFlow = bindFlow("disable_safeguard_removable_esim", false)
    val verboseLoggingFlow = bindFlow("verbose_logging", false)

    // ---- Developer Options ----
    val developerOptionsEnabledFlow = bindFlow("developer_options_enabled", false)
    val unfilteredProfileListFlow = bindFlow("unfiltered_profile_list", false)
    val ignoreTLSCertificateFlow = bindFlow("ignore_tls_certificate", false)

    private fun bindFlow(name: String, defaultValue: Boolean) =
        bindFlow(booleanPreferencesKey(name), defaultValue)

    private fun <T> bindFlow(key: Preferences.Key<T>, defaultValue: T) =
        BoundPreference(context.dataStore, key, defaultValue)
}

class BoundPreference<T>(
    private val store: DataStore<Preferences>,
    private val key: Preferences.Key<T>,
    defaultValue: T
) : Flow<T> {
    private val flow = store.data.map { it[key] ?: defaultValue }

    suspend fun emit(value: T) = store.edit { it[key] = value }

    override suspend fun collect(collector: FlowCollector<T>) = flow.collect(collector)
}