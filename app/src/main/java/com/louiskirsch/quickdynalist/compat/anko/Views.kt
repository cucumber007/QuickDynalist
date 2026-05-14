package com.louiskirsch.quickdynalist.compat.anko

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.IdRes

fun Context.colorAttr(@AttrRes attr: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

@Suppress("UNCHECKED_CAST")
fun <T : View> Activity.find(@IdRes id: Int): T = findViewById(id) as T

@Suppress("UNCHECKED_CAST")
fun <T : View> View.find(@IdRes id: Int): T = findViewById(id) as T
