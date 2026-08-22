# Money Brain — Build Plan (v1)

> The third document in the trio:
> - [product.md](product.md) — **what** we're building (features, principles)
> - [ARCHITECTURE.md](ARCHITECTURE.md) — **how** it's built (stack, decisions)
> - **PLAN.md (this file)** — **in what order**, with a clear "done" test for every step

## How to read this document

The build is split into **phases 0–10**. Every phase ends with something you can actually use on your phone — no phase is "invisible plumbing only".

Each phase has the same seven sections, so you'll learn the rhythm of how software gets built:

| Section | What it means |
|---|---|
| **Goal** | One sentence: why this phase exists. |
| **What we build** | The concrete things that get made. |
| **What we do NOT build yet** | Things that *sound* like they belong here but are deliberately postponed — scope control is half of software engineering. |
| **Exit gate** | An objective checklist. The phase is not done until every item passes. No vague "works well" — each item is a yes/no test. |
| **Your manual checks** | Things *you* do on your own phone to verify. You are the QA (quality assurance) tester of this project. |
| **What you'll learn** | The software concept this phase demonstrates. |
| **Likely to change** | Where the fine details will probably shift once we're hands-on. |

**The rules of the plan:**

1. A phase is done only when its **exit gate passes AND your manual checks pass**. Both. If your check fails, the phase stays open even if the gate technically passed.
2. **Small details inside a phase will change** during implementation — that's normal and expected. But the **phase order and the exit gates only change with your agreement**, recorded in the change log at the bottom.
3. We finish one phase before starting the next. No half-open phases piling up.

**The two-machine workflow (added 2026-08-22):** development runs across two machines.
The borrowed Mac runs **Claude (architect)**: all decisions, specs, correctness-critical code, and review of every change — with a tiny footprint (no Android tools installed there). Rajnikant's own laptop runs **Codex (implementer)**: Android Studio, builds, emulator, phone installs, and routine implementation from written specs. They communicate only through the private GitHub repository — instructions travel as files in `workorders/`, standing rules for Codex live in `AGENTS.md`, and Claude reviews the actual pushed code, never a verbal summary. Rajnikant's role: paste one kickoff line into Codex per round, relay "Codex is done / has questions", and perform the manual checks below. Exit gates are unchanged; each phase now additionally ends with Claude's review of the pushed code before the gate is declared passed.

**About timing:** no date estimates — they'd be fiction. Instead each phase has a size: **S** (a sitting or two), **M** (a few sessions), **L** (many sessions, the hard ones). Phases 2 and 8 are the L's — automatic capture and sync are where real-world messiness lives.

---

## Phase 0 — Dev environment & project skeleton (S)

> Amended 2026-08-22: executes on **Rajnikant's laptop via Codex** (see "The two-machine workflow" above), not on the borrowed Mac. Work order: `workorders/phase0.md`. Gate unchanged, plus: the repo builds from a clean clone.

**Goal:** Everything installed and proven: code on this Mac becomes an app icon on your phone.

**What we build**
- Install Android Studio + Android SDK on this Mac (the toolchain that turns Kotlin code into an APK).
- Put the project under **git** (version control: a save-history for code; every change is recorded and reversible — the developer's equivalent of your app's activity log).
- Create the empty Kotlin + Jetpack Compose project, app name "Money Brain", an icon.
- Build a debug APK and install it on your phone over USB.
- Do the build→install cycle twice, so we know repeat updates work.

**What we do NOT build yet**
- No screens, no database, no features. A placeholder "Hello" screen only. Phase 0 is entirely about the pipeline, not the product.

**Exit gate**
- [ ] Money Brain icon appears on your phone and opens.
- [ ] A code change (e.g. new text on screen) reaches the phone in a rebuild+reinstall.
- [ ] Project committed to git with a first commit.
- [ ] App survives a phone restart (still installed, still opens).

