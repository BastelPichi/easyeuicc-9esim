package im.angry.openeuicc.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.ArrayRes
import im.angry.easyeuicc.R
import im.angry.openeuicc.core.EuiccChannelManager

class SIMToolkit(private val context: Context) {
    private val slotSelection = getComponentNames(R.array.sim_toolkit_slot_selection)

    private val slots = buildMap {
        put(0, getComponentNames(R.array.sim_toolkit_slot_1))
        put(1, getComponentNames(R.array.sim_toolkit_slot_2))
    }

    private val packageNames = buildSet {
        addAll(slotSelection.map { it.packageName })
        addAll(slots.values.flatten().map { it.packageName })
    }

    private val activities by lazy {
        val pm = context.packageManager
        packageNames.flatMap { packageName ->
            try {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
                packageInfo.activities!!.filter { it.exported }
                    .map { ComponentName(it.packageName, it.name) }
            } catch (_: PackageManager.NameNotFoundException) {
                emptyList()
            }
        }
    }

    private val launchIntent by lazy {
        val pm = context.packageManager
        for (packageName in packageNames) {
            try {
                return@lazy pm.getLaunchIntentForPackage(packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                continue
            }
        }
        null
    }

    private fun getComponentNames(@ArrayRes id: Int) = context.resources.getStringArray(id)
        .mapNotNull(ComponentName::unflattenFromString)

    private fun findComponentName(slotId: Int): ComponentName? {
        val components = (slots[slotId] ?: emptySet()) + slotSelection
        return components.find(activities::contains)
    }

    fun isAvailable(slotId: Int) = when (slotId) {
        -1 -> false
        EuiccChannelManager.USB_CHANNEL_ID -> false
        else -> intent(slotId) != null
    }

    fun intent(slotId: Int) = findComponentName(slotId).let {
        if (it == null) return@let launchIntent
        Intent(Intent.ACTION_MAIN, null).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            component = it
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
    }
}
