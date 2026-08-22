# Work order: Phase 3 — buckets & salary split

Status: OPEN
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

- [ ] Migration 2→3 preserves all live data; schema v3 exported.
- [ ] Plan editor: buckets + percent/fixed entries; warning over 100%; order respected.
- [ ] Salary card previews amounts identical to what Split now then creates; split is
      idempotent per salary transaction; SALARY_SPLIT appears in Activity and undo
      removes exactly its allocations.
- [ ] Spending in a mapped category drains the right bucket; per-transaction override wins;
      remaining goes negative honestly (red) when overspent.
- [ ] Month boundary correct: a transaction on the 1st counts in the new month.
- [ ] All architect-owned tests pass unmodified.

## Questions

(Write questions here and push if blocked. Do not guess.)

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
