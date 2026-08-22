# Money Brain

I built this to make my own life easy. Keeping track of my money was something I was genuinely
suffering with — payments scattered across bank SMS, no idea what was safe to spend, who owed me
what, or which bill was about to hit. So I made an app that does all of that thinking for me.
Now I can relax and take a chill pill. 💊

A private, offline-first personal finance app for Android. It reads bank SMS (Bank of Baroda + HDFC),
turns them into transactions automatically, and gives every rupee a place to live — with a one-tap
undo for everything it does on its own.

Built for one person, one phone. No cloud, no account, no analytics, no lock-in.

## What it does

- **Automatic capture** — bank SMS become transactions (amount, merchant, in/out) within seconds,
  with fingerprint deduplication so the same payment can never appear twice. OTPs and promos are
  filtered out by a sender allowlist before any text is read.
- **Learning categorisation** — correct a merchant's category once and the app learns the rule;
  every future payment to that merchant is categorised automatically.
- **Undo everything** — every automatic action (capture, categorisation, split, match…) is logged
  with its stored inverse. Undo is exact, not approximate.
- **Envelope buckets** — split any credited amount into buckets using an editable template;
  money carries over until you spend or move it. Add, take out, and move between buckets anytime.
  Nothing is ever split without your confirmation.
- **Account balances** — state each account's real balance once and every captured transaction
  moves it. Drift (cash, missed SMS, bank fees) is fixed with a visible, undoable correction —
  never silently. The **money map** shows the total and exactly where every rupee sits.
- **Recurring bills** — due dates, reminders, reservations that make each bucket's "available"
  number honest, automatic matching when the real payment arrives, and detection of new
  recurring patterns from history.
- **People** — a personal lending ledger: who owes you, whom you owe, equal or custom splits
  down to the exact paisa, settle-up.
- **Trips** — group spending in a date window; payments during an active trip file themselves
  (bills excluded), each filing undoable.

## The rules that never bend

1. **Money is integer paise.** Floating point never touches an amount.
2. **Every derived number is computed, never stored.** A bucket's balance is the sum of its
   ledger; an account's balance is the last snapshot plus what happened after it. Nothing can drift.
3. **Every automatic action stores its inverse.** The activity log can put anything back.
4. **One law per number.** The Overview and every detail screen call the same functions —
   the same figure can never disagree with itself in two places.
5. **Raw SMS text never leaves the device**, and anything displayed or copied is masked first.

## Stack

Kotlin · Jetpack Compose (Material 3, custom "Modernist" design system with the Archivo typeface) ·
Room/SQLite with hand-written migrations · WorkManager. No network permission is used for data —
everything lives in one SQLite file on the phone.

## How it was built

This repo is a two-AI-agent collaboration, run by one human owner:

- **Claude (architect)** designed everything, wrote the correctness-critical code
  (money math, parsing, splitting, undo, balances — all JVM-unit-tested against store
  interfaces) and reviewed every pushed line.
- **Codex (implementer)** built the app around it: Room adapters, ViewModels, screens,
  builds, and on-device verification.
- They never spoke directly. All coordination happened through this repository:
  [`AGENTS.md`](AGENTS.md) holds the implementer's standing orders, and every task travelled
  as a work order in [`workorders/`](workorders/), with results and review findings written back
  into the same file.

The full paper trail is in the repo: [`product.md`](product.md) (what to build),
[`ARCHITECTURE.md`](ARCHITECTURE.md) (the decisions), [`PLAN.md`](PLAN.md) (phases, exit gates,
and a change log where every scope change is recorded, never made silently).

## Building it

```
./gradlew test            # JVM unit tests (money math, parsing, splitting, undo, balances)
./gradlew assembleDebug   # debug APK, sideloaded — this app is not on any store
```

Requires JDK 17+ and the Android SDK. On first launch grant the SMS permission from
Settings → SMS capture; capture templates cover Bank of Baroda and HDFC formats.

## A note on privacy

This is a personal-finance app that deliberately knows nothing about you unless you put it there,
and shares it with no one. All test data in this repository is fictional; masked SMS samples use
X-ed digits. There is no backend to trust because there is no backend.
