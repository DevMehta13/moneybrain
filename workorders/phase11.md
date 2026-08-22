# Work order: Phase 11 — account balances + envelope buckets

Status: FIX ROUND 1 — see "Fix round 1" section below (review of abaf4d7)
Phase reference: PLAN.md → Phase 11 (added 2026-08-23 by owner decision)

## What changed and why (read first)

The owner redefined how bucket money works (recorded in PLAN.md change log, 2026-08-23):

1. **Buckets are ENVELOPES now.** Money in a bucket carries over until spent or moved.
   Months matter only for reports. The month-scoped allocation model is retired.
2. **Any amount can be split** — salary is just the common case. The plan is now a split
   TEMPLATE that prefills an editable editor; **the owner confirms every split** before
   anything is written. The automatic salary-split path is gone.
3. **Manual control**: add to / take out of / move between buckets anytime.
4. **Account balances**: the owner states each account's real balance once; every captured
   transaction moves it. "Correct balance" writes a newer snapshot and logs an undoable
   BALANCE_CORRECTED action. An account with no snapshot is "not tracked" — never ₹0.
5. **The money map** opens the Buckets tab: total balance, per-account balances, and where
   the money sits (each bucket, of which reserved, and unallocated).

## Architect files already rewritten (do NOT alter their logic)

- `money/BalanceMath.kt` (NEW) — balance = latest snapshot + signed transactions strictly
  after it; totalBalance; correctionDelta. Untracked = null, never guessed.
- `buckets/BucketLedger.kt` (NEW) — `EntryKinds` (SPLIT/MANUAL/MOVE), balance / available /
  unallocated laws, `validateSplit` (the editor and the splitter obey the same validation).
- `buckets/BucketSplitter.kt` (REWRITTEN) — **BucketStore interface changed**: `insertEntry`,
  `insertMovePair`, `entriesExistForSource`, `recordAction`. New API: `applySplit(sourceTransactionId?,
  amountPaise, confirmedLines, now)`, `adjust(bucketId, signedAmount, note, now)`,
  `move(from, to, amount, now)`. `splitSalary` is GONE.
- `buckets/BucketMath.kt` — behaviour unchanged; `split()` now PREFILLS the split editor.
- `capture/Actions.kt` — new kinds `AMOUNT_SPLIT`, `BALANCE_CORRECTED`; new payload keys
  `ENTRY_IDS`, `SNAPSHOT_ID`; UndoStore: `deleteAllocations` RENAMED to `deleteBucketEntries`,
  new `deleteBalanceSnapshot`. UndoEngine handles legacy SALARY_SPLIT rows via ALLOCATION_IDS.
- Tests (all must pass unmodified): `BalanceMathTest` (new), `BucketLedgerTest` (new),
  `BucketsTest` (rewritten), `CapturePipelineTest` (FakeStore updated).

Known call sites you must rework: `data/Entities.kt`, `data/Daos.kt`, `data/CaptureStores.kt`,
`data/MoneyBrainDatabase.kt`, `summary/OverviewMath.kt`, `viewmodel/BucketsViewModel.kt`,
`ui/MoneyBrainScreen.kt`.

## Tasks

### 1. Database v6 (Room migration 5→6, hand-written, NO destructive fallback)

NOTE: this v6 SUPERSEDES the cancelled notification-samples v6 from `workorders/phase7a.md`
(phase 7 declined — build none of it).

```sql
CREATE TABLE bucket_entries (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  bucketId INTEGER NOT NULL REFERENCES buckets(id) ON DELETE RESTRICT,
  amountPaise INTEGER NOT NULL,            -- SIGNED: + in, − out
  kind TEXT NOT NULL,                      -- SPLIT | MANUAL | MOVE (BucketLedger.EntryKinds)
  sourceTransactionId INTEGER,             -- the credit a SPLIT came from (NULL otherwise)
  counterpartEntryId INTEGER,              -- the other MOVE leg (NULL otherwise)
  note TEXT,
  createdAt INTEGER NOT NULL
);
CREATE INDEX index_bucket_entries_bucketId ON bucket_entries(bucketId);
CREATE INDEX index_bucket_entries_sourceTransactionId ON bucket_entries(sourceTransactionId);

INSERT INTO bucket_entries (id, bucketId, amountPaise, kind, sourceTransactionId, createdAt)
  SELECT id, bucketId, amountPaise, 'SPLIT', sourceTransactionId, createdAt FROM bucket_allocations;
DROP TABLE bucket_allocations;

CREATE TABLE balance_snapshots (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  accountId INTEGER NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
  balancePaise INTEGER NOT NULL,
  asOfMillis INTEGER NOT NULL,
  deltaPaise INTEGER,                      -- display only: stated − computed at save time; NULL for the first
  createdAt INTEGER NOT NULL
);
CREATE INDEX index_balance_snapshots_accountId_asOfMillis ON balance_snapshots(accountId, asOfMillis);

CREATE TABLE split_dismissed (
  transactionId INTEGER PRIMARY KEY NOT NULL,
  dismissedAt INTEGER NOT NULL
);
```

**IDs of migrated allocations MUST be preserved** — old SALARY_SPLIT undo actions point at
them (UndoEngine reads both payload keys). Export schema v6.

### 2. Stores

- `RoomBucketStore`: implement the NEW `BucketStore`. `insertMovePair` inserts both legs and
  links them via `counterpartEntryId` both ways, atomically (inside the caller's transaction).
- `RoomUndoStore`: rename `deleteAllocations` → `deleteBucketEntries` (DELETE from
  bucket_entries; when a deleted row has a `counterpartEntryId`, delete that row too — a move
  never survives as one leg). Implement `deleteBalanceSnapshot`.
- Balance snapshots DAO: insert; observe/get all (feed the full list to `BalanceMath` — it
  picks the latest itself; do not duplicate that selection in SQL).
- Bucket delete stays blocked while the bucket has entries (RESTRICT). Message the owner:
  "Move its money out first."

### 3. Shared computation (`summary/OverviewMath.kt` — one law everywhere)

- `BucketStatus` becomes: bucket, `balancePaise` = `BucketLedger.balance(bucketId, entries,
  spentAllTime)` where spentAllTime = ALL-TIME sum of OUT transactions assigned to the bucket
  (bucketId override, else category mapping — same assignment rule as today, but the month
  filter is GONE from the law), `reservedPaise` (unchanged RecurringMath source), and
  `available` = `BucketLedger.available`.
- New money-map function: `BalanceMath.totalBalance` over all accounts + per-account
  balances + `BucketLedger.unallocated`. Overview and the Buckets tab MUST consume these
  same functions — no screen recomputes its own version.
- Cards MAY additionally show "spent this month" as secondary info (the old month-filtered
  spent) — display only; it never feeds balance/available.

### 4. Buckets tab (top to bottom)

1. **Money map header**: total balance (or "Set your balances" CTA when nothing is tracked);
   per-account rows — name + balance, or "Set balance" when untracked; then the distribution:
   every bucket's balance ("of which ₹X reserved" when > 0), then **Unallocated** (em dash +
   a short hint when any account is untracked). Negative numbers show honestly in the error
   colour. All amounts via `Money.formatPaise`.
2. **"Split this?" cards**: IN transactions where `SalaryDetector.looksLikeSalary` is true,
   newer than 14 days, with no bucket_entries for that source, and not in `split_dismissed`.
   Buttons: **Split…** (opens the editor) and **Skip** (insert into `split_dismissed`).
3. **Split editor** (sheet or screen): title "Split ₹X". Lines prefilled from
   `BucketMath.split(amount, plan)` — every line editable (`Money.parseToPaise`). Live footer
   from `BucketLedger.validateSplit`: "₹Y left unallocated" when Ok, the specific error
   otherwise; **Apply is disabled unless validation is Ok**. Apply calls
   `BucketSplitter.applySplit` inside `withTransaction`. The editor is also reachable from:
   - any IN transaction's detail — "Split into buckets…" (replaced by an "Already split"
     label when entries exist for it), and
   - a **"Split an amount"** button on the tab (source = null; the owner types the amount;
     warn when it exceeds unallocated but do not block — unallocated may be unknown).
