# Work order: Phase 3 — buckets & salary split

Status: DONE — gate passed 2026-08-22 (two fix rounds; architect review + owner 22-step walkthrough)
Phase reference: PLAN.md → Phase 3

## Goal

Salary lands, the app offers to split it into envelopes by the saved plan (undoably),
and every spend honestly drains one bucket. remaining = allocated − spent (reserved
arrives in phase 4 and shows ₹0 for now).

## Architect-owned files just added (do NOT alter their logic)

- `buckets/BucketMath.kt` — split math (integer paise, basis points), SalaryDetector.
- `buckets/BucketSplitter.kt` — `BucketStore` interface + the split executor.
- `capture/Actions.kt` — extended: SALARY_SPLIT action kind + undo path.
- Tests: `buckets/BucketsTest.kt` and the updated `capture/CapturePipelineTest.kt` —
  must pass unmodified.

## Tasks

### 1. Database v3 (Room migration 2→3, hand-written, NO destructive fallback)

```sql
CREATE TABLE buckets (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  name TEXT NOT NULL,
  sortOrder INTEGER NOT NULL,
  createdAt INTEGER NOT NULL
);

CREATE TABLE bucket_plan (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  bucketId INTEGER NOT NULL REFERENCES buckets(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,          -- 'FIXED' (paise) | 'PERCENT' (basis points, 10000 = 100%)
  value INTEGER NOT NULL,
  sortOrder INTEGER NOT NULL
);
CREATE INDEX index_bucket_plan_bucketId ON bucket_plan(bucketId);

CREATE TABLE bucket_allocations (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  bucketId INTEGER NOT NULL REFERENCES buckets(id) ON DELETE RESTRICT,
  month TEXT NOT NULL,         -- 'YYYY-MM', device timezone of the salary transaction
  amountPaise INTEGER NOT NULL,
  sourceTransactionId INTEGER, -- null for manual allocations
  createdAt INTEGER NOT NULL
);
CREATE INDEX index_bucket_allocations_month_bucketId ON bucket_allocations(month, bucketId);
CREATE INDEX index_bucket_allocations_sourceTransactionId ON bucket_allocations(sourceTransactionId);

ALTER TABLE categories ADD COLUMN bucketId INTEGER;    -- app-enforced link, NO Room ForeignKey
ALTER TABLE transactions ADD COLUMN bucketId INTEGER;  -- per-transaction override, NO Room ForeignKey
```

IMPORTANT: the two ALTER columns must be plain nullable Longs in the entities with NO
`ForeignKey` declaration — SQLite ALTER cannot retrofit a foreign key, and a declared FK
that the migrated schema lacks makes Room reject the database at open.

### 2. Entities, DAOs, adapters

- `BucketEntity`, `BucketPlanEntity`, `BucketAllocationEntity` (+ the two new columns).
- `RoomBucketStore : BucketStore` — direct mappings; `allocationsExistForSource` is an
  EXISTS query.
- Extend `RoomUndoStore` with the new `deleteAllocations(ids)` member
  (`DELETE FROM bucket_allocations WHERE id IN (:ids)`, returning the row count).
- **Effective bucket rule** (single source of truth for every query and screen):
  a transaction's bucket = `transactions.bucketId` if non-null, else its category's
  `bucketId`, else none.
- Month-status query per bucket for a given month string:
  allocated = SUM(bucket_allocations.amountPaise) for that month/bucket;
  spent = SUM(amountPaise) of OUT transactions in that month whose effective bucket is
  this bucket (month boundaries in the device timezone);
  remaining = `BucketMath.remaining(allocated, spent, 0)` — computed, never stored.

### 3. Buckets tab (4th bottom-bar tab: Timeline · Activity · Buckets · Settings)

- **Month status list** (current month): per bucket — name, allocated, spent, remaining
  via `Money.formatPaise`; remaining < 0 rendered in the Material error color. Plus an
  "Unallocated" row: total IN-month salary allocations… keep simple: sum of the month's
  allocations vs plan is visible per bucket; unallocated shows up in the split dialog only.
