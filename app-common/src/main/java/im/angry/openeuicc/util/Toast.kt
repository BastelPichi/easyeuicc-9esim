package im.angry.openeuicc.util

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

fun Activity.makeLongToast(@StringRes resId: Int): Toast =
    Toast.makeText(this, resId, Toast.LENGTH_LONG)

fun Activity.makeShortToast(@StringRes resId: Int): Toast =
    Toast.makeText(this, resId, Toast.LENGTH_SHORT)

fun Context.makeLongToast(@StringRes resId: Int): Toast =
    Toast.makeText(this, resId, Toast.LENGTH_LONG)

fun Context.makeShortToast(@StringRes resId: Int): Toast =
    Toast.makeText(this, resId, Toast.LENGTH_SHORT)