4. **Bucket cards**: balance / reserved / available, plus actions:
   - **Add** and **Take out** — amount + optional note → `BucketSplitter.adjust` (take-out
     warns when the bucket would go negative but allows it),
   - **Move** — target bucket + amount → `BucketSplitter.move`,
   - **History** — the bucket's entries newest first: signed coloured amount, kind label,
     note, date; each row deletable. Deleting a MOVE leg deletes BOTH legs — the confirm
     dialog must say so.
5. **Remove** the old salary card, "Split now", and the month-scoped `refreshMonth` flow
   (including its `LaunchedEffect`) — the split cards + editor replace them.
6. Plan editor + category→bucket mapping stay; retitle "salary" wording to "Split template".

### 5. Account balances UI

- **Settings → Accounts**: each row shows its balance or "not tracked". Tapping opens
  set/correct: show the computed number (when tracked), input via `Money.parseToPaise`.
  Saving — in ONE `withTransaction`: insert the snapshot (asOfMillis = now; deltaPaise =
  `BalanceMath.correctionDelta(stated, computed)`, NULL when first) and record a
  BALANCE_CORRECTED action (targetType "account", targetId, payload SNAPSHOT_ID; description
  "Set <account> balance: ₹X" for the first, else "Corrected <account> balance by +/−₹D").
- **Overview**: one total-balance line at the top (tap → Buckets tab); hidden while nothing
  is tracked.
- **Activity**: BALANCE_CORRECTED and AMOUNT_SPLIT actions appear in the log with working
  undo (should be automatic if the action list is generic — verify both).

### 6. Quality bar

- `./gradlew test` green — including the four architect suites, unmodified.
- Migration 5→6 on a live phase-6 database: every old allocation visible as a SPLIT entry,
  undo of a pre-migration salary split still works, nothing else lost.
- Kill and reopen the app: balances and bucket numbers identical (everything computed).

## Acceptance

- [ ] Migration preserves all data; allocation ids survive into bucket_entries; schema v6 exported.
- [ ] Undo of a pre-migration salary split deletes exactly its entries.
- [ ] Split editor: template prefill, editable lines, live validation, Apply → entries + one
      AMOUNT_SPLIT action; Skip sticks across restarts.
- [ ] Any IN transaction splits exactly once; second attempt shows "Already split";
      unallocated splits (no source) work repeatedly.
- [ ] Add / take out / move work; move legs sum to zero and delete together.
- [ ] Balance set once tracks every captured transaction; corrections log undoable actions;
      untracked accounts never show ₹0.
- [ ] Money map: total = Σ account balances; unallocated = total − Σ bucket balances
      (verified by hand); "—" when any account is untracked.
- [ ] Overview total balance equals the Buckets tab total (same shared function).
- [ ] All architect-owned tests pass unmodified.

## Fix round 1 (architect review of abaf4d7, 2026-08-23)

Overall: faithful implementation — migration exact, shared law everywhere, writes transactional,
architect tests untouched. Six items, two of them must-fix:

### F1 (MUST) — Flow read inside a Room transaction can hang Save

`AccountsScreen` save flow runs `database.transactionDao().observeAll().first()` INSIDE
`withTransaction`. Flow collection is not guaranteed to join the enclosing Room transaction;
on the single-connection path it can block against the transaction the coroutine itself
holds — Save hangs forever with the database locked. Suspend DAO calls are the guaranteed
mechanism. Fix: add `@Query("SELECT * FROM transactions") suspend fun getAll(): List<TransactionEntity>`
to `TransactionDao` and use it there. Then exercise the flow live once on the device:
set a balance, correct it, undo the correction from Activity.

### F2 (MUST) — Transaction editor split validates against a different amount than it applies

