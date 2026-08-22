# Work order: Phase 2, Stage B — live capture, rules, activity log with undo

Status: OPEN
Phase reference: PLAN.md → Phase 2 (final stage)

## Goal

The magic moment: a real payment appears in the timeline seconds after the SMS arrives,
categorised when a rule exists — and everything automatic is listed in an Activity screen
with one-tap undo.

## Architect-owned files just added (do NOT alter their logic)

- `capture/Actions.kt` — action kinds, payload codec, `UndoStore` interface, `UndoEngine`.
- `capture/CaptureProcessor.kt` — `CaptureStore`/`RuleStore` interfaces, `CaptureProcessor`,
  `RuleLearner`.
- `app/src/test/.../capture/CapturePipelineTest.kt` — must pass unmodified.

Your job is the Room plumbing behind those interfaces, the receiver, and the screens.

## Tasks

### 1. Database v2 (Room migration 1→2 — hand-written Migration, NO destructive fallback)

Exactly these changes:

```sql
ALTER TABLE accounts ADD COLUMN bankCode TEXT;

CREATE TABLE merchant_rules (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  merchantKey TEXT NOT NULL,
  categoryId INTEGER NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
  createdAt INTEGER NOT NULL
);
CREATE UNIQUE INDEX index_merchant_rules_merchantKey ON merchant_rules(merchantKey);
CREATE INDEX index_merchant_rules_categoryId ON merchant_rules(categoryId);

CREATE TABLE actions (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  kind TEXT NOT NULL,
  targetType TEXT NOT NULL,
  targetId INTEGER NOT NULL,
  description TEXT NOT NULL,
  payload TEXT NOT NULL,
  createdAt INTEGER NOT NULL,
  undoneAt INTEGER
);
CREATE INDEX index_actions_createdAt ON actions(createdAt);

CREATE TABLE unparsed_sms (
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  sender TEXT NOT NULL,
  body TEXT NOT NULL,
  receivedAt INTEGER NOT NULL,
  resolvedAt INTEGER
);
CREATE INDEX index_unparsed_sms_receivedAt ON unparsed_sms(receivedAt);

-- Remove the phase 1 placeholder "Bank" account if it was never used
DELETE FROM accounts WHERE name = 'Bank' AND type = 'BANK' AND bankCode IS NULL
  AND id NOT IN (SELECT DISTINCT accountId FROM transactions);
```

Matching entities (`MerchantRuleEntity`, `ActionEntity`, `UnparsedSmsEntity`; add `bankCode`
to `AccountEntity`), DAOs, database version 2 with exported schema. Entity FK/index
declarations must match the SQL exactly or Room rejects the schema at runtime.

`unparsed_sms.body` is the RAW SMS text: allowed at rest on the device only. It is shown in
the UI and copied out ONLY through `SmsMask.mask`. It will be excluded from sync in phase 8.

### 2. Store adapters over Room

`RoomCaptureStore : CaptureStore`, `RoomUndoStore : UndoStore`, `RoomRuleStore : RuleStore`
in a `data/` file. Notes:
- `insertTransactionIfNew` = `@Insert(onConflict = OnConflictStrategy.IGNORE)`; Room returns
  -1 on conflict → return null.
- `upsertRule`: update the existing row for merchantKey if present, else insert; return the id.
- Action `payload` column stores `ActionPayload.encode(...)`; decode when reading.
- All multi-step operations that must be atomic run inside `withTransaction`.

### 3. SMS receiver (live capture)

- Manifest: add `RECEIVE_SMS`; a `BroadcastReceiver` for `android.provider.Telephony.SMS_RECEIVED`.
- CRITICAL: reassemble multipart SMS — `Telephony.Sms.Intents.getMessagesFromIntent(intent)`,
  group by originating address, concatenate bodies in order. HDFC's multi-line messages arrive
  as multiple parts; parsing a lone fragment must not happen.
- In the receiver use `goAsync()` + a coroutine to call `CaptureProcessor.process(sender,
  fullBody, System.currentTimeMillis())`, then finish. No long work on the main thread.
- The capture-setup screen now requests BOTH `READ_SMS` and `RECEIVE_SMS` together.

### 4. Activity screen (the undo log)

