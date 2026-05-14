package com.louiskirsch.quickdynalist.compat.anko

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.Fragment
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

private val ankoExecutor: ExecutorService = Executors.newCachedThreadPool()
private val mainHandler = Handler(Looper.getMainLooper())

fun doAsync(task: () -> Unit) {
    ankoExecutor.execute(task)
}

fun <T> doAsyncResult(task: () -> T): Future<T> {
    return ankoExecutor.submit(Callable { task() })
}

fun Context.uiThread(task: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) task() else mainHandler.post(task)
}

fun Activity.uiThread(task: () -> Unit) = (this as Context).uiThread(task)

fun Fragment.uiThread(task: () -> Unit) {
    val ctx = context
    if (ctx != null) {
        ctx.uiThread(task)
    } else {
        mainHandler.post(task)
    }
}
