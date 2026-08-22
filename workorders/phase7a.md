# Work order: Phase 7, Stage A — notification access + sample harvest

Status: CANCELLED (2026-08-23) — phase 7 declined by the owner. Do NOT execute any task below.
The v6 migration slot this file planned was never built; phase 11 (workorders/phase11.md) reuses v6.
Phase reference: PLAN.md → Phase 7 (first of two stages; parsing + merge is Stage B)

## Goal

Catch payment-app notifications LIVE (they have no inbox to scan later) and store them as
device-only samples, so the architect can write real parsing templates in Stage B. No
transactions are created in this stage — harvest only.

## Architect-owned files just added (do NOT alter their logic)

- `capture/NotificationParser.kt` — package allowlist + template skeleton (empty by design).
- `app/src/test/.../capture/NotificationParserTest.kt` — must pass unmodified.

## Privacy rules (hard requirements, same spirit as phase 2 stage A)

- Raw notification text is stored ON DEVICE ONLY (a table that phase 8 sync — if ever
  built — must exclude). Everything DISPLAYED or COPIED goes through `SmsMask.mask` first.
- Only packages in `NotificationParser.paymentPackages` are ever read; every other app's
  notifications are ignored before any text is touched. No logging of notification content.

## Tasks

### 1. Database v6 (Room migration 5→6, hand-written, NO destructive fallback)

```sql
CREATE TABLE notification_samples (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  packageName TEXT NOT NULL,
  title TEXT NOT NULL,
  text TEXT NOT NULL,
  postedAt INTEGER NOT NULL,
  resolvedAt INTEGER
);
CREATE INDEX index_notification_samples_postedAt ON notification_samples(postedAt);
```

Entity + DAO (insert, observeUnresolved newest-first, dismiss(id, at), pruneKeepNewest(200)).

### 2. NotificationListenerService

- Manifest: service with `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and the
  `android.service.notification.NotificationListenerService` intent filter, exported=false
  is fine here (the system binds listeners regardless of exported — this is the documented
  pattern for listeners, unlike SMS receivers).
- `onNotificationPosted`: if `NotificationParser.isPaymentApp(sbn.packageName)` — extract
  `EXTRA_TITLE`, `EXTRA_TEXT`, and `EXTRA_BIG_TEXT` (prefer big text when present) from
  the notification extras, insert a sample row, then prune to the newest 200. Skip
  group-summary notifications (FLAG_GROUP_SUMMARY). Wrap in a coroutine off the main
  thread. NEVER throw out of the callback.

### 3. Settings → "Notification capture (setup)" screen

- Status line: enabled/disabled via NotificationManagerCompat.getEnabledListenerPackages.
- Explanation text + button opening `ACTION_NOTIFICATION_LISTENER_SETTINGS` (the system
  page where the user grants access — we cannot request it as a runtime dialog).
- List of unresolved samples, newest first: package label, `SmsMask.mask(title)`,
  `SmsMask.mask(text)`, time; per-row Dismiss.
- **"Copy masked samples"** button: up to 30 newest unresolved as
  `[packageName]` / masked title / masked text, blank line between; snackbar confirm.

### 4. Quality bar

- `./gradlew test` green (incl. new NotificationParserTest, unmodified).
- Migration 5→6 on top of live data; everything intact.
- Smoke: enable access via the settings screen, make one GPay payment (or any payment
  producing an app notification), sample appears masked; copy fills the clipboard masked.

## Acceptance

- [ ] Migration 5→6 preserves all live data; schema v6 exported.
- [ ] Listener records ONLY allowlisted packages' notifications; nothing else.
- [ ] Samples display and copy masked-only; raw text never leaves the device.
- [ ] Enable/disable status reflects reality; disabled state degrades gracefully.
- [ ] No transaction rows are created anywhere in this stage.
- [ ] All architect-owned tests pass unmodified.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

(Fill in after execution.)
