package com.louiskirsch.quickdynalist.compat.anko

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

fun Context.toast(@StringRes resId: Int): Toast = Toast.makeText(this, resId, Toast.LENGTH_SHORT).apply { show() }
fun Context.toast(text: CharSequence): Toast = Toast.makeText(this, text, Toast.LENGTH_SHORT).apply { show() }
fun Context.longToast(@StringRes resId: Int): Toast = Toast.makeText(this, resId, Toast.LENGTH_LONG).apply { show() }
fun Context.longToast(text: CharSequence): Toast = Toast.makeText(this, text, Toast.LENGTH_LONG).apply { show() }

fun Activity.toast(@StringRes resId: Int): Toast = (this as Context).toast(resId)
fun Activity.toast(text: CharSequence): Toast = (this as Context).toast(text)
fun Activity.longToast(@StringRes resId: Int): Toast = (this as Context).longToast(resId)
fun Activity.longToast(text: CharSequence): Toast = (this as Context).longToast(text)

fun Fragment.toast(@StringRes resId: Int): Toast = requireContext().toast(resId)
fun Fragment.toast(text: CharSequence): Toast = requireContext().toast(text)
fun Fragment.longToast(@StringRes resId: Int): Toast = requireContext().longToast(resId)
fun Fragment.longToast(text: CharSequence): Toast = requireContext().longToast(text)
