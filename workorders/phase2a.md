# Work order: Phase 2, Stage A — SMS permission + inbox scanner

Status: OPEN
Phase reference: PLAN.md → Phase 2 (first of two stages; live capture is Stage B)

## Goal

Harvest the phone's EXISTING bank SMS as masked samples so the architect can build the
BoB/HDFC parsing templates. No live capture yet, no database writes, nothing automatic.

## Architect-owned files just added (do NOT alter their logic)

- `app/src/main/java/com/rajnikant/moneybrain/capture/SmsParser.kt` — template model + parser.
  Its template list is EMPTY by design this stage: every bank SMS will show "unrecognised".
- `app/src/main/java/com/rajnikant/moneybrain/capture/SmsMask.kt` — the privacy mask.
- `app/src/main/java/com/rajnikant/moneybrain/capture/Fingerprint.kt` — dedupe key (unused
  until Stage B; ships now with its tests).
- `app/src/test/java/com/rajnikant/moneybrain/capture/CaptureTest.kt` — must pass unmodified.

## Privacy rules for this screen (hard requirements)

- Raw SMS bodies are read into memory only. Everything DISPLAYED and everything COPIED goes
  through `SmsMask.mask` first. There is no code path that puts a raw body on screen,
  on the clipboard, in a log, or in a file.
- No DB writes, no new permissions beyond READ_SMS, no manifest receivers this stage.

## Tasks

1. **Manifest:** add `READ_SMS` permission only (RECEIVE_SMS comes in Stage B).
2. **Settings entry:** a new card "SMS capture (setup)" below Accounts, navigating to the
   Capture screen.
3. **Capture screen:**
   - If permission is not granted: a short plain-language explanation ("Money Brain reads
     bank SMS to record payments automatically. Messages never leave this phone unmasked.")
     and a Grant button using the standard Compose runtime-permission launcher. Denial is
     handled gracefully (explain + allow retry); never crash.
   - When granted: query `content://sms/inbox` (columns: address, body, date), newest first,
     scan at most 500 rows, keep those where `SmsParser.isBankSender(address)`.
   - For each kept message run `SmsParser.parse` and label it `recognised` /
     `unrecognised` (all will be unrecognised now — that is the expected state).
   - Header summary: "Bank messages: N · recognised: R · unrecognised: U".
   - List: sender id, `SmsMask.mask(body)`, date — newest first.
   - Button **"Copy masked samples"**: puts on the clipboard the up-to-30 most recent
     UNRECOGNISED entries as plain text, formatted `[sender]` on one line, masked body on
     the next, blank line between entries. Confirm with a snackbar.
4. **Quality bar:** `./gradlew test` green (including the new CaptureTest, unmodified);
   build + install on the phone; smoke: open screen, grant permission, list shows masked
   bodies only, copy button fills clipboard with masked text.

## Acceptance

- [ ] Capture screen reachable from Settings; graceful without permission; no crashes.
- [ ] With permission: bank-sender messages listed, ALL bodies masked (spot-check: no run
      of 4+ real digits visible outside amounts).
- [ ] Copy button produces masked-only text, capped at 30 entries.
- [ ] `./gradlew test` passes with architect-owned tests unmodified.
- [ ] No DB writes anywhere in this stage.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

- Added the `READ_SMS` permission only, with no receiver, database write, or new dependency.
- Added Settings → SMS capture (setup). The capture screen requests permission with the
  standard runtime launcher, explains a denial and allows retry.
- With permission, it scans at most 500 inbox rows in newest-first order, retains only
  `SmsParser.isBankSender` matches, and shows the parser result and date. Raw bodies exist
  only while each row is processed; screen state and clipboard samples contain only
  `SmsMask.mask` output. Sender labels are masked too.
- Copy masked samples limits itself to the 30 newest unrecognised messages and confirms via
  snackbar. There are no logs or files containing SMS text.
- `./gradlew test` passed, including the unmodified architect-owned capture tests. The debug
  APK built, installed, and launched on the connected phone. SMS permission was granted;
  the capture list showed masked bodies only and Copy masked samples completed successfully.
- Verified the architect-owned `SmsParser.kt`, `SmsMask.kt`, `Fingerprint.kt`, and
  `CaptureTest.kt` have no local changes.
