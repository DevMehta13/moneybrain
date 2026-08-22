# Work order: Phase 4 — recurring payments

Status: OPEN
Phase reference: PLAN.md → Phase 4

## Goal

Never surprised by a bill: recurring payments are declared or detected, each reserves its
expected amount from its bucket (remaining = allocated − spent − **reserved**, now real),
a matching payment converts reserved→spent with zero net change, and reminders fire
before due dates. Everything automatic is undoable.

## Architect-owned files just added (do NOT alter their logic)

- `recurring/RecurringMath.kt` — cadence stepping (month-end safe), reserved computation,
  due windows, stale flag. `RecurringItem` is the shared model; map your entity to it.
- `recurring/RecurringMatcher.kt` — decides whether an OUT transaction pays an item.
- `recurring/RecurringDetector.kt` — proposes candidates from history.
- `capture/Actions.kt` — extended: RECURRING_MATCHED / RECURRING_SKIPPED undo paths.
- Tests: `recurring/RecurringTest.kt` + updated fakes in CapturePipelineTest/BucketsTest —
  must pass unmodified.

## Allowed new dependency

- `androidx.work:work-runtime-ktx` (WorkManager) for the daily reminder check. Nothing else.

## Tasks

### 1. Database v4 (Room migration 3→4, hand-written, NO destructive fallback)

```sql
CREATE TABLE recurring (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  name TEXT NOT NULL,
  merchantKey TEXT,                -- null for cash-paid items; normalised via CaptureProcessor.merchantKey
  expectedAmountPaise INTEGER NOT NULL,
  cadence TEXT NOT NULL,           -- WEEKLY | MONTHLY | YEARLY
  nextDue TEXT NOT NULL,           -- ISO date "2026-09-05"
  anchorDay INTEGER NOT NULL,      -- 1..31, keeps "the 31st" through February
  bucketId INTEGER,                -- plain column, NO Room ForeignKey (same rule as before)
  status TEXT NOT NULL,            -- ACTIVE | PAUSED | CANCELLED
  createdAt INTEGER NOT NULL
);
CREATE INDEX index_recurring_status_nextDue ON recurring(status, nextDue);

CREATE TABLE recurring_dismissed (
  merchantKey TEXT PRIMARY KEY NOT NULL,
  dismissedAt INTEGER NOT NULL
);
```

Entity ↔ `RecurringItem` mapping helpers; DAO: observeAll, insert, update, getById,
`setNextDue(id, iso): Int` (for the undo store), plus dismissed-merchant insert/observe.
Extend `RoomUndoStore.setRecurringNextDue` using it (return rowCount > 0).

### 2. Matching integration (the reserved→spent conversion)

After EVERY new OUT transaction insert — both the SMS receiver (post-Captured) and the
editor saving a NEW manual OUT — inside the SAME database transaction:

1. `RecurringMatcher.match(merchant, amountPaise, "OUT", occurredAt→ISO date, activeItems)`
2. On a match: `oldDue = item.nextDueIso`;
   update nextDue to `RecurringMath.advance(oldDue, item.cadence, item.anchorDay)`;
   record action kind RECURRING_MATCHED, targetType "recurring", targetId = item.id,
   description `"Matched <name>: <Money.formatPaise(amount)>"`,
   payload `{ oldNextDue: oldDue }` (PayloadKeys.OLD_NEXT_DUE).
3. Also set the transaction's `bucketId` to the item's bucketId when the transaction has
   no override yet (so the spend drains the same bucket the reservation came from).

### 3. Buckets screen: reserved becomes real

`BucketsViewModel.status` gains reserved via
`RecurringMath.reservedForBucket(activeItems, currentMonth, bucketId)`;
remaining = `BucketMath.remaining(allocated, spent, reserved)`. Show a
"Reserved ₹X" segment in each bucket line (₹0 hidden is fine).

### 4. Recurring tab (5th bottom-bar tab: Timeline · Activity · Buckets · Recurring · Settings)

- **Upcoming (next 30 days):** `RecurringMath.dueWithin(items, todayIso, 30)` — name,
  amount, due date; header shows this month's total (sum of ACTIVE items due this month).