**Your manual checks**
- Enable Developer Mode + USB debugging on your phone (I'll give exact steps for your phone model).
- Open the app yourself; restart your phone; open it again.

**What you'll learn:** what a toolchain is, what version control is, how code becomes an installed app.

**Likely to change:** USB/driver quirks vary by phone brand; we may switch to Wi-Fi installation if USB misbehaves.

---

## Phase 1 — Manual entry + timeline (M)

**Goal:** A genuinely usable cash-expense tracker — the simplest version of the product that's real.

**What we build**
- The on-device database (Room/SQLite) with the first tables: `accounts` (seeded: your bank + cash), `transactions`, `categories` (seeded with a sensible starter list).
- Money stored as **integer paise** from the very first line (see ARCHITECTURE.md core rules), displayed as ₹ properly.
- **Two-tap manual add:** amount → category, defaulting to the cash account (product.md §2).
- **Timeline screen:** all transactions newest first; tap to edit; delete.
- A minimal Settings screen (it will grow every phase), including **Accounts management**: view/add bank, card, cash accounts (product.md §10 — multi-account ready from day one, even though v1 ships with two).

**What we do NOT build yet**
- No SMS reading, no buckets, no undo log (edits are direct for now — undo arrives with the first *automatic* actions in phase 2).
- No timeline search/filters yet — filters need categories/buckets/trips/people to exist first; they arrive progressively, finishing in phase 6.

**Exit gate**
- [ ] Adding a cash expense takes two taps plus typing the amount.
- [ ] It appears at the top of the timeline instantly.
- [ ] Data survives force-closing the app AND restarting the phone (proves the database, not just the screen).
- [ ] Edit and delete both work and stick.
- [ ] ₹1,234.50 style amounts display correctly everywhere (paise math is right).

**Your manual checks**
- Use it for real for a day or two: log chai, auto, whatever you pay cash for.
- Restart your phone, confirm nothing vanished.
- Try to confuse it: ₹0, huge amounts, decimals like ₹99.99.

**What you'll learn:** what a database is, how a screen reads/writes data, why "survives restart" is the real test of saving.

**Likely to change:** the starter category list (we'll tune it to your life), and the add-screen layout.

---

## Phase 2 — SMS capture, auto-categorisation & the activity log (L)

**Goal:** The magic moment — pay someone with UPI and watch it appear in the app, categorised, with an undo.

**What we build**
- SMS permission flow + the background receiver that sees incoming SMS.
- **Sender allowlist** (BOBSMS, HDFCBK, etc.) so OTPs and promos never enter the pipeline.
- **Regex templates** for Bank of Baroda + HDFC, built from ~10–20 of your real messages (account digits masked). This is the first thing we do in the phase.
- Parser → transaction, with a **fingerprint** (account + amount + time window + reference no.) so the same payment can never appear twice.
- **Auto-categorisation:** best-guess for new merchants; your correction creates a merchant→category rule that applies forever after.
- **"Needs attention"** list: bank-looking SMS that matched no template land here; mapping one manually teaches the app a new template.
- **The activity log with undo** — born in this phase because this is the first time the app acts *on its own*. Every automatic action stores its inverse (ARCHITECTURE.md rule 3).

**What we do NOT build yet**
- Notification listener (phase 7). Email receipts (not scheduled in v1 phases — product.md calls them nice-to-have; we'll revisit after phase 10 if you still want them).
- LLM-assisted parsing of unknown formats — v1 of "unknown" is the manual needs-attention flow; smart assist can come later if unknowns are frequent.

**Exit gate**
- [ ] A real UPI payment appears in the timeline, categorised, within seconds of the SMS arriving.
- [ ] The same event arriving twice produces exactly one transaction.
- [ ] OTPs and promotional SMS never appear anywhere in the app.
- [ ] Correcting a wrong category creates a rule; the next payment to that merchant is categorised right automatically.
- [ ] Undo on an auto-categorisation reverses it exactly, and the undo itself is visible in the activity log.
- [ ] An unrecognised bank SMS lands in "needs attention" instead of being silently dropped.

**Your manual checks**
- Make a small real payment (₹10 UPI to a friend works) and watch the app.
- Pay the same merchant twice across two days; confirm the second one is auto-categorised after you corrected the first.
- Check after receiving an OTP that nothing appeared.
- Undo something and confirm the transaction really reverted.

**What you'll learn:** background services, app permissions, pattern matching (regex), why "exactly once" is one of the hard problems.

**Likely to change:** the regex templates themselves — banks have more message variants than anyone expects; the first two weeks will surface stragglers, and that's what "needs attention" is for. Some phone brands (Xiaomi, Samsung…) aggressively kill background apps; we may need to whitelist Money Brain from battery optimisation.

---

## Phase 3 — Buckets + salary split (M)

**Goal:** Salary lands, splits itself into envelopes, and every spend honestly drains one.

**What we build**
- Buckets (create/rename/delete) + the **plan editor**: percent or fixed amount per bucket (product.md §3).
- **Salary-credit detection** (a credit matching your salary pattern) triggering the auto-split — logged in the activity log, undoable. First months: the app asks "this looks like salary — split it?" before acting.
- Category→bucket mapping, overridable per transaction.
- **Buckets screen:** allocated / spent / reserved / remaining per bucket. (Reserved shows ₹0 until phase 4 makes it real.)
- Month rollover: a new month starts fresh allocations.

**What we do NOT build yet**
- Reserved amounts (they need recurring payments — phase 4).
- Rollover of unspent money between months (keep or reset?) — deliberately undecided until you've lived with buckets for a month.

**Exit gate**
- [ ] A salary credit triggers the split per your plan; the split is in the activity log and undo restores the pre-split state exactly.
- [ ] Every categorised spend drains the mapped bucket; the override works per transaction.
- [ ] remaining = allocated − spent for every bucket, verified against hand-calculation.
- [ ] Month boundary: on the 1st, new allocations appear and last month's figures are preserved, not overwritten.

**Your manual checks**
- Set up your *real* salary plan.
- On salary day (or via a simulated credit), check the split against your own mental math.
- Deliberately mis-categorise something, fix it, confirm the right bucket got drained after the fix.

**What you'll learn:** business logic — the app now makes decisions, not just records; why computed values beat stored totals.

**Likely to change:** exactly how salary is recognised (amount pattern vs sender vs manual confirm), and the rollover question above.

---

## Phase 4 — Recurring payments (M)

**Goal:** Never surprised by a bill: rent, EMI, SIP, subscriptions — detected, reserved, reminded.

**What we build**
- **Detection:** same merchant + similar amount + regular gap → "looks recurring, confirm?" card. Manual add for the ones you already know (rent, EMI, SIP…).
- Expected amount, cadence, **next due date**, linked bucket, paused/cancelled states (product.md §4).
- **Reserved amounts:** each confirmed recurring reserves its expected amount from its bucket — "remaining" becomes honest (the product's One Number You Can Trust).
- When the actual payment arrives and matches, reserved converts to spent — never double-counted.
- **Reminders** (phone notifications) a few days before each due date.
- Upcoming-30-days list + monthly recurring total + the "unchanged for long — review this?" age flag.

**What we do NOT build yet**
- Usage tracking, price-drift alerts, duplicate-charge detection (all in product.md's "Later" list).

**Exit gate**
- [ ] A manually added recurring immediately reduces its bucket's remaining by the reserved amount.
- [ ] When the real payment arrives, reserved → spent with no double-count (remaining moves by ₹0 at that moment — this is THE subtle test).
- [ ] A repeating payment in your history gets detected and offered within 2–3 cycles.
- [ ] A reminder notification fires at the configured lead time.
- [ ] Pause/cancel stops both the reservation and the reminders.

**Your manual checks**
- Enter your full real list: rent, EMIs, SIPs, Netflix, mobile.
- On a due date, watch the reserved→spent conversion and confirm remaining didn't jump.
- Confirm a reminder actually reached you before a real bill.

**What you'll learn:** scheduled background jobs, detection heuristics (rules of thumb that guess, then ask).

**Likely to change:** matching a real payment to its recurring entry (amounts drift — Netflix raises prices); the tolerance windows will need tuning.

---

## Phase 5 — People & trips (M)

**Goal:** "Who owes whom" and "what did Goa cost" answered without a notebook.

**What we build**
- **People:** add a person, split any transaction (equal/custom), direct "lent X" / "owe X" entries, per-person balance + history, **settle up** (clears balance, records a settlement transaction), optional owed-to-me reminders (product.md §5).
- **Trips:** create with dates or "start now / stop later"; transactions in the window auto-file to the trip (each filing is in the activity log, undoable); add/remove manually; trip view with total, per-category, per-day, and splits within the trip (product.md §6).
- Timeline filters for person and trip (the filter set keeps growing).

**What we do NOT build yet**
- Multi-user / shared accounts (explicit non-goal). Contact-book sync — names are typed manually for now; we'll decide during the phase if contact picking is worth it.

**Exit gate**
- [ ] Splitting a real dinner between two people produces the correct balances on both.
- [ ] Settle up zeroes the balance AND the settlement appears in the timeline as a transaction.
- [ ] With a trip active, a new payment auto-files to it; undo unfiles it; recurring payments (rent/EMI) do NOT auto-file to trips.
- [ ] Trip total equals the sum of its transactions, split amounts included correctly.

**Your manual checks**
- Recreate a real recent lend/owe with a friend and check it against your memory/WhatsApp.
- Start a trip on a weekend outing, stop it after, read the trip view: does it match what the weekend actually cost?

**What you'll learn:** relational data — records that point at other records, and what that makes possible.

**Likely to change:** which transactions are excluded from trip auto-filing (recurring is excluded from day one; we'll discover other exclusions by living with it), and the split-entry UX.

---

## Phase 6 — The Overview screen (S)

**Goal:** The one glanceable screen the whole product was designed around.

**What we build**
- Home screen assembling: bucket remainings (headline), upcoming recurring (7–30 days), active trip summary, people net balance, recent transactions, and the **"needs attention" strip** (uncategorised, new recurring detected, reminders) — product.md §7.
- Final navigation structure between all screens.
- Timeline **search** + the completed filter set (account/category/bucket/trip/person).

**What we do NOT build yet**
- No new data features at all. This phase is pure composition — a test of everything built so far.

**Exit gate**
- [ ] Every number on Overview exactly matches its detail screen (bucket remaining, people net, trip total, upcoming count). Any mismatch is a bug in the source of that number.
- [ ] The needs-attention strip count is correct and each item taps through to its fix.
- [ ] Cold app open lands on Overview in under ~2 seconds.
- [ ] Timeline search finds a transaction by merchant name; every filter narrows correctly.

**Your manual checks**
- The real test is a week of daily use: do you open the app, glance once, and *trust it*? If you catch yourself tapping into detail screens to double-check, tell me what you were checking — that belongs on Overview.

**What you'll learn:** why consistency (every number agreeing everywhere) is what makes software feel trustworthy.

**Likely to change:** layout and ordering of sections — this screen gets tuned to your taste over time.

---

## Phase 7 — Notification listener + dedupe hardening (M)

**Goal:** Second capture source: cleaner merchant names, and catching whatever SMS misses.

**What we build**
- The notification listener (you grant it once in system settings — I'll walk you through it).
- Parsers for GPay/bank-app notifications.
- **Merge logic:** when SMS and notification describe the same payment, one transaction results — keeping the better merchant name (notifications usually say "Ratnadeep Super Market", SMS says "UPI/318…").
- Battery-optimisation guidance for your phone brand so Android doesn't silently kill the listener.

**What we do NOT build yet**
- Nothing else — this phase deliberately adds no features, only a second pipe into the same pipeline.

**Exit gate**
- [ ] A payment producing both an SMS and a notification yields exactly ONE transaction.
- [ ] That transaction carries the cleaner merchant name of the two.
- [ ] A notification-only event (no SMS) is still captured.
- [ ] Turning the listener off degrades gracefully to SMS-only — nothing breaks, nothing doubles.

**Your manual checks**
- A normal day of GPay payments: count transactions in the app vs payments you actually made. Exactly equal, no misses, no doubles.
- Check whether merchant names got noticeably nicer.

**What you'll learn:** how Android notification access works, and why capturing an event "exactly once" from two unreliable sources is a classic hard problem.

**Likely to change:** notification formats are app-version-dependent and phone-brand-dependent; expect parser tweaks for weeks. This is also where your specific phone's battery-killer behaviour gets handled.

---

## Phase 8 — Cloud mirror + sync (L)

**Goal:** An encrypted copy of your data reaches a tiny free server, so something exists for ChatGPT to talk to.

**What we build**
- The mirror server (TypeScript on Vercel free tier) + mirror database (Neon Postgres free tier) — see ARCHITECTURE.md for why these.
- **Auth:** one long secret token; rotation from app Settings.
- **Sync engine on the phone:** pushes changes up; pulls down writes queued on the server (this queue is how ChatGPT entries will reach the phone in phase 9). Phone wins all conflicts.
- Sync status in Settings + the **privacy screen** (exactly what is stored where, what leaves the phone — product.md §Settings). Raw SMS bodies never sync; notes excludable.
- Encrypted backup decision (the deferred item from ARCHITECTURE.md) gets made and built here alongside sync.

**What we do NOT build yet**
- No ChatGPT connection yet. We test the mirror by calling its API directly, pretending to be ChatGPT. Cheaper to debug one new thing at a time.

**Exit gate**
- [ ] A transaction created on the phone is visible in the mirror within one sync cycle.
- [ ] A write injected via the server API (simulated ChatGPT) lands on the phone, in the activity log, with working undo.
- [ ] Phone in airplane mode for a day → reconnects → catches up completely, no losses, no duplicates.
- [ ] Requests without the token (or with a rotated-out old token) are rejected.
- [ ] The privacy screen's claims are verified true against what's actually in the mirror database.

**Your manual checks**
- One-time: create the free Vercel account (I drive everything after the login).
- The airplane-mode day test.
- Read the privacy screen and ask me to prove any line of it — good habit, and it keeps the screen honest.

**What you'll learn:** what a server is, what "deploying" means, what an API is, and why sync is famous among developers for hiding bugs.

**Likely to change:** the sync schedule (instant vs every-few-minutes vs on-app-open — we'll tune for battery), and the shape of the write queue. Sync edge cases WILL surface after "done" — the phone-wins rule plus the activity log is our safety net, but expect a return visit to this phase.

---

## Phase 9 — ChatGPT integration (M)

**Goal:** "Spent 300 cash on auto" typed into ChatGPT shows up on your phone, undoable.

**What we build**
- The API description (OpenAPI format) that teaches ChatGPT what our server can do.
- A **Custom GPT** in your ChatGPT Plus account (I walk you through the few clicks; it's yours, private to your account), connected via Actions with your secret token.
- **Query** endpoints: spend by period/category/trip/person, bucket remaining, upcoming bills. **Write** endpoints: log transaction, lend/borrow, split, start/stop trip, recategorise, confirm recurring, settle up (product.md §8).
- Every write arrives on the phone tagged `source: chatgpt`, in the activity log, with undo.
- Answers contain only the data needed for the question — nothing extra leaves the mirror.

**What we do NOT build yet**
- MCP connector / Claude as a second chat surface (parked in ARCHITECTURE.md — same server can grow it later). Voice: nothing to build; the ChatGPT app's voice mode gets it free.

**Exit gate**
- [ ] In the ChatGPT mobile app: "how much did I spend on food this month?" returns the same number as the app's own screens.
- [ ] "spent 300 cash on auto" → appears on the phone, correct category/account, `source: chatgpt`, undo works.
- [ ] "lent Rahul 2000" and "start Goa trip" both work end-to-end.
- [ ] A request with a wrong/missing token is refused; nothing about your data leaks in error messages.

**Your manual checks**
- A few days of asking your real questions in your own words — the phrasings that feel natural to you are the test set that matters.
- Try to break it: ambiguous phrasing, weird amounts, questions it shouldn't answer.
- Verify a couple of its numeric answers against Overview.

**What you'll learn:** how AI assistants call tools/APIs, and the basics of API security.

**Likely to change:** the wording of the API descriptions — how well ChatGPT picks the right endpoint depends heavily on them, and they always need a few rounds of tuning against real usage.

---

## Phase 10 — Export (S)

**Goal:** All your data, out, in one tap — the no-lock-in promise made real.

**What we build**
- CSV and Excel export with the exact columns from product.md §11, one row per transaction.
- Extra Excel sheets: `buckets`, `recurring`, `people`, `trips`.
- Pre-export filters: date range, account, trip.
- Android share sheet: save to Drive, send to yourself, open in Sheets.

**What we do NOT build yet**
- Nothing follows — this is the last v1 phase. (Everything in product.md's "Later" section remains parked until you ask.)

**Exit gate**
- [ ] The exported file opens cleanly in Excel/Google Sheets with all columns present.
- [ ] Row count matches the filtered timeline for the same filters.
- [ ] Five spot-checked rows match the app exactly, including split names/amounts and paise-accurate values.
- [ ] The unfiltered export contains every transaction ever recorded.

**Your manual checks**
- Export a month you remember well; open it on a computer; verify a week of it by hand.
- Confirm the full export really is *everything* — the no-lock-in test.

**What you'll learn:** data portability, and why "the user can always leave" is a design principle, not just a feature.

**Likely to change:** almost nothing — the most mechanical phase, which is why it's last and sized S.

---

## Phase 11 — Account balances + envelope buckets (M)

> Added 2026-08-23 by owner decision (see change log). Work order: `workorders/phase11.md`.

**Goal:** You always know how much money you have and exactly where every rupee of it sits — and bucket money is fully yours to command.

**What we build**
- **Envelope buckets:** money in a bucket carries over until you spend or move it — the month-scoped allocation model is retired. Bucket balance = everything ever put in − everything ever spent from it, computed live (core rule 2).
- **Split anything, always confirmed:** any credit (salary is just the common case) — or any unallocated amount — can be split into buckets. Your plan becomes a **split template** that prefills an editable editor; nothing moves until you approve the lines. Salary-looking credits get a "Split this?" card; every credit gets a "Split into buckets…" action.
- **Manual control:** add to, take out of, and move between buckets anytime, with a per-bucket history where each manual entry can be deleted.
- **Account balances:** you state each account's real balance once; every captured transaction moves it. "Correct balance" writes a newer snapshot and logs an undoable action — drift (cash, missed SMS, bank fees) is corrected honestly, never silently.
- **The money map** at the top of the Buckets tab: total balance, per-account balances, and the distribution — each bucket (with its reserved part), and unallocated.

**What we do NOT build yet**
- No automatic splitting of anything — the owner confirms every split (owner decision).
- No balance predictions or spending forecasts; the map shows what IS, not what might be.

**Exit gate**
- [ ] Migration v5→v6 preserves every allocation as a ledger entry (same ids); undo of a pre-migration salary split still works.
- [ ] Any credit can be split exactly once via the confirmed editor; splitting unallocated money works without a source.
- [ ] Add / take out / move all work; a move's two legs always sum to zero and delete together.
- [ ] Balance: set once, tracks every captured transaction; a correction logs an undoable action; untracked accounts show "not tracked", never ₹0.
- [ ] Money map: total = sum of account balances; unallocated = total − bucket balances, verified by hand; Overview's total matches the Buckets tab exactly.
- [ ] All architect-owned tests pass unmodified.

**Your manual checks**
- Set your real BoB and HDFC balances, then make a real payment and watch the balance move by exactly that amount.
- Split a real credit, change one line in the editor before applying, and verify the bucket balances by hand.
- Move money between two buckets and check both cards; take money out and check unallocated grew.
- Compare the app's balance against your bank app after a few days; correct it once; confirm the correction shows in Activity and undo reverses it.

**What you'll learn:** reconciliation — why every ledger app must let reality overrule its own arithmetic, visibly.

**Likely to change:** which credits get the "Split this?" card (salary-detection only at first; we widen it if you find yourself splitting other credits often), and the money-map layout.

---

## Cross-phase rules (never suspended)

From ARCHITECTURE.md, restated because every phase must obey them:

1. **Money is integer paise.** Never floating-point.
2. **Bucket remaining is always computed** (allocated − spent − reserved), never a stored running total.
3. **Every automatic action stores its inverse** — undo is exact, not approximate.
4. **Every phase ends with the app usable on your phone.** If a phase can't demo on the real device, it isn't done.
5. **Corrections become rules.** Any fix you make twice is a bug in our automation.

## Progress tracker

| Phase | Name | Size | Status | Gate passed on |
|---|---|---|---|---|
| 0 | Dev environment & skeleton | S | ✅ done | 2026-08-22 |
| 1 | Manual entry + timeline | M | ✅ done | 2026-08-22 |
| 2 | SMS capture + activity log | L | ✅ done | 2026-08-22 |
| 3 | Buckets + salary split | M | ✅ done | 2026-08-22 |
| 4 | Recurring | M | ✅ done | 2026-08-22 |
| 5 | People & trips | M | ✅ done | 2026-08-23 |
| 6 | Overview screen | S | ✅ done | 2026-08-23 |
| 7 | Notification listener | M | ⛔ declined | — |
| 8 | Cloud mirror + sync | L | ⏸ parked | — |
| 9 | ChatGPT integration | M | ⏸ parked | — |
| 10 | Export | S | ⏸ on hold | — |
| 11 | Account balances + envelope buckets | M | ✅ done | 2026-08-23 |

## Change log

Plan changes are recorded here, never made silently.

| Date | Change | Why |
|---|---|---|
| 2026-08-22 | Plan created. | — |
| 2026-08-22 | Two-machine workflow adopted: Claude architects/reviews on the borrowed Mac (no heavy installs there), Codex implements/builds on Rajnikant's laptop, GitHub as the shared medium. Phase 0 amended accordingly; `AGENTS.md` + `workorders/` added. | Borrowed Mac has no disk space for the Android toolchain and shouldn't be modified; Rajnikant's own laptop has Codex and manageable space. |
| 2026-08-23 | Phases 8–9 (cloud mirror + ChatGPT) parked indefinitely by owner decision; phase 10 also on hold. After phase 7 the owner decides what happens next. Phase 7 runs in two stages (A: notification harvest, B: templates + merge), mirroring phase 2's approach. | Owner doesn't want the ChatGPT configuration now; until any backup path exists, note that all data lives on the phone only. |
| 2026-08-23 | Phase 7 (notification listener) declined by owner. `workorders/phase7a.md` cancelled; its planned v6 migration was never built, so the v6 slot is reused by phase 11. | Owner decision — SMS capture alone is doing the job. |
| 2026-08-23 | Phase 11 added: envelope buckets (carryover, owner-confirmed splits of any amount, manual add/take-out/move) + per-account balance tracking with visible corrections + the money map. Retires the month-scoped allocation model and the salary-only split. Decided with the owner: carryover yes; balances per account; splits always ask first; money map lives at the top of the Buckets tab. | Owner wants any amount splittable under manual control, and wants the app to know account balances — "knowing account's balance will change lot of things". |
