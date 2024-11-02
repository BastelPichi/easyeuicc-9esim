package im.angry.openeuicc.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException

object SIMToolkit {
    private const val PKG_NAME = "com.android.stk"

    fun isInstalled(context: Context): Boolean = context.let {
        try {
            it.packageManager.getPackageInfo(PKG_NAME, 0)
            true
        } catch (_: NameNotFoundException) {
            false
        }
    }

    fun intent(context: Context, slotId: Int): Intent? {
        val intent = context.packageManager.getLaunchIntentForPackage(PKG_NAME) ?: return null
        if (intent.component?.shortClassName == ".StkMain1" && slotId == 1) {
            intent.component = ComponentName(PKG_NAME, "$PKG_NAME.StkMain2")
        }
        return intent
    }
}