- **Salary card**: current-month IN transactions where
  `SalaryDetector.looksLikeSalary(direction, merchant)` and no allocation references them
  (`sourceTransactionId`) → card "Salary detected: ₹X — split into buckets?" with a
  preview of the plan's resulting amounts (call `BucketMath.split` for the preview) and a
  **Split now** button → `database.withTransaction { BucketSplitter.splitSalary(...) }`,
  outcomes: Done → snackbar with allocated/unallocated summary; AlreadySplit/EmptyPlan →
  explanatory snackbar (EmptyPlan points to the plan editor).
- **Plan editor**: list/add/remove buckets (name), per bucket a plan entry: FIXED amount
  (input via `Money.parseToPaise`) or PERCENT (integer percent input × 100 → basis points;
  whole percents are enough for v1). Reorder via up/down is enough. Sanity line using
  `BucketMath.totalPercentBp` / `totalFixedPaise`: warn (don't block) when percents exceed
  100% — the math caps in order by design.
- Deleting a bucket: only offer when it has no allocations (RESTRICT would fail anyway);
  hide/disable otherwise.

### 4. Category → bucket mapping + per-transaction override

- Settings → "Categories & buckets": list categories, each with a bucket picker
  (including "None").
- Transaction editor: a bucket override picker — default "(from category)", options =
  buckets + "None". Saving writes `transactions.bucketId` (null when "(from category)").

### 5. Quality bar

- `./gradlew test` green — ALL architect suites unmodified (CapturePipelineTest gained a
  member; BucketsTest is new).
- Install on top of the live phase 2 install; migration 2→3 runs against real data;
  all transactions/actions/rules intact after update.
- Smoke: create 2–3 buckets + a plan; verify month status renders; salary card appears
  for a matching credit (the July/June NEFT credits are historical — for smoke, temporarily
  check with a manual IN transaction whose merchant contains "salary", then delete it).

## Acceptance

- [x] Migration 2→3 preserves all live data; schema v3 exported.
- [x] Plan editor: buckets + percent/fixed entries; warning over 100%; order respected.
- [x] Salary card previews amounts identical to what Split now then creates; split is
      idempotent per salary transaction; SALARY_SPLIT appears in Activity and undo
      removes exactly its allocations.
- [x] Spending in a mapped category drains the right bucket; per-transaction override wins;
      remaining goes negative honestly (red) when overspent.
- [x] Month boundary correct: a transaction on the 1st counts in the new month.
- [x] All architect-owned tests pass unmodified.

## Review findings — fix round 2 (architect, 2026-08-22) — Status: RESOLVED (verified in re-review)

Category mapping and the transaction override are approved. The following must be fixed
before the gate; items 1–3 are bugs, 4–7 are missing spec requirements.

1. **Spent must be month-filtered.** `BucketsViewModel.status` sums OUT transactions from
   all time against current-month allocations. Fix: include only transactions whose
   `occurredAt`, converted via the device timezone to "YYYY-MM", equals the status month —
   the same month semantics allocations use.
2. **Surface split outcomes.** `splitSalary` must deliver its `SplitOutcome` to the UI
   (Channel → snackbar, same pattern as ActivityViewModel.undoResults):
   Done → "₹X allocated, ₹Y unallocated" from the result; AlreadySplit → explain;
   EmptyPlan → point to the plan editor.
3. **Salary candidates:** restrict to CURRENT-MONTH IN transactions, and check
   already-split globally, not against one month's allocations — query the distinct
   non-null `sourceTransactionId`s from bucket_allocations (all months) and exclude them.
   Also: use `SalaryDetector.looksLikeSalary(...)` — delete the inline reimplementation;
   detection logic lives in exactly one place.
4. **Split preview on the salary card:** before the button, list each bucket's amount from
   `BucketMath.split(salary, plan)` plus an "unallocated" line — the card must show what
   Split now will do, and the numbers must come from the same function that will do it.
5. **Plan editor completion:** per-entry remove (add a deleteById DAO query for
   bucket_plan), up/down reorder (maintain proper sequential `sortOrder` values — replace
   the `currentTimeMillis().toInt()` hack with max+1 on insert and swaps on reorder), and
   the warning line when `BucketMath.totalPercentBp(plan) > 10_000`.
6. **Blocked bucket delete must say why** (snackbar "This bucket has allocations"), not
   silently do nothing.
7. **Confirm negative remaining renders in the Material error color**; implement if missing.

Then: `./gradlew test` green, reinstall on top, append a fix-round note to Result, push.

## Questions

- ~~RESOLVED by architect (2026-08-22): the missing-month call in BucketsTest was the
  architect's own bug — fixed by adding the explicit month. Your handling this round
  (refuse to modify, refuse to work around, report and block) was exactly correct;
  that is the standing rule for any architect-owned file that will not compile.~~
- **Blocked by architect-owned test/API mismatch:** after commit `c4d720e` removed
  `BucketSplitterCompat.kt` (as instructed), the unmodified architect-owned
  `BucketsTest.kt` still calls `BucketSplitter.splitSalary(sourceId, amount, plan, now)` at
  lines 184–188. The architect-owned splitter exposes only the explicit-month overload, so
  `./gradlew test` fails to compile. I did not re-add the prohibited month-defaulting overload
  and did not modify the test. Please update the test to provide an explicit month or provide
  an architect-approved compatibility API that cannot default to today's month.

## Architect note during interim review (2026-08-22)

Foundation approved — migration, entities, DAOs, adapters all correct. One removal:
`BucketSplitterCompat.kt` (a month-defaulting `splitSalary` overload) was deleted. It was
unused — no architect test calls it — and it defaults the month to TODAY, which files a
late-split salary into the wrong month. When building the salary card, derive `month` from
the SALARY TRANSACTION's `occurredAt` (device timezone, "YYYY-MM") and pass it explicitly.
Do not re-add convenience overloads to architect-owned APIs.

## Result

### In progress — foundation pushed for review

- Implemented the hand-written Room 2→3 migration, v3 schema export, bucket entities/DAOs,
  Room bucket adapter, and SALARY_SPLIT allocation deletion support in the undo store.
  `categories.bucketId` and `transactions.bucketId` are nullable plain columns with no Room
  ForeignKey declarations, matching the ALTER TABLE migration exactly.
- Added a basic Buckets tab with current-month computed allocated/spent/remaining values and
  a minimal bucket/percent-plan entry surface. The v3 APK was installed over the existing
  phone app without uninstalling; it launched without a migration/Room validation error.
- `./gradlew test` and the debug build pass with all architect-owned bucket/capture files
  unchanged.
- Remaining before Phase 3 completion: salary preview/split controls, full plan editing,
  Settings category-to-bucket mapping, and transaction-level bucket overrides.
- Continued after interim review: added the salary card's Split now path using the salary
  transaction's `occurredAt` in the device timezone to derive the explicit allocation month;
  it never defaults a late split to today's month. Further validation is blocked by the
  architect-owned test/API mismatch recorded above.
- Continued implementation: Settings now includes Categories & buckets with a persisted
  bucket-or-None picker for every category. The transaction editor now persists a bucket
  override and exposes From category plus each bucket as choices. Tests and debug build pass;
  the APK was installed over the existing phone app without uninstalling.
- Fix round 2 complete: spent is now limited to the status month in the device timezone, and
  salary candidates are current-month only, globally exclude already-split source transactions,
  and use `SalaryDetector`. Split outcomes now reach the Buckets snackbar. The salary card shows
  its `BucketMath.split` preview (including unallocated), plan entries can be removed and moved
  with ordered sort values, over-100% plans warn, blocked bucket deletion explains that
  allocations exist, and negative remaining uses the Material error color. `./gradlew test` and
  the debug build pass. The debug APK was installed over the existing app with `adb install -r`
  successfully; no architect-owned files were changed.
- Plan-order UX fix: removed the misleading per-bucket Up/Down controls and added a global,
  numbered Split order list beneath the bucket cards. Its controls reorder the same global plan
  sequence that salary splitting uses, with an explanation of the priority rule. Per-bucket
  remove and add-entry controls remain unchanged. `./gradlew test` and the debug build pass;
  the APK was installed over the existing app successfully. No schema or architect-owned files
  changed.
