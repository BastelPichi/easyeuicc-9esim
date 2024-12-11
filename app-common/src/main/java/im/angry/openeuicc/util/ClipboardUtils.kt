package im.angry.openeuicc.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import im.angry.openeuicc.common.R

fun Context.setClipboard(label: String, callback: () -> ClipData) {
    getSystemService(ClipboardManager::class.java)!!
        .setPrimaryClip(callback())
    Toast.makeText(this, getString(R.string.toast_copied, label), Toast.LENGTH_SHORT)
        .show()
}