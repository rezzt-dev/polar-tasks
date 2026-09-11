# v1.7 — complete

> combined release notes for the github publication of polar `v1.7`, covering
> both builds. written in lowercase, in english, following the same format as
> `v1.6.2`.
>
> v1.7 ships in two builds from the same codebase:
>
> - **online build** — the standard polar, with supabase cloud sync and an
>   account. detailed on its own in `release-v1.7-online.md`.
> - **offline build** — polar with no account and no network access: no
>   `INTERNET` permission, every list, task, subtask and reminder lives only on
>   the device. detailed on its own in `release-v1.7.md`.
>
> everything under "shared" applies to both builds. the "online build only" and
> "offline build only" blocks apply to just one.

---

## release title

```
v1.7 — reminders redesign, settings categories & a 100% offline build
```

## release description

### improvements — shared

- **reminders redesign**: `RemindersFragment` has been rebuilt around an upcoming-alert summary, status filters, date groups and specific empty states. reminder cards now show description, time, date, location and an actions menu, keeping completed reminders legible in every theme. a new `ReminderPresentation` filters and orders reminders by the local day and keeps due dates fresh across daylight-saving changes.
- **reusable bidirectional swipe everywhere**: `TaskSwipeHelper` gained labels, rounded backgrounds and dynamic actions for the complete / reactivate / move-to-trash gestures, and now also drives `ReminderAdapter`, `CalendarFragment` and `TrashFragment` — trash items reveal a colored background and icon when restoring to the list (right) or deleting permanently (left), matching the task list swipe. per-screen manual swipe code has been removed.
- **settings reorganized into categories**: `fragment_settings.xml` is split into dedicated views — appearance, notifications, data and help (plus account on the online build) — with back navigation that preserves the open category and menu scroll on recreation. every preference now shows an icon, a description and its current value.
- **expanded list icon catalog**: the icon picker in the list creation and editing forms grew from 16 to 40 options, with icons for shopping, education, health, fitness, travel, finance, leisure, family and home, backed by 24 new theme-compatible vector drawables.
- **always-visible priority stripe**: the left stripe on task cards now stays visible for tasks without a priority, painted with the active theme's foreground color (resolved once in the viewholder, reusing the adapter's `priorityColor`), so every card keeps the same visual detail in every theme and no recycled color can leak between rows.
- **lowercase & accent-free consistency**: settings navigation, task detail, the theme / typography / checkbox / language dialogs and the reminders agenda have been normalized to the application's lowercase, accent-free rule across `values`, `values-en-rGB`, `values-en-rUS`, `values-de` and `values-fr`, preserving `plurals` and format specifiers. the convention is now documented in `agents.md` (section 4.6) and `claude.md`.
- **versioned project context**: the `.docs/` directory — commit guidelines, release notes and the 67 agent skills — is now tracked in the repository instead of living only in local copies, alongside new commit-writing and `changelog.md` guides in `agents.md` and `claude.md`.
- **formalized license**: the license files have been rewritten for spanish intellectual property law (rdleg 1/1996) and the berne convention, with consistent license references in the readme and reinforced warranty and liability disclaimers. no functional code changes.

### improvements — online build only

- **supabase cloud sync**: polar can now sync task lists, tasks, subtasks and reminders across devices. adds an `AuthActivity` with sign-in and "forgot password" flows (sign-up is intentionally kept out of the app — accounts are created from the companion app), a first-login merge dialog (upload local data vs. discard local and use cloud only), and a `SyncManager` with push/pull, last-write-wins conflict resolution, per-table realtime `postgres_changes` subscriptions and batched upserts. a `SyncWorker` runs a 3-minute foreground chain plus a 15-minute background safety net, and an immediate sync fires whenever the app comes to the foreground. session status is observed reactively, so an expired or revoked session is detected and surfaced instead of failing silently. room migrations add `uuid` / `updatedAt` / `deletedAt` / `dirty` bookkeeping columns and ship with an instrumented migration test.
- **account & sync settings**: a new "account & sync" section shows the last successful sync time and sync errors, and offers a manual "sync now", full cloud download/overwrite actions and a lost-conflicts warning banner.

### improvements — offline build only

