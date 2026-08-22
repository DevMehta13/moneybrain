# Money Brain — Product Brief (v1)

> Personal finance app for Rajnikant. Single user, mobile-first (phone). This document is the source of truth for *what* we are building. The *how* (stack, architecture) is decided separately with Claude Code.

## One-line

A finance app you barely open: it captures payments automatically, files them into buckets, trips and people, lets you ask anything through ChatGPT, and lets you undo anything it did on its own.

## Goals

- Know exactly where money goes without manual effort.
- Never be surprised by a bill, subscription or "who owes whom".
- Feel in control: every automatic action is visible and reversible.
- Usable from one overview screen + a chat (ChatGPT) — no spreadsheet juggling.

## Non-goals (v1)

- Tax helper (80C/80D/HRA), documents/warranty vault, year-in-review, reflective/regret tagging, round-up savings, investment tracking, anomaly alerts. See "Later" section.
- Multi-user / shared household accounts.
- Bank API integrations (account aggregators) — v1 relies on SMS/notifications + manual entry.

## Core concepts (data model vocabulary)

- **Account** — a source of money: salary bank account, future trading account, credit card, cash. v1 has one bank account + cash, but the model supports many from day one (adding one is a setting, not a rebuild).
- **Transaction** — amount, direction (in/out), account, merchant, category, date/time, notes, source (sms / notification / manual / chatgpt), optional links to a Trip, a Person split, a Bucket.
- **Category** — groceries, food delivery, transport, rent, etc. Auto-assigned; user corrections create merchant→category rules.
- **Bucket** — salary allocation envelope (Savings, Essentials, Fun, custom e.g. "Bike", "Gifts"). Each has a monthly allocation and a live "remaining" figure.
- **Recurring** — a detected or user-declared repeating payment (rent, EMI, SIP, Netflix, mobile). Has expected amount, cadence, next due date, linked bucket.
- **Person** — a friend/contact with a running balance (they owe me / I owe them).
- **Trip/Event** — a container with start/end (or "active until I stop it"); transactions in that window are auto-filed into it.
- **Action** — an entry in the activity log: what the app did automatically, with an undo.

## v1 Features

### 1. Automatic capture + categorisation
- Capture payments made via GPay/UPI/cards from bank & UPI **SMS and app notifications** (GPay itself exposes nothing; the bank SMS that arrives with every payment is the signal).
- Parse merchant, amount, account, reference. Dedupe when both SMS and notification arrive.
- Auto-categorise. First time an unknown merchant appears, best-guess; when the user corrects it, remember the rule.
- Email receipts (Amazon, Swiggy, Zomato, Uber etc.) — nice-to-have in v1, can enrich a transaction with line items.

### 2. Manual entry + ChatGPT entry
- Two-tap manual add for cash or anything missed (amount → category, defaults to cash account).
- Same thing via ChatGPT: "spent 300 cash on auto", "lent Rahul 2000", "start Goa trip".

### 3. Salary buckets
- On salary credit, split into buckets by a saved plan (percent or fixed amounts). User can adjust the plan anytime.
- Every transaction drains one bucket (category → bucket mapping, overridable per transaction).
- Recurring payments are **pre-reserved** from their bucket so "remaining" is honest.
- Overview shows per-bucket: allocated, spent, reserved, remaining.

### 4. Subscriptions & recurring management
- Auto-detect repeating payments by merchant + cadence; user can confirm, edit or add manually.
- Show: what's due in the next 30 days, next due date per item, monthly total of all recurring.
- Reminder a few days before each due date.
- Flag subscriptions unchanged for long periods as "review this?" (no usage tracking in v1, just age).
- Mark as cancelled / paused.

### 5. People: splits, lending, owing
- Split any transaction with one or more people (equal or custom amounts).
- Record "I lent X" / "I owe X" directly.
- Per-person balance + history. "Settle up" clears the balance and records the settlement transaction.
- Optional reminder for money owed to me.