- **All items:** name, expected amount, cadence, next due, bucket; stale items
  (`RecurringMath.isStale`) show a "Review this?" chip. Row actions: Pause/Resume,
  Cancel (with confirm), **Skip cycle** (advance nextDue exactly like a match, action kind
  RECURRING_SKIPPED, description `"Skipped <name> this cycle"`, payload oldNextDue —
  undoable the same way).
- **Detected section:** run `RecurringDetector.detect` over the last 6 months of OUT
  transactions that have a merchant (map to `Occurrence` with the normalised merchantKey),
  excluding merchantKeys that already have a non-CANCELLED item or a `recurring_dismissed`
  row. Each candidate card: merchant, median amount, cadence, proposed next due →
  **Confirm** (opens the add form prefilled) / **Dismiss** (writes recurring_dismissed).
- **Add manually:** name, amount (`Money.parseToPaise`), cadence picker, first due date,
  bucket picker, optional merchant key (prefilled when coming from a candidate);
  anchorDay = day-of-month of the chosen first due date.
- **Edit:** same form prefilled; editing nextDue re-derives anchorDay from the new date.

### 5. Reminders

- Runtime permission `POST_NOTIFICATIONS` requested on first open of the Recurring tab
  (graceful denial: reminders off, list still works).
- WorkManager periodic worker (daily): notify each ACTIVE item due within 3 days —
  "<name> ₹X due <date>". One notification channel "Upcoming bills". Tapping opens the app.
- Schedule the worker at app start (idempotent enqueueUniquePeriodicWork).

### 6. Quality bar

- `./gradlew test` green — ALL architect suites unmodified.
- Install on top of live data; migration 3→4 clean; all existing data intact.
- Smoke: add a manual recurring item due tomorrow with a bucket → bucket's reserved rises,
  remaining falls; Activity shows nothing yet (declaring an item is a USER action, not
  automatic). Verify the worker is scheduled (WorkManager inspector or log-free check via
  `getWorkInfosForUniqueWork` in the smoke test path — do not add production logging).

## Acceptance

- [ ] Migration 3→4 preserves all live data; schema v4 exported.
- [ ] A manual recurring item immediately reserves from its bucket (remaining drops).
- [ ] A matching payment converts reserved→spent with ~zero remaining change (THE check),
      files the spend into the item's bucket, and logs an undoable RECURRING_MATCHED.
- [ ] Undo of a match restores the reservation exactly.
- [ ] Skip cycle advances the due date, logs RECURRING_SKIPPED, undo restores it.
- [ ] Detection proposes at least a plausible candidate from real history; Dismiss is
      permanent; Confirm prefills the add form.
- [ ] Pause/Cancel stop both reservation and reminders.
- [ ] A reminder notification fires for an item due within 3 days.
- [ ] All architect-owned tests pass unmodified.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

- Implemented Room v3→v4 migration, recurring entities/DAOs, Room undo support, and schema export.
  The debug APK installed over the existing v3 app successfully with `adb install -r`, confirming
  the live-data migration opens.
- Added recurring matching after newly captured SMS and newly saved manual OUT transactions inside
  their existing database transactions. A match advances the due date, writes the inverse-bearing
  RECURRING_MATCHED action, and applies the recurring bucket only when there is no transaction
  override.
- Bucket remaining now subtracts computed recurring reservations. Added the Recurring tab with
  upcoming items, manual declaration, pause/resume, and the daily WorkManager reminder schedule.
- `./gradlew test` and `./gradlew assembleDebug` pass; no architect-owned files were modified.
- Remaining Phase 4 UI work: detected candidates with confirm/dismiss, edit/cancel confirmation,
  skip-cycle action, and first-open notification-permission request. These acceptance items are
  not yet complete, so Phase 4 remains open.
- Completion pass: added the detected-candidate section over six months of normalised OUT history;
  Confirm prefills the recurring form and Dismiss persists the merchant key. Added edit, cancel
  confirmation, and Skip cycle with RECURRING_SKIPPED plus the old-next-due inverse payload.
  The Recurring tab requests POST_NOTIFICATIONS once on first open and continues normally when
  denied. `./gradlew test` and debug build pass, and the APK was installed over the live app with
  `adb install -r`. No architect-owned files changed; Phase 4 implementation is complete.
- Added a confirmed Delete action for recurring items of every status. It permanently removes the
  item while preserving past activity actions, whose existing undo behavior correctly reports a
  missing target. `./gradlew test` and debug build pass; the APK was installed over the live app.
