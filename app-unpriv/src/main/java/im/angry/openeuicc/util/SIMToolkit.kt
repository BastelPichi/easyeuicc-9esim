package im.angry.openeuicc.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException

fun isInstalledSTK(context: Context): Boolean =
    try {
        context.packageManager.getPackageInfo("com.android.stk", 0)
        true
    } catch (_: NameNotFoundException) {
        false
    }

fun intentSTK(slotId: Int?) = Intent().apply {
    val pkgName = "com.android.stk"
    action = Intent.ACTION_MAIN
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
    component = ComponentName(
        pkgName,
        when (slotId) {
            0 -> "${pkgName}.StkMain1"
            1 -> "${pkgName}.StkMain2"
            else -> "${pkgName}.StkMain"
        },
    )
    addCategory(Intent.CATEGORY_LAUNCHER)
}