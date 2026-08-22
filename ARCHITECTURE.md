# Money Brain — Architecture & Decisions (v1)

> Companion to [product.md](product.md). That file says *what* we're building; this file says *how*.
> Written for a non-developer owner: jargon is explained the first time it appears.

## Decisions made (2026-08-22)

| Decision | Choice | Why |
|---|---|---|
| Platform | Native Android: **Kotlin + Jetpack Compose** | SMS + notification capture only works on Android, and native code is the most reliable way to do it. No Play Store publishing (personal app, installed directly), so Google's SMS-permission policy doesn't apply. |
| Where data lives | **Phone is the source of truth** (SQLite database on device) | Matches the product principle "data belongs to the user". App works fully offline. |
| ChatGPT connectivity | **Tiny cloud mirror** on Vercel free tier | ChatGPT can only call a public server, and a phone isn't one. The phone syncs an encrypted copy to a small single-user server; ChatGPT talks to that. Zero maintenance, zero cost. |
| ChatGPT protocol | **Custom GPT with Actions** first; MCP connector later | Actions (describing our API to ChatGPT in a standard format) is stable and works in the ChatGPT mobile app. MCP can be added to the same server later and would also let Claude connect. |
| SMS parsing | **Regex templates per bank** (BoB + HDFC first), LLM-assisted fallback for unknown formats | Bank SMS are machine-generated with only a handful of templates. Pattern rules are instant, offline, free. Unknown formats go to "needs attention" and can teach the app a new template. |
| Capture sources | **SMS first, notification listener second**, dedupe between them | SMS is the reliable backbone (banks send one for ~every transaction). Notifications add cleaner merchant names and catch stragglers. |

## Glossary (terms used below)

- **SQLite / Room** — SQLite is a database that lives in a single file on the phone; Room is Android's official, safe way to use it from Kotlin.
- **Regex** — a pattern language for matching text, e.g. "find `Rs.<number> debited from A/c <digits>`".
- **API** — a set of URLs a program exposes so other programs can ask it questions or tell it things.
- **APK** — the installable app file for Android, like an .exe for Windows.
- **Sideloading** — installing an APK directly on your phone instead of via the Play Store.

## System overview

```
┌────────────────── Android phone (source of truth) ──────────────────┐
│                                                                     │
│  SMS receiver ────┐                                                 │
│  Notification     ├─→ Parser (regex templates) ─→ Dedupe ─┐         │
│  listener ────────┘                                       ▼         │
│  Manual entry ─────────────────────────────────→ Rules engine       │
│                                                  (categorise, file  │
│                                                   to trip, drain    │
│                                                   bucket, detect    │
│                                                   recurring)        │
│                                                       │             │
│                          ┌────────────────────────────┤             │
│                          ▼                            ▼             │
│                    Action log (undo)           SQLite DB (Room)     │
│                                                       │             │
│  UI: Overview · Timeline · Buckets · Recurring ·      │             │
│      People · Trips · Activity · Settings  ←──────────┘             │
└───────────────────────────┬─────────────────────────────────────────┘
                            │  sync (both directions, token-protected)
┌───────────────────────────┴─────────────────────────────────────────┐
│  Cloud mirror — Vercel free tier, single user                       │
│  · mirror database (Neon Postgres free tier)                        │
│  · read API: spend queries, bucket status, upcoming bills           │
│  · write API: log transaction, lend, split, start trip …            │
│  · auth: one long secret token only you (and ChatGPT) hold          │
└───────────────────────────▲─────────────────────────────────────────┘
                            │  HTTPS (Actions now, MCP later)
                        ChatGPT
```

## The stack, concretely

