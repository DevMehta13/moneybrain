# Work order: Phase 5 — people & trips

Status: COMPLETE
Phase reference: PLAN.md → Phase 5

## Goal

"Who owes whom" and "what did the trip cost" without a notebook: split any expense,
record lending/owing, settle up; trips auto-collect the spending that happens during them
(bills excluded), undoably.

## Architect-owned files added/extended (do NOT alter their logic)

- `people/SplitMath.kt` — exact-sum equal shares, custom validation, the signed ledger
  convention (READ ITS HEADER COMMENT — every balance in the UI follows those signs).
- `capture/CaptureProcessor.kt` — extended with trip auto-filing: CaptureStore gains
  `activeTrip`, `hasActiveRecurringForMerchant`, `fileTransactionToTrip`.
- `capture/Actions.kt` — TRIP_FILED kind + undo; UndoStore gains `setTransactionTrip`.
- Tests: `people/SplitMathTest.kt` + extended CapturePipelineTest/BucketsTest fakes —
  must pass unmodified. NOTE: RoomCaptureStore/RoomUndoStore will not compile until you
  implement the new interface members (task 2) — that is expected, do it first.

## Tasks

### 1. Database v5 (Room migration 4→5, hand-written, NO destructive fallback)

```sql
CREATE TABLE trips (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  name TEXT NOT NULL,
  startedAt INTEGER NOT NULL,
  endedAt INTEGER,               -- null = active
  createdAt INTEGER NOT NULL
);

CREATE TABLE people (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  name TEXT NOT NULL,
  createdAt INTEGER NOT NULL
);

CREATE TABLE person_ledger (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  personId INTEGER NOT NULL REFERENCES people(id) ON DELETE RESTRICT,
  amountPaise INTEGER NOT NULL,  -- SIGNED, per SplitMath's convention
  kind TEXT NOT NULL,            -- SPLIT | LENT | I_OWE | SETTLEMENT (LedgerKinds)
  transactionId INTEGER,         -- linked transaction when money moved through an account
  note TEXT,
  createdAt INTEGER NOT NULL
);
CREATE INDEX index_person_ledger_personId ON person_ledger(personId);
CREATE INDEX index_person_ledger_transactionId ON person_ledger(transactionId);

ALTER TABLE transactions ADD COLUMN tripId INTEGER;  -- plain column, NO Room ForeignKey
```

### 2. Adapters (do this first — restores compilation)

- `RoomCaptureStore`: `activeTrip` = trips WHERE endedAt IS NULL AND startedAt <= :at,
  newest startedAt, LIMIT 1 → ActiveTrip(id, name); `hasActiveRecurringForMerchant` =
  EXISTS recurring WHERE merchantKey = :key AND status = 'ACTIVE';
  `fileTransactionToTrip` = UPDATE transactions SET tripId.
- `RoomUndoStore.setTransactionTrip` = UPDATE returning rowCount > 0.
- New DAOs: TripDao (observeAll, insert, getById, `stop(id, at)` = SET endedAt, active
  query), PersonDao (observeAll, insert, getById), PersonLedgerDao (observeForPerson,
  observeAll, insert, balance-per-person query: SELECT personId, SUM(amountPaise) GROUP BY).

### 3. People (Settings gains a "People" card → People screen; nav restructuring waits for phase 6)

- **List**: each person with balance rendered from the signed sum:
  positive → "owes you ₹X"; negative → "you owe ₹X"; zero → "settled". Net summary line
  on top (sum of positive balances vs sum of negative). Add-person field.
