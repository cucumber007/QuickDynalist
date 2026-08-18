package com.louiskirsch.quickdynalist

import android.content.Context
import com.louiskirsch.quickdynalist.objectbox.DynalistItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SyncLog {
    private const val PREFS = "sync_log"
    private const val KEY_ERRORS = "errors"
    private const val KEY_FULL_SYNC_STATE = "full_sync_state"
    private const val MAX_ERRORS = 50
    private const val OPERATION_VISIBILITY_DELAY_MS = 5_000L

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    data class Report(val currentState: String, val history: String)

    fun delayOperationForVisibility() {
        Thread.sleep(OPERATION_VISIBILITY_DELAY_MS)
    }

    @Synchronized
    fun markFullSyncQueued() {
        DynalistApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_FULL_SYNC_STATE, "Queued").apply()
    }

    @Synchronized
    fun markFullSyncRunning() {
        DynalistApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_FULL_SYNC_STATE, "Running").apply()
    }

    @Synchronized
    fun markFullSyncIdle() {
        DynalistApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_FULL_SYNC_STATE).apply()
    }

    @Synchronized
    fun recordError(job: String, throwable: Throwable?) {
        val message = throwable?.let { formatThrowable(it) } ?: "Unknown error"
        val entry = "${dateFormat.format(Date())}  $job\n$message"
        val prefs = DynalistApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val errors = prefs.getString(KEY_ERRORS, "")!!
                .split("\n\n")
                .filter { it.isNotBlank() }
                .toMutableList()
        errors.add(0, entry)
        prefs.edit().putString(KEY_ERRORS, errors.take(MAX_ERRORS).joinToString("\n\n")).apply()
    }

    private fun formatThrowable(throwable: Throwable): String {
        val type = throwable.javaClass.simpleName
        val message = throwable.localizedMessage
                ?.takeUnless { it.isBlank() }
                ?: throwable.message?.takeUnless { it.isBlank() }
        return message?.let { "$type: $it" } ?: type
    }

    fun buildReport(context: Context): Report {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val fullSyncState = prefs.getString(KEY_FULL_SYNC_STATE, null)
        val pendingItems = DynalistItem.box.all.filter { it.syncJob != null }
        val pendingJobs = pendingItems.groupBy { it.syncJob!! }
        val itemQueue = if (pendingJobs.isEmpty()) {
            "No pending item jobs."
        } else {
            pendingJobs.entries.joinToString("\n\n") { (jobId, items) ->
                val preview = items.take(5).joinToString("\n") { item ->
                    val title = item.name.takeUnless { it.isBlank() } ?: "(empty item)"
                    "  • ${title.take(120)}"
                }
                val more = if (items.size > 5) "\n  … ${items.size - 5} more" else ""
                "Job $jobId: ${items.size} item(s)\n$preview$more"
            }
        }

        val syncQueue = fullSyncState?.let { "Full sync: $it\n\n" } ?: ""
        val errors = prefs.getString(KEY_ERRORS, "")!!
                .takeUnless { it.isBlank() }
                ?: "No job errors recorded."

        return Report(
                currentState = "$syncQueue$itemQueue",
                history = errors
        )
    }
}