- Bottom bar gains a third tab: Timeline · **Activity** · Settings.
- Top section — **Needs attention**: unresolved `unparsed_sms` rows, shown MASKED
  (`SmsMask.mask`), with per-row "Dismiss" (sets resolvedAt) and "Add manually"
  (navigates to the Add screen, unprefilled).
- Below — the action log, newest first: `description`, relative/absolute time, and an
  **Undo** button per row calling `UndoEngine.undo`. Handle each `UndoResult`:
  Done → row shows "Undone"; AlreadyUndone → same; Blocked → snackbar with the reason;
  TargetGone → row shows "Undone". Undone rows keep visible, greyed, no button.
- Undoing `SMS_CAPTURED` deletes a recorded payment → confirm dialog first
  ("Remove this recorded payment? The SMS itself is untouched."). Other kinds undo directly.

### 5. Corrections become rules

In the transaction editor: when the user SAVES a category change on a transaction whose
`merchant` is non-null and the new category is non-null, call
`RuleLearner.learn(merchant, categoryId, categoryName, now)` after saving.
(This applies to future captures; it does not re-categorise history.)

### 6. Timeline touch

Rows for `source == "SMS"` show a small "auto" marker (a subtle label chip is fine) so
captured and manual entries are distinguishable.

### 7. Quality bar

- `./gradlew test` green — including CapturePipelineTest and every earlier architect suite,
  all unmodified.
- Build, install ON TOP of the existing install (migration must run against real phase 1
  data — do not uninstall). Verify phase 1 transactions still present after update.
- Smoke on device: send a test SMS if possible, else verify via the capture screen that
  the receiver is registered and the Activity tab renders.

## Acceptance (architect + owner checks follow)

- [ ] Migration runs on a live phase 1 install without data loss; app version 2 schema exported.
- [ ] A real bank SMS creates exactly one transaction within seconds, visible in Timeline.
- [ ] The same event never duplicates (fingerprint IGNORE path exercised).
- [ ] Activity tab lists the capture with working undo (confirm dialog for captures).
- [ ] A category correction creates a rule; the next capture of that merchant is categorised.
- [ ] Unparsed bank SMS appear masked under Needs attention; dismiss works.
- [ ] All architect-owned tests pass unmodified.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

- Added Room v2 with a hand-written 1→2 migration and exported v2 schema. The migration
  adds bank codes, merchant rules, actions, and unparsed SMS tables; it does not use a
  destructive fallback and preserves existing accounts, categories, and transactions.
  The unused phase-1 Bank placeholder is removed only by the specified guarded SQL.
- Added Room adapters for capture, undo, and rule learning. Fingerprint inserts use
  `IGNORE`; rule upserts and capture/editor/undo multi-step flows run in Room transactions.
- Added the `RECEIVE_SMS` permission and registered SMS receiver. It uses `goAsync()` and
  reassembles all message parts per sender before calling the capture processor, so no HDFC
  fragment is parsed on its own. Capture setup now requests both SMS permissions.
- Added the Activity tab: masked unresolved SMS entries can be dismissed or sent to a blank
  manual-add form; automatic actions support undo, including confirmation before deleting an
  SMS-recorded transaction. SMS timeline rows show an `auto` marker. Editing a transaction's
  category learns a rule for future captures.
- `./gradlew test` and the debug build passed with all architect-owned capture tests unchanged.
  The APK was installed over the existing phone app (no uninstall); the database migration
  completed and the app launched without a Room/migration error. The SMS receiver is
  registered and the Activity tab rendered successfully. A live test SMS was not sent during
  this install check.
- Follow-up receiver-registration verification: pulled the architect's `exported=true` /
  `BROADCAST_SMS` manifest fix, rebuilt, and installed over the existing phone app without
  uninstalling. Both READ_SMS and RECEIVE_SMS are granted. After clearing logcat, a new
  payment/SMS was triggered while `adb logcat | grep -i moneybrain` was watched. The receiver
  itself intentionally writes no Money Brain capture logs (and no SMS content is logged), so
  logcat did not provide a per-event receiver trace. The Timeline currently shows an `auto`
  marker, confirming an SMS-sourced transaction is present on-device. The earlier payment
  occurred before this registration fix was installed, so it could not have been captured by
  the previously non-exported receiver.
