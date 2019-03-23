package de.westnordost.streetcomplete.ktx

import androidx.appcompat.app.AlertDialog
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

fun AlertDialog.Builder.setResumeButtons(
    cont:Continuation<Boolean>, textIdPositive: Int, textIdNegative: Int): AlertDialog.Builder {
    setPositiveButton(textIdPositive) { _,_ -> cont.resume(true) }
    setNegativeButton(textIdNegative) { _,_ -> cont.resume(false) }
    return this
}

fun AlertDialog.Builder.setResumeButtons(
    cont:Continuation<Boolean>, textPositive: CharSequence, textNegative: CharSequence)
        : AlertDialog.Builder {
    setPositiveButton(textPositive) { _,_ -> cont.resume(true) }
    setNegativeButton(textNegative) { _,_ -> cont.resume(false) }
    return this
}
