---
name: Replace Anko dependency
overview: The project depends only on [anko-commons](https://github.com/Kotlin/anko) ([app/build.gradle](app/build.gradle)). Usage is limited to helpers for threading, Toasts, dialogs, opening URLs, one `find` helper, and two collection helpers—several imports are unused and can be removed during migration.
todos:
  - id: pick-primitives
    content: Choose toast/dialog/url/threading strategies from options above
    status: pending
  - id: stdlib-find-browse
    content: Replace forEachWithIndex, decorView find, browse URLs
    status: pending
  - id: dialogs
    content: Rewrite Dynalist + NavigationActivity alert DSL to AlertDialog/Material
    status: pending
  - id: async-migrate
    content: Migrate doAsync/uiThread call sites (Fragments → Application → jobs/utils)
    status: pending
  - id: remove-anko
    content: Drop anko-commons dependency and unused imports (colorAttr, alert, doAsyncResult)
    status: pending
isProject: false
---

# Full replacement of Anko (`anko-commons`)

## Current dependency

- [app/build.gradle](app/build.gradle): `implementation 'org.jetbrains.anko:anko-commons:0.10.7'`

Anko is [deprecated/unmaintained](https://github.com/Kotlin/anko); removing it reduces risk and aligns with modern Android APIs.

---

## 1. Inventory by Anko API

### A. `toast` / `longToast` (Context / FragmentActivity extensions)

Used for short user feedback.

| Location | Usage |
|----------|--------|
| [Dynalist.kt](app/src/main/java/com/louiskirsch/quickdynalist/Dynalist.kt) | `context.toast(...)` (strings + string resources) |
| [MainActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/MainActivity.kt) | `toast(...)`, `longToast(...)` |
| [AdvancedItemActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/AdvancedItemActivity.kt) | `toast(...)` |
| [ProcessTextActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/ProcessTextActivity.kt) | `toast(...)` |
| [NavigationActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/NavigationActivity.kt) | `toast(...)` |
| [BaseItemListFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/BaseItemListFragment.kt) | `context!!.toast(...)`, `longToast(...)` |
| [ItemListFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/ItemListFragment.kt) | `context!!.toast(...)` |
| [SyncShortcutActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/SyncShortcutActivity.kt) | `toast` |
| [InsertBarFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/InsertBarFragment.kt) | `toast` |
| [LoginFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/LoginFragment.kt) | `toast` |
| [WizardActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/WizardActivity.kt) | `toast` |
| [SettingsActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/SettingsActivity.kt) | `context!!.toast(...)` |
| [InboxConfigurationFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/InboxConfigurationFragment.kt) | `activity!!.longToast(...).show()` |
| [utils/ImageCache.kt](app/src/main/java/com/louiskirsch/quickdynalist/utils/ImageCache.kt) | `context.toast(...)` |
| [utils/DynalistEditActionHelper.kt](app/src/main/java/com/louiskirsch/quickdynalist/utils/DynalistEditActionHelper.kt) | `toast` |
| [utils/SpeechRecognitionHelper.kt](app/src/main/java/com/louiskirsch/quickdynalist/utils/SpeechRecognitionHelper.kt) | `toast` |

---

### B. `alert { }` / `okButton` (dialog DSL)

| Location | Usage |
|----------|--------|
| [Dynalist.kt](app/src/main/java/com/louiskirsch/quickdynalist/Dynalist.kt) | `context.alert { titleResource; messageResource; okButton {}; show() }` |
| [NavigationActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/NavigationActivity.kt) | `alert { messageResource; okButton { dynalist.sync() }; show() }` |

[TagManagerFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/TagManagerFragment.kt) imports `alert` but does **not** use it (safe to drop with migration).

---

### C. `browse(url)` (open URL in browser)

| Location | Usage |
|----------|--------|
| [ItemListFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/ItemListFragment.kt) | Dynalist deep link |
| [LoginFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/LoginFragment.kt) | external URL |

---

### D. `doAsync { }` / `uiThread { }` (background + main thread)

| Location | Usage |
|----------|--------|
| [ProcessTextActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/ProcessTextActivity.kt) | Upload work off main thread, dismiss snackbar + UI on completion |
| [ItemListFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/ItemListFragment.kt) | Box/ObjectBox + share intent + checklist toggles |
| [FilteredItemListFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/FilteredItemListFragment.kt) | `box.put` / `box.remove` / filter persistence |
| [TagManagerFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/TagManagerFragment.kt) | Tag suggestions query + UI update |
| [SettingsActivity.kt](app/src/main/java/com/louiskirsch/quickdynalist/SettingsActivity.kt) | Tag cleanup |
| [InboxConfigurationFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/InboxConfigurationFragment.kt) | Network `execute()` + UI result |
| [jobs/ItemJob.kt](app/src/main/java/com/louiskirsch/quickdynalist/jobs/ItemJob.kt) | `addToDatabase()` off calling thread |
| [DynalistApp.kt](app/src/main/java/com/louiskirsch/quickdynalist/DynalistApp.kt) | `upgrade()` path: clear jobs + sync |
| [ViewModels.kt](app/src/main/java/com/louiskirsch/quickdynalist/ViewModels.kt) | `eagerInitialize` batch |
| [utils/ImageCache.kt](app/src/main/java/com/louiskirsch/quickdynalist/utils/ImageCache.kt) | Bitmap write to disk |

Note: [InboxConfigurationFragment.kt](app/src/main/java/com/louiskirsch/quickdynalist/InboxConfigurationFragment.kt) imports `doAsyncResult` but does **not** use it.

---

### E. `find(id)` on `View` ([Extensions.kt](app/src/main/java/com/louiskirsch/quickdynalist/utils/Extensions.kt))

```kotlin
val AppCompatActivity.actionBarView: View
    get() = window.decorView.find(R.id.action_bar_container)
```

This is Anko’s `find`, not ObjectBox’s `Query.find()`.

---

### F. `forEachWithIndex` ([anko collections](https://github.com/Kotlin/anko/blob/master/anko/library/static/commons/anko-commons/src/main/java/Collections.kt))

| Location | Usage |
|----------|--------|
| [jobs/MoveItemJob.kt](app/src/main/java/com/louiskirsch/quickdynalist/jobs/MoveItemJob.kt) | `currentChildren.forEachWithIndex { i, it -> ... }` |
| [jobs/SyncJob.kt](app/src/main/java/com/louiskirsch/quickdynalist/jobs/SyncJob.kt) | `newDocuments.forEachWithIndex { idx, doc -> ... }` |

**Unused imports** (can delete when dropping Anko): [DeleteItemJob.kt](app/src/main/java/com/louiskirsch/quickdynalist/jobs/DeleteItemJob.kt), [BulkEditItemJob.kt](app/src/main/java/com/louiskirsch/quickdynalist/jobs/BulkEditItemJob.kt), [EditItemJob.kt](app/src/main/java/com/louiskirsch/quickdynalist/jobs/EditItemJob.kt) import `forEachWithIndex` but do not call it.

---

### G. Wildcard `import org.jetbrains.anko.*`

Hides which helpers are used; actual symbols from those files are the ones listed above (`toast`, `longToast` in MainActivity/AdvancedItemActivity; BaseItemListFragment/ItemListFragment/FilteredItemListFragment as above).

---

### H. Likely dead Anko-only imports

| File | Note |
|------|------|
| [Location.kt](app/src/main/java/com/louiskirsch/quickdynalist/Location.kt) | `colorAttr` imported; code uses [resolveColorAttribute](app/src/main/java/com/louiskirsch/quickdynalist/utils/Extensions.kt) instead |

---

## 2. Replacement options (you choose per category)

### Toasts

- **Option 1 — Small local extensions** (minimal deps): `fun Context.toast(@StringRes id: Int)`, overload for `CharSequence`, using `Toast.makeText(..., LENGTH_SHORT/LONG).show()`. Fixes `longToast(...).show()` by matching intended behavior (long duration once).
- **Option 2 — Snackbar** where there is a suitable anchor (already used elsewhere): more Material-consistent but not drop-in everywhere (Application/`Dynalist` context has no anchor).
- **Option 3 — AndroidX / material wrappers**: e.g. `Snackbar` from Material only where it fits; keep `Toast` for edge cases.

### Threading (`doAsync` / `uiThread`)

- **Option 1 — Kotlin Coroutines** add `kotlinx-coroutines-android` (+ core): `lifecycleScope` / `viewLifecycleOwner.lifecycleScope` in Activities/Fragments; `CoroutineScope(SupervisorJob() + Dispatchers.Default)` in `Application` for `DynalistApp.upgrade`; `withContext(Dispatchers.IO)` for disk/network; structured cancellation vs fire-and-forget today.
- **Option 2 — Executor + main Handler**: `Executors.newSingleThreadExecutor()` or existing APIs + `Handler(Looper.getMainLooper()).post { }` / `runOnUiThread` — no new dependency, more boilerplate, weaker lifecycle ties.
- **Option 3 — Hybrid**: coroutines in UI layer only; keep simple executors in `ItemJob` / `ImageCache` if you want smallest diff.

### Dialogs (`alert` / `okButton`)

- **Option 1 — `AlertDialog.Builder` / `MaterialAlertDialogBuilder`**: imperative API; set title/message/positive button; same UX as today.
- **Option 2 — AndroidX `DialogFragment`**: heavier refactor; only if you want lifecycle-safe dialogs long-term.

### Opening URLs (`browse`)

- **Option 1 — `Intent.ACTION_VIEW` + `Uri.parse`**: wrap in `fun Context.openUrl(url: String)` with try/catch for `ActivityNotFoundException`.
- **Option 2 — Chrome Custom Tabs**: better for auth/marketing links; optional dependency.

### `find(R.id.…)` on decor view

- **Option 1 — `findViewById`**: `window.decorView.findViewById(R.id.action_bar_container)` (requires casting or generic inline helper).
- **Option 2 — Migrate overlay/toolbar access** when you move off kotlin-android-extensions (out of scope unless you want it bundled).

### `forEachWithIndex`

- **Option 1 — Kotlin stdlib**: replace with `forEachIndexed { index, element -> ... }` (behavior equivalent for `List`).

---

## 3. Suggested migration order (after you pick options)

1. Add chosen primitives (e.g. `Context.toast` / `openUrl` in [Extensions.kt](app/src/main/java/com/louiskirsch/quickdynalist/utils/Extensions.kt) or a small `Ui.kt`).
2. Replace `forEachWithIndex` → `forEachIndexed` and remove dead imports.
3. Replace `find` → `findViewById`.
4. Replace `browse` → `Intent` helper.
5. Replace `alert`/`okButton` with Material/AppCompat dialogs.
6. Replace `doAsync`/`uiThread` with your chosen threading approach file-by-file (Fragments/Activities first, then `ItemJob`, `DynalistApp`, `ViewModels`, `ImageCache`).
7. Remove `anko-commons` from Gradle and verify no remaining `org.jetbrains.anko` imports.

---

## 4. Scope note

Replacing Anko does **not** require migrating off `kotlin-android-extensions` or synthetic views; those are separate. If you add coroutines, align Kotlin Gradle plugin / stdlib versions with your existing [build.gradle](build.gradle) toolchain.
