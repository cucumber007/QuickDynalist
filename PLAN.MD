# Plan: Replace Anko with a compatibility API

## Goal

Remove Anko dependency while keeping call sites mostly unchanged by providing a small internal compatibility API that mimics the used Anko methods.

---

## 1) Inventory and scope

Current project usage includes these Anko features:

- Threading
  - `doAsync { ... }`
  - `uiThread { ... }`
  - `doAsyncResult { ... }` (imported in `InboxConfigurationFragment`)
- UI feedback
  - `toast(...)`
  - `longToast(...)`
- Dialog DSL
  - `alert { ... }`
  - `okButton { ... }`
  - (potentially `yesButton` / `noButton` if present later)
- Intent helpers
  - `browse(...)`
- Utility extensions
  - `colorAttr(...)`
  - `find<T>(id)`
- Collection helper
  - `forEachWithIndex`

Files touched by these usages are in `app/src/main/java/com/louiskirsch/quickdynalist/**`.

---

## 2) Strategy

Create an internal package, e.g.:

- `com.louiskirsch.quickdynalist.compat.anko`

Provide drop-in equivalents with matching names/signatures where practical, so most call sites only need import changes.

This allows migration in two phases:

1. Add compatibility layer and switch imports.
2. Remove Anko dependency from Gradle.

---

## 3) Compatibility API design

### 3.1 Threading API

Implement:

- `fun doAsync(task: () -> Unit)`
- `fun Fragment.uiThread(task: () -> Unit)`
- `fun Activity.uiThread(task: () -> Unit)`
- `fun Context.uiThread(task: () -> Unit)`
- `fun <T> doAsyncResult(task: () -> T): Deferred<T>` (or a tiny custom future wrapper)

Implementation options:

- Preferred: Kotlin coroutines with `Dispatchers.Default` and `Dispatchers.Main`.
- Minimal-risk: plain executors + `Handler(Looper.getMainLooper())`.

Recommendation: executor + main handler first (behavior closest to old fire-and-forget), then optional coroutine cleanup later.

### 3.2 Toast API

Implement extension overloads for `Context`, `Activity`, `Fragment`:

- `toast(@StringRes resId: Int)`
- `toast(text: CharSequence)`
- `longToast(@StringRes resId: Int)`
- `longToast(text: CharSequence)`

Return `Toast` so call sites like `.show()` keep working.

### 3.3 Alert DSL API

Implement a lightweight builder compatible with current use:

- `Context.alert { ... }`
- builder fields used now:
  - `titleResource`
  - `messageResource`
- builder methods:
  - `okButton { ... }`
  - `show()`

Backed by `AlertDialog.Builder`.

(Optionally include `yesButton`/`noButton` for future safety.)

### 3.4 Intent helper

Implement:

- `fun Context.browse(url: String): Boolean`

Use `Intent.ACTION_VIEW` with try/catch and return success boolean.

### 3.5 View/util helpers

Implement:

- `fun Context.colorAttr(@AttrRes attr: Int): Int`
- `fun <T : View> Activity.find(@IdRes id: Int): T`
- `fun <T : View> View.find(@IdRes id: Int): T`

### 3.6 Collection helper

Implement:

- `inline fun <T> Iterable<T>.forEachWithIndex(action: (Int, T) -> Unit)`

---

## 4) Migration steps

1. Add compatibility files under `compat/anko`.
2. Replace imports:
   - from `org.jetbrains.anko...`
   - to `com.louiskirsch.quickdynalist.compat.anko...`
3. Compile and fix signature mismatches.
4. Run app and test key flows:
   - initial sync
   - item add/edit/move/delete
   - login/inbox configuration
   - settings cleanup action
   - error toasts and dialogs
5. Remove Anko dependency from `build.gradle`.
6. Clean up any no-longer-needed wildcard imports.

---

## 5) Risks and compatibility notes

- `doAsync/uiThread` lifecycle behavior is historically unsafe (callbacks may run after Fragment detach). Keep behavior for compatibility, but add null/attached guards where needed.
- Dialog DSL in project appears limited; if unknown methods are used later, extend builder incrementally.
- `forEachWithIndex` can be replaced with stdlib `forEachIndexed`; compatibility helper avoids touching logic.

---

## 6) Definition of done

- No `org.jetbrains.anko` imports remain.
- Project builds and runs.
- Existing call sites compile with either no logic changes or trivial import-only changes.
- Anko dependency removed from Gradle.
- Smoke-tested major flows above.
