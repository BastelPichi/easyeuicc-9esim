package im.angry.openeuicc.util

import android.text.InputFilter

fun allowedInputFilter(predicate: (Char) -> Boolean) =
    InputFilter { source, start, end, _, _, _ ->
        source.substring(start until end).filter(predicate)
    }

fun Char.isHex() = isDigit() || uppercaseChar() in 'A'..'F'