# Work order: Phase 5 — people & trips

Status: OPEN
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

- [ ] Migration 4→5 preserves all live data; schema v5 exported.
- [ ] Equal split of ₹1,000 across 3 shows shares that sum exactly; ledger rows created.
- [ ] Balances render with correct direction both ways; settle up zeroes the balance AND
      creates the settlement transaction in the timeline.
- [ ] "I lent" creates transaction + ledger; "they paid for me" ledger only.
- [ ] With a trip active: captured OUT files to it (Activity shows TRIP_FILED, undo
      unfiles); merchants with ACTIVE recurring items are NOT filed; credits are not filed.
- [ ] Stopping the trip stops filing; trip totals match its transactions.
- [ ] Only one active trip at a time is possible.
- [ ] All architect-owned tests pass unmodified.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

(Fill in after execution.)
