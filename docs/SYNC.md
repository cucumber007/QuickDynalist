# Sync implementation in QuickDynalist

This document summarizes how synchronization works in this codebase.

## High-level architecture

Sync is **[pull-first reconciliation](PULL-FIRST-RECONCILIATION.md)** from Dynalist API into local ObjectBox storage, with safeguards for local pending edits.

Main pieces:

- `Dynalist.kt`
  - decides when to trigger sync
  - tracks sync state via EventBus (`isSyncing`)
- `/jobs/SyncJob.kt`
  - performs full sync logic
- `/jobs/ItemJob.kt` + subclasses
  - local edit/move/delete/add jobs
  - mark affected items with `syncJob` so `SyncJob` won’t overwrite in-flight local changes
- `/objectbox/DynalistItem.kt`
  - item model, including `syncJob` and hierarchy helpers

## How sync is triggered

### Automatic trigger
In `Dynalist.subscribe()`:

- checks `lastFullSync` against `sync_frequency` preference (minutes)
- if authenticated and stale, calls `sync()`

### Manual trigger
UI routes (menu/shortcut/login flow) call either:

- `Dynalist.sync(isManual = true)`
- or `SyncJob.forceSync(...)`

### Force sync behavior
`SyncJob.forceSync()`:

- cancels existing jobs tagged `syncJob`
- enqueues a fresh `SyncJob(false, isManual)`
- disables unmetered-only requirement for forced run

## Job constraints and dedup

`SyncJob` is created with:

- `requireNetwork()`
- optional `setRequiresUnmeteredNetwork(requireUnmeteredNetwork)`
- `singleInstanceBy(TAG)` and tag `syncJob`

So only one sync job instance can run/enqueue at a time.

`Dynalist.sync()` additionally short-circuits if:

- already syncing (`isSyncing == true`), or
- `sync_automatic` preference is off

## Core sync algorithm (`SyncJob.onRun`)

1. Post `SyncEvent(RUNNING, isManual)` (sticky).
2. Call `file/list` to fetch remote files.
3. Split remote files into folders and editable documents.
4. Remove local documents/items that disappeared remotely (`deleteDisappearedDocuments`).
5. Call `doc/check_for_updates`; only process docs whose version increased (or unknown locally).
6. For each changed document:
   - call `doc/read`
   - reconcile nodes with local items (`syncDocument`)
   - post progress `SyncProgressEvent`.
7. Determine local items that are no longer present remotely and not locked by local jobs; delete them.
8. Recompute metadata for changed/new items (`updateMetaData`).
9. Update document version table.
10. Rebuild folder hierarchy and parent/position references.
11. Persist all changes in one transaction.
12. Fetch inbox preference (`pref/get inbox_location`) and update local inbox flags.
13. Set `lastFullSync = now`.
14. Post success events:
    - sticky `SyncEvent(NOT_RUNNING, isManual)`
    - `SyncEvent(SUCCESS, isManual)`
    - sticky `InboxEvent(configured=...)`
15. Notify widget refresh.

## Reconciliation details (`syncDocument`)

For each remote node, code tries to match an existing local item by:

1. `(serverFileId, serverItemId)` absolute server ID
2. fallback `(content, createdTime)` pair

Then:

- If matched and item has **no local pending job** (`syncJob == null`):
  - updates editable fields from server when server modified is newer
  - updates hierarchy placeholder data (`childrenIds`) for later parent/position rebuild
- If no match:
  - creates a new `DynalistItem` from server node

After node mapping:

- builds parent/child relations via `populateChildren`
- stores results, excluding rows modified locally after sync start (`getModifiedAfterSync`) to avoid clobbering concurrent local edits

## Protection against overwriting local edits

Local mutation jobs (`AddItemJob`, `EditItemJob`, `MoveItemJob`, `DeleteItemJob`, etc.) mark affected rows:

- `item.syncJob = <job id>`

`SyncJob` respects this:

- does not overwrite certain fields for items with non-null `syncJob`
- does not treat such rows as deletable "missing on server" rows

When item jobs complete, they clear `syncJob`.

## Item job ↔ sync interaction

`ItemJob.requireItemId()` may force sync when a local item still lacks server node ID:

- triggers `SyncJob.forceSync(false)`
- retries later with exponential backoff

This is important for operations that require server IDs (move/edit/delete).

## API usage

Used endpoints from `DynalistService`:

- `file/list`
- `doc/check_for_updates`
- `doc/read`
- `pref/get` (for inbox)

Item jobs separately use `doc/edit`, `inbox/add`, etc.

## Rate limiting behavior

`execRespectRateLimit()` wraps Retrofit calls:

- if response code is `TooManyRequests`, waits (default 60s), posts `RateLimitDelay`, retries once via `clone().execute()`

`SyncJob` wires this callback to show user-facing delay notification.

## Failure and retry semantics

`SyncJob`:

- `getRetryLimit() = 2`
- retries with exponential backoff (`10s * 2^n` style via jobqueue helper)
- on reaching retry limit, posts:
  - sticky `SyncEvent(NOT_RUNNING, isManual)`
  - `SyncEvent(NO_SUCCESS, isManual)`

Token invalidation during sync posts `AuthenticatedEvent(false)` and throws auth error.

## Observed design intent

- Keep local DB as offline-first cache of Dynalist state.
- Serialize full syncs and gate by connectivity preferences.
- Avoid destructive conflicts using per-item `syncJob` markers + modified-after-sync guard.
- Prefer eventual consistency over hard conflict resolution UI.
