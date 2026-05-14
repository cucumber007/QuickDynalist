package com.louiskirsch.quickdynalist.compat.anko

import android.app.AlertDialog
import android.content.Context
import androidx.annotation.StringRes

class AlertBuilder(private val context: Context) {
    @StringRes
    var titleResource: Int? = null

    @StringRes
    var messageResource: Int? = null

    private var okAction: (() -> Unit)? = null

    internal fun setOkButton(action: () -> Unit) {
        okAction = action
    }

    fun show(): AlertDialog {
        val dialogBuilder = AlertDialog.Builder(context)
        titleResource?.let { dialogBuilder.setTitle(it) }
        messageResource?.let { dialogBuilder.setMessage(it) }
        dialogBuilder.setPositiveButton(android.R.string.ok) { _, _ -> okAction?.invoke() }
        return dialogBuilder.show()
    }
}

fun Context.alert(block: AlertBuilder.() -> Unit): AlertBuilder {
    return AlertBuilder(this).apply(block)
}

fun AlertBuilder.okButton(action: () -> Unit) {
    this.setOkButton(action)
}
