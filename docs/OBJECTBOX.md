# ObjectBox

In QuickDynalist, ObjectBox is the local persistence layer for Dynalist data. The app initializes an ObjectBox `BoxStore` in `DynalistApp`, then uses ObjectBox boxes and queries to store and read entities such as:

- `DynalistItem`
- `DynalistDocument`
- `DynalistFolder`
- `DynalistTag`
- `DynalistItemFilter`

During sync, the Dynalist API provides the remote state, while ObjectBox holds the local state. `SyncJob` reconciles remote files and document nodes into ObjectBox records so the app can show and modify a local copy of the user's Dynalist data.

## Local and remote shape

The local ObjectBox model is not identical to the remote Dynalist API model. Some fields are mostly renamed when stored locally, but the important differences are structural and app-specific.

Remote Dynalist data comes from API response models such as `File` from `file/list` and `Node` from `doc/read`. Local ObjectBox data is stored as app entities such as `DynalistFolder`, `DynalistDocument`, and `DynalistItem`.

The meaningful local additions are:

- Local identity: ObjectBox assigns a local `clientId`; the remote identity is preserved separately as `serverFileId` and `serverItemId`.
- Stored relations: remote parent/child IDs are turned into ObjectBox `ToOne`/`ToMany` relations so the app can navigate the outline locally.
- UI state: fields such as `hidden`, `isInbox`, `isBookmark`, `isChecklist`, and `areCheckedItemsVisible` support app behavior that is not just the remote Dynalist node payload.
- Derived metadata: parsed dates, images, symbols, tags, links, and backlinks are stored for local querying and display.
- Document bookkeeping: `DynalistDocument.version` stores the last synced remote document version.

The rest of the mapping is mostly straightforward field copying with local names, for example remote node content/note/checked timestamps becoming local item name/note/checked/date fields.

## Records and items

In this documentation, a local ObjectBox entry is usually called a **record** or an **item**.

For QuickDynalist sync, the important local entity is `DynalistItem`, so the clearest phrasing is:

- local item
- local record
- locally stored `DynalistItem`

When sync documentation says a remote Dynalist node is matched to a local item, it means the code is trying to find the existing local `DynalistItem` object that represents the same server-side Dynalist node.
