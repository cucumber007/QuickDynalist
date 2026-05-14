package com.louiskirsch.quickdynalist.compat.anko

import android.content.Context
import android.content.Intent
import android.net.Uri

fun Context.browse(url: String): Boolean {
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        true
    } catch (_: Exception) {
        false
    }
}