### 6. Trips & events
- Create a trip/event with name and optional dates, or "start now / stop later".
- All transactions in the window are auto-filed (undoable per transaction). Can add/remove manually.
- Trip view: total, per-category breakdown, per-day spend, shared splits within the trip.

### 7. Overview page (home)
One glanceable screen:
- Buckets with remaining amounts (the headline numbers).
- Upcoming recurring payments (next 7–30 days).
- Active trip summary, if any.
- People balances summary (net owed to me / by me).
- Recent transactions.
- "Needs attention" strip: uncategorised items, new recurring detected, reminders.
- Entry point to ChatGPT (see §8).

### 8. ChatGPT integration (primary chat surface)
- The app is the backend; ChatGPT is the conversational front-end.
- Capabilities exposed: query (spend by period/category/trip/person, bucket remaining, upcoming bills), log (transaction, lend/borrow, split, start/stop trip), manage (recategorise, confirm recurring, settle up).
- Every write made from ChatGPT appears in the activity log with undo.
- Only the data needed to answer a question leaves the device.

### 9. Activity log + undo (manual control)
- Every automatic action (categorised, filed to trip, detected recurring, allocated salary, reserved amount, ChatGPT write) is listed with timestamp and a one-tap undo.
- Undoing a categorisation or filing teaches a rule ("don't file X to trips", "Ratnadeep = groceries").
- Everything is also editable directly from the timeline.

### 10. Accounts (multi-account ready)
- Settings → Accounts: add bank account, credit card, cash. Each transaction belongs to one.
- v1 ships with one bank + cash; adding a second bank/trading account later is just configuration.

### 11. Export (CSV / Excel)
Columns, one row per transaction:
`date, time, amount, direction (in/out), account, merchant, category, bucket, trip, split_with (names+amounts), person_balance_effect, recurring (yes/no + name), source (sms/notification/manual/chatgpt), notes, transaction_id, reference_no`

Additional sheets in the Excel export: `buckets` (month, bucket, allocated, spent, reserved, remaining), `recurring` (name, amount, cadence, next_due, status), `people` (name, balance, last_activity), `trips` (name, dates, total).

Filters before export: date range, account, trip. Export includes all data the user owns — no lock-in.

## Screens (v1)

1. **Overview** — as in §7.
2. **Timeline** — all transactions newest first; search, filter by account/category/bucket/trip/person; tap to edit, split, file to trip, attach note.
3. **Buckets** — plan editor + current month status.
4. **Recurring** — list, upcoming, confirm/dismiss detected.
5. **People** — balances, history, settle up.
6. **Trips** — list, active trip, trip detail.
7. **Activity** — the undo log.
8. **Settings** — accounts, salary plan, categories & rules, ChatGPT connection, export, privacy (what is stored, what leaves the phone).

## Principles

- Automatic by default, reversible always.
- Corrections become rules; the app should get quieter over time.
- One number you can trust: bucket "remaining" must account for reserved recurring payments.
- Data belongs to the user: stored on device, exportable in full.

## Later (parked ideas)

Catch-it-early: price drift alerts, missing refunds, duplicate charges, card statement reconciliation, 30–60 day cash-flow forecast. Tax tagging (80C/80D/HRA). Receipt & warranty vault, documents vault with renewal dates. Round-up savings, goal envelopes, idle-money nudge, salary-day ritual. Investment/net-worth tracking. Money weather, daily one-liner, what-if simulator. Weekly/monthly notes, year-in-review, happy/regret tagging. Location-aware merchant suggestions. Calendar awareness for festivals/weddings.

## Open questions for the "how" phase

- Platform: Android-only native (needed for SMS/notification reading) vs. cross-platform with an Android capture layer.
- Where data lives: on-device DB + encrypted backup; what minimal server is needed for the ChatGPT integration.
- How ChatGPT connects: custom GPT with Actions vs. MCP-style connector; auth; what is exposed.
- SMS parsing approach: regex templates per bank vs. small on-device model vs. LLM-assisted parsing with caching.
- Notification listener vs. SMS permission vs. both.