- **100% offline**: polar no longer needs an account or an internet connection. every list, task, subtask and reminder lives only on the device, and the app no longer declares the `INTERNET` permission. the supabase cloud sync introduced in v1.6 — sign-in, `SyncManager`, `SyncWorker`, realtime, cloud image storage and the account settings — has been removed together with the supabase, ktor and kotlinx serialization dependencies.
- **seamless upgrade from v1.6**: a new room migration (`MIGRATION_17_18`, database v18) drops the sync bookkeeping columns (`uuid`, `updatedAt`, `deletedAt`, `dirty`, `imagePath`) without losing data. lists and subtasks that were already deleted are purged for good, subtasks trashed together with their task come back when the task is restored, the autoincrement counters are preserved so alarm ids are never reused, and `tasks.listId` / `subtasks.taskId` are now indexed. an instrumented `MigrationTest` covers both the 17 → 18 and the full 14 → 18 paths.
- **no leftovers from the cloud**: on first launch after upgrading, `LegacyCloudDataCleaner` cancels the pending sync jobs still persisted by workmanager and deletes the stored sync state and the saved account session, so no tokens remain on the device.
- **safer backups**: restoring a backup now runs in a single database transaction, so a broken file can no longer wipe the current data. backups exported by v1.6 are still accepted: deleted lists and subtasks and orphaned rows are discarded instead of being brought back.

### fixes — shared

- **stale subtask overwrite**: only subtasks whose checkbox was actually toggled during an open task dialog session can write back their `completed` value, so a change made elsewhere while the dialog stays open (for example, completing the task from its notification) is no longer reverted on save.
- **recyclerview crash on swipe reset**: removed `animate().cancel()` from `resetVisuals()` in `TaskAdapter`, `HomeTaskAdapter` and `ReminderAdapter`. cancelling a view's in-flight add animation from `bind()` re-entered recyclerview's animation-finished bookkeeping mid-bind and crashed with "Tmp detached view should be removed from RecyclerView before it can be recycled"; the manual alpha / translation / scale resets already cover the stale swipe state.
- **lint errors blocking release builds**: completed the 35 missing british and us english, german and french strings; added `platform_themes.xml` in `values/` and `values-v27/` so `android:windowLightNavigationBar` is only applied from api 27 while keeping the light and dark theme inheritance; and replaced `android:tint` with `app:tint` in `item_reminder.xml` for the appcompat-compatible tint.
- **back navigation from settings sub-screens**: pressing back from a settings category now returns to the settings root, keeping the open category and menu scroll, instead of leaving the screen.

### fixes — online build only

- **realtime updates never arriving**: switched the ktor engine from `ktor-client-android` to `ktor-client-okhttp`. the android engine is built on `HttpURLConnection`, which has no websocket support, so realtime's `channel.subscribe()` failed immediately and retried forever — ordinary postgrest push/pull is plain http and worked either way.
- **double sync on cold start**: the immediate-sync trigger was firing from both the `ProcessLifecycleOwner` `onStart()` observer and the authenticated foreground `combine()` flow, racing two syncs whose `REPLACE` work policy cancelled and restarted the first. it now fires only from the foreground flow, roughly halving the wait to see fresh data after opening the app.
- **slow pulls**: `pull()`'s four per-table fetches and the tombstone remote-id lookups now run with `async` / `coroutineScope` instead of one after another, turning about four sequential round trips into one bounded by the slowest table.
- **trash purge resurrecting items**: `emptyTrash()` and `permanentDelete()` are gated on `dirty = 0`, and soft-delete now cascades to a task's active subtasks, so purging no longer brings back tasks, subtasks or reminders whose tombstone never reached the server. local tombstones that were physically deleted server-side are also detected and purged.
- **purge blocked without an account**: permanently deleting and emptying the trash left items stuck with the `still waiting to sync with the cloud` warning on a purely local install. when there is no session, the purge now runs directly through `forcePermanentDelete` / `forceEmptyTrash`.
- **pull cursor dropping rows**: the pull cursor now uses `pullStartedAt - 1` so a row written in the same millisecond as the pull started is not skipped.
- **notification actions not propagating**: the snooze notification action, recurring rollovers and other notification actions now mark rows dirty and trigger a sync, so due-date changes reach other devices — previously only completing a reminder propagated.
- **removed dead hard-delete code paths**: dropped `TaskRepository.deleteTask` / `deleteSubtask` and the `TaskDao` / `SubtaskDao` / `ReminderDao` `delete` methods, which bypassed the sync pipeline entirely.

### fixes — offline build only

- **subtasks lost when restoring a task**: sending a task to the trash no longer deletes its subtasks, so restoring it brings the whole task back.
- **alarms of deleted lists still firing**: deleting a list now removes its tasks and subtasks and cancels their pending alarms.
- **deleted items counted in the ui**: subtask progress on home cards and per-list statistics no longer include deleted subtasks or lists.
- **trashed recurring tasks coming back**: `RecurrenceWorker` now skips tasks in the trash, which were being reset to pending and scheduling alarms even though the user had deleted them.
- **snoozed due date not saved**: snoozing a task from its notification now persists the new due date, so lists, calendar and widget show the snoozed time instead of the original one.