`SplitEditorDialog` in the editor receives `viewModel.validAmount()` — the CURRENT (possibly
edited, unsaved) editor amount — while `applyBucketSplit` validates against the STORED
`existingTransaction.amountPaise`. Edit the amount field before tapping "Split into buckets…"
and the two disagree: `applySplit` returns `Invalid`, and `applyBucketSplit` ignores the
outcome — a silent no-op the owner reads as success. Fix both halves:
- the dialog must use the STORED transaction amount (expose it from the ViewModel), and
- non-`Done` outcomes must surface a message (mirror `BucketsViewModel.applySplit`'s messaging).

### F3 (minor) — Bucket history rows are missing note and date

Spec (task 4.4): signed COLOURED amount, kind label, note, date. Currently only amount + kind.
Add the note (when present) and the createdAt date, and colour the amount (+ normal, − error).

### F4 (minor) — Two specified warnings not implemented

- Take out: warn when the take-out would push the bucket negative (allow it, but say so).
- "Split an amount": warn when the amount exceeds the known unallocated (allow; skip the
  warning when unallocated is unknown).

### F5 (minor) — The money map's "Set your balances" leads nowhere

"Set your balances" / per-account "Set balance" are plain text. Give BucketsScreen a
navigation callback to Settings → Accounts and make those rows tap through to it.

### F6 (polish) — Blank split line reports the wrong error

An unparseable/blank line becomes the `-1` sentinel and reads "Amounts cannot be negative".
Treat unparseable text as its own state with the existing "Enter valid amounts" message.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

- Interim implementation pushed in `18ab3a3` and `b7789ca`.
- Implemented the hand-written 5→6 migration: `bucket_entries` preserves legacy allocation IDs,
  `balance_snapshots` and `split_dismissed` are created, and Room now targets schema v6.
- Replaced the Room allocation store with the envelope-entry store, including atomic linked move
  pairs and inverse deletion of both legs. Legacy SALARY_SPLIT undo and new AMOUNT_SPLIT undo
  both delete `bucket_entries` by their preserved IDs; balance-snapshot undo is wired.
- Reworked shared bucket status calculation to use `BucketLedger` all-time balances and added
  the shared account/bucket money-map calculation. Overview consumes the new law and displays
  total balance when at least one account is tracked.
- Settings → Accounts now displays tracked/not-tracked balances and saves set/correct snapshots
  together with undoable BALANCE_CORRECTED actions.
- Build verification is currently blocked by local tooling: Gradle requires Java 17+ (Java 25 is
  available and was used), but no Android SDK location/platform is installed or configured on
  this machine. Consequently `./gradlew test` cannot run and Room cannot export schema v6 yet.
- Remaining work: complete the split/editor, dismissal, bucket-history/manual-control UI, and
  run the full test + migration verification once the Android SDK is available.
- Completed the envelope-bucket UI: the Buckets tab now opens with the shared money map,
  salary-credit split cards (with durable Skip), an editable confirmed split editor, arbitrary
  unallocated-amount splits, and per-bucket add/take-out/move/history controls. Moves use the
  linked pair store and history deletion uses the paired inverse delete. Credit transaction
  details also expose “Split into buckets…” and change to “Already split” after application.
- The split template is retitled throughout and the old salary-only/month-refresh UI path is
  removed. Account balance set/correct remains undoable through the generic Activity UI.
- Final verification passed: `./gradlew test`, debug assembly, and install-over-existing-data
  on the connected Pixel 9. The installed app was force-stopped and cold-launched successfully
  with no AndroidRuntime/Compose exception; this exercised the live v5→v6 upgrade. Schema v6
  is exported at `app/schemas/.../6.json`.
- Fix round 1 complete. F1 now uses the suspend `TransactionDao.getAll()` query inside the
  balance-save transaction rather than collecting a Flow while holding the Room transaction.
  F2 always presents and applies the stored transaction amount, and reports every split outcome.
  F3 adds coloured signed history amounts plus notes and dates. F4 adds non-blocking warnings
  for negative take-outs and oversize unallocated splits. F5 makes money-map balance CTAs open
  Settings → Accounts. F6 reports blank/unparseable split inputs as “Enter valid amounts.”
- Final verification: full `./gradlew test` passed; debug APK installed on the connected Pixel 9.
  Live F1 exercise completed: set Cash to ₹10, correct it to ₹12, undo the correction in
  Activity (Cash returned to ₹10), then undo the initial test snapshot (Cash is again
  “not tracked”), leaving no test balance behind.