### Phone app
- **Language/UI:** Kotlin + Jetpack Compose (Google's modern native stack).
- **Database:** Room over SQLite. One file on device; included in encrypted backups/export.
- **Capture:**
  - `BroadcastReceiver` for incoming SMS (needs `RECEIVE_SMS`/`READ_SMS` permission — fine for a sideloaded personal app).
  - `NotificationListenerService` for app notifications (user grants it once in system settings).
- **Background work:** WorkManager (Android's scheduler) for sync, recurring-payment reminders, and salary-day splitting.
- **Install path:** built as an APK from this machine, installed on the phone via USB (`adb install`) or file transfer.

### Cloud mirror
- **Runtime:** small TypeScript API on Vercel (free tier, no maintenance).
- **Database:** Neon Postgres free tier (Vercel Marketplace) holding the mirror copy.
- **Auth:** a single long random bearer token; no accounts, no login screens. Rotateable from app settings.
- **Role:** serve ChatGPT reads instantly; accept ChatGPT writes into a queue table; hand queued writes to the phone at next sync, where they enter the normal rules engine + activity log with undo.

### ChatGPT
- **v1:** a Custom GPT with Actions pointing at the mirror's API (requires a paid ChatGPT plan to create — flagging now so it's not a surprise).
- **Later:** an MCP endpoint on the same server (also enables Claude as a chat surface).

## Core design rules (non-negotiable from day one)

1. **Money is stored as integer paise** (₹123.45 → `12345`). Decimal floating-point math silently corrupts money values; integers never do.
2. **Bucket "remaining" is always computed** (`allocated − spent − reserved`), never stored as a running total that can drift out of sync.
3. **Every automatic action writes an Action row containing its inverse** — enough information to put things back exactly. Undo = apply the inverse (+ optionally learn a rule). This is what makes "automatic by default, reversible always" real rather than a slogan.
4. **Idempotent capture:** every parsed SMS/notification carries a fingerprint (account + amount + timestamp window + reference no.). Seeing the same event twice (SMS *and* notification, or a re-delivered SMS) never creates two transactions.
5. **Sync is append-mostly, phone wins conflicts.** Single user, so conflicts are rare; when they happen the device copy is authoritative.
6. **The mirror holds only what ChatGPT needs.** Notes can be excluded; raw SMS bodies never leave the phone.

## Data model (first cut)

Tables mirror the vocabulary in product.md:

- `accounts` (id, name, type: bank/card/cash)
- `transactions` (id, amount_paise, direction, account_id, merchant_raw, merchant_clean, category_id, bucket_id, trip_id, occurred_at, source, notes, fingerprint, reference_no)
- `categories` (id, name) and `merchant_rules` (merchant_pattern → category_id, learned from corrections)
- `buckets` (id, name) and `bucket_plans` (bucket_id, month, allocation type + value)
- `recurring` (id, name, merchant_pattern, expected_amount_paise, cadence, next_due, bucket_id, status)
- `people` (id, name) and `splits` (transaction_id, person_id, amount_paise, direction)
- `trips` (id, name, started_at, ended_at nullable = "active")
- `actions` (id, kind, target, description, inverse_payload, created_at, undone_at)
- `sms_templates` (bank, regex, field mapping) — seeded with BoB + HDFC, growable

## SMS parsing plan (BoB + HDFC)

1. Seed templates for Bank of Baroda and HDFC debit/credit/UPI alert formats (built from your real messages — first step of that phase will be looking at a handful of actual SMS from your phone, with account numbers masked).
2. A message matching a template → parsed transaction → rules engine.
3. A bank-looking message matching nothing → "needs attention" card; you can map it manually, and the app derives a new template from your mapping so next time it's automatic.
4. Everything non-financial (OTPs, promos) is ignored by sender-ID allowlist (`BOBSMS`, `HDFCBK`, etc.).

## Build order (phases)

Each phase ends with something you can actually use on your phone. The full execution plan — per-phase exit gates, your manual checks, and progress tracking — lives in [PLAN.md](PLAN.md).

1. **Skeleton + manual entry + timeline** — app installs, you can add cash expenses, see and edit them. Proves the whole toolchain (build → APK → your phone).
2. **SMS capture + categorisation + activity log** — the magic moment: pay someone, watch it appear categorised, undo it. (Action log built here because capture is the first automatic behaviour.)
3. **Buckets + salary split** — plan editor, auto-split on salary credit, honest "remaining".
4. **Recurring** — detection, confirmation, reserved amounts, reminders.
5. **People + trips** — splits, balances, settle up; trip windows and auto-filing.
6. **Overview screen** — assembled from all of the above (it's a composition of parts, so it comes late).
7. **Notification listener** — second capture source + dedupe hardening.
8. **Cloud mirror + sync** — Vercel deploy, token setup, two-way sync.
9. **ChatGPT Actions** — custom GPT wired to the mirror; every write lands in the activity log.
10. **Export** — CSV/Excel with the exact columns in product.md.

## What you'll need along the way (heads-up, nothing needed yet)

- **Android Studio / Android SDK** on this Mac (I can install and drive it) and your phone in developer mode with USB debugging — needed at phase 1.
- **A free Vercel account** — needed at phase 8, one-time login.
- **A paid ChatGPT plan** (to create a Custom GPT) — needed at phase 9. Already covered: Rajnikant has ChatGPT Plus, so the project adds no new recurring cost. The cloud mirror runs on card-free free tiers (Vercel + Neon), so surprise billing is impossible.
- A few **real bank SMS examples** (account digits masked) — needed at phase 2.

## Open items deliberately deferred

- Encrypted off-device backup of the phone DB (e.g. to Google Drive) — design in phase 8 alongside sync.
- Email receipt enrichment (product.md calls it nice-to-have) — after phase 10 if still wanted.
- MCP connector + Claude as a second chat surface — after phase 9.