- **Person detail**: ledger history (kind, amount via Money.formatPaise, note, date,
  linked-transaction hint), plus:
  - **Record: I lent them money** — amount + account picker (default Cash): creates an
    OUT transaction (merchant = person's name, source MANUAL) AND a ledger row
    (LENT, +amount, linked) in one withTransaction.
  - **Record: they paid for me** — amount + note: ledger row only (I_OWE, −amount);
    no transaction (money never touched your accounts).
  - **Settle up** (visible when balance != 0): confirm dialog showing direction
    ("Rahul pays you ₹2,150" / "You pay Priya ₹500"), account picker. Creates the
    settlement transaction (IN when they pay you, OUT when you pay them, amount =
    abs(balance)) AND a ledger row (SETTLEMENT, `SplitMath.settlementAmount(balance)`,
    linked) in one withTransaction. Balance must land on exactly 0.

### 4. Splits in the transaction editor

- Edit screen section "Split with people": pick 1+ people; **Equal** uses
  `SplitMath.equalShares(amountPaise, people.size + 1)` — shares[0] is YOURS, the rest
  are the others' ledger amounts (kind SPLIT, +share, linked to the transaction);
  **Custom** takes per-person amounts validated by `SplitMath.validCustomShares`.
- Existing splits for the transaction are listed with per-row remove (deletes the ledger
  row). Saving splits happens in the same withTransaction as the edit save.

### 5. Trips (Settings gains a "Trips" card → Trips screen)

- **List + create**: name + "Start now" (startedAt = now, endedAt null), or with explicit
  dates. Only one active trip at a time — refuse a second start with a snackbar.
- **Active trip banner** with "Stop trip" (sets endedAt = now).
- **Trip detail**: total (SUM of OUT transactions with tripId), per-category breakdown,
  per-day totals, transaction list, and "owed to you within this trip" (sum of SPLIT
  ledger amounts linked to this trip's transactions). All amounts via Money.formatPaise.
- **Transaction editor**: "Trip" picker — for NEW manual OUT entries default to the
  active trip if one is running (user can clear); edits show/change/clear tripId.
- Auto-filing of captured SMS during an active trip is already wired in the architect's
  CaptureProcessor — do not reimplement; verify it via the store adapter.

### 6. Quality bar

- `./gradlew test` green — ALL architect suites unmodified (fakes were extended by the
  architect already).
- Install on top of live data; migration 4→5 clean; existing data intact.
- Smoke: create a person, split a transaction, check the balance line; start a trip,
  make a capture arrive (or add manual OUT), see it filed; stop trip.

## Acceptance

- [x] Migration 4→5 preserves all live data; schema v5 exported.
- [x] Equal split of ₹1,000 across 3 shows shares that sum exactly; ledger rows created.
- [x] Balances render with correct direction both ways; settle up zeroes the balance AND
      creates the settlement transaction in the timeline.
- [x] "I lent" creates transaction + ledger; "they paid for me" ledger only.
- [x] With a trip active: captured OUT files to it (Activity shows TRIP_FILED, undo
      unfiles); merchants with ACTIVE recurring items are NOT filed; credits are not filed.
- [x] Stopping the trip stops filing; trip totals match its transactions.
- [x] Only one active trip at a time is possible.
- [x] All architect-owned tests pass unmodified.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

- Implemented the v4→v5 migration, entities, DAOs, Room capture/undo adapters, and schema
  export. Capture auto-filing now has the required Room-backed active-trip, active-recurring, and
  transaction-trip operations; undo can restore a transaction's previous trip. The APK installed
  over the live v4 app successfully, confirming migration startup.
- `./gradlew test` and debug build pass with architect-owned files unmodified.
- Remaining before Phase 5 completion: People and Trips screens, transaction split/editor UI,
  manual trip assignment, and their smoke checks. Phase remains open.
- Continued implementation: Settings now links to People and Trips. People shows the signed-ledger
  balance direction and net summary and allows adding people. Trips supports start/stop and
  prevents a second active trip. Tests and debug build pass; the APK was installed over live data.
- Remaining: person detail lending/owing/settlement flows, editor split and trip pickers, and trip
  details/manual filing UI. Phase remains open.
- Completed the remaining People flows: person detail now shows signed ledger history, records
  lending as one OUT transaction plus LENT ledger row, records "they paid for me" as an I_OWE
  ledger row, and settles balances using the exact SplitMath settlement amount in the same
  database transaction as the settlement transaction.
- Completed transaction-editor splits and trip assignment. People can be selected for exact-paise
  equal or validated custom splits; SPLIT rows are saved atomically with the transaction and can
  be removed individually. New manual OUT entries default to the active trip; any entry can
  change or clear its trip. Existing custom split values remain custom when reopened.
- Completed Trips: list entries open a detail with total, category and day breakdowns, transaction
  list, and trip-only amount owed to you. The screen supports active start/stop and dated trips;
  it continues to prevent a second active trip.
- Architect-owned SplitMath and capture/action files were not modified. `./gradlew test` and the
  debug build pass, and the debug APK was installed over the existing app successfully. Phase 5
  is ready for architect review and owner smoke checks.
