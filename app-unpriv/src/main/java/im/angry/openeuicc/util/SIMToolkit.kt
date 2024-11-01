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

fun intentSTK(slotId: Int) = Intent().apply {
    action = Intent.ACTION_MAIN
    flags = Intent.FLAG_ACTIVITY_NEW_TASK
    component = ComponentName("com.android.stk", "com.android.stk.StkMain${slotId + 1}")
    addCategory(Intent.CATEGORY_LAUNCHER)
}