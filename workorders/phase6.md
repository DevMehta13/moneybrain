# Work order: Phase 6 — the Overview screen

Status: OPEN
Phase reference: PLAN.md → Phase 6

## Goal

One glanceable home screen the owner can trust at a glance, proper navigation, and the
timeline's search + filters. Pure composition: NO new data features, NO schema change,
NO new dependencies, NO architect-owned changes.

## The one law of this phase

**Every number on Overview must come from the same computation its detail screen uses.**
Extract the existing status/summary logic (bucket status incl. reserved, person balances,
trip totals, upcoming recurring) into shared functions/flows that BOTH the detail screen
and Overview consume. Duplicated queries that could drift are a review-fail.

## Tasks

### 1. Navigation restructure

- Bottom bar becomes: **Overview · Timeline · Buckets · Recurring · Settings**
  (Overview is the start destination).
- Activity moves off the bar: reachable from Overview's "Recent activity" header and a
  Settings card. People and Trips stay in Settings AND are reachable from their Overview
  cards.
- Replace every text-glyph tab "icon" with proper Material icons
  (androidx.compose.material.icons: Home, List/Receipt, AccountBalanceWallet, Autorenew,
  Settings; FAB gets Icons.Default.Add). No new dependency — material-icons-core ships
  with Compose Material3.

### 2. Overview screen (product.md §7, top to bottom)

1. **Buckets headline**: per bucket — name + Remaining (red when negative), current month.
   Tap → Buckets tab.
2. **Upcoming bills**: the next 3 items from the 30-day window with name/amount/date +
   "This month ₹X" total. Tap → Recurring tab.
3. **Active trip** (only when one is running): name + total so far. Tap → trip detail.
4. **People**: "Owed to you ₹X · You owe ₹Y" (net summary). Tap → People screen.
5. **Recent transactions**: latest 5, compact rows (merchant/category + amount, auto
   marker preserved). Tap row → editor; "See all" → Timeline.
6. **Needs attention strip** (TOP of the screen, above buckets, only when non-empty):
   "N unrecognised SMS · M uncategorised · K detected recurring" — each segment taps to
   its home (Activity, Timeline filtered to uncategorised, Recurring).

### 3. Timeline search + filters (completes the product.md Timeline spec)

- Search field: case-insensitive substring over merchant, notes, and category name.
- Filter chips (combinable): Account, Category, Bucket, Trip, Direction, Uncategorised.
  Person filter: transactions having a SPLIT/LENT/SETTLEMENT ledger row for that person.
- Filtering happens in the ViewModel over the existing flows; day headers still correct
  for the filtered list.
- The needs-attention "M uncategorised" taps into Timeline with the Uncategorised filter
  pre-applied.

### 4. Polish-list items that belong to this phase

- **Trip delete**: on ended trips only, confirm dialog; inside ONE withTransaction —
  set tripId = NULL on all its transactions, then delete the trip row.
- **Midnight month rollover**: bucket status month must recompute when the screen is
  (re)entered, not be frozen at ViewModel creation (compute the month string inside the
  flow, or refresh it on resume) — an app left open across the 1st shows the new month
  after any navigation.
- Plan editor global-order clarity was already fixed (Split order section) — no action.

### 5. Quality bar

- `./gradlew test` green, architect suites unmodified. Install on top; data intact.
- Cold app open lands on Overview quickly (~2s on the Pixel).
- Cross-check EVERY Overview number against its detail screen with real data.

## Acceptance

- [ ] Overview shows all six sections with real data; every number identical to its
      detail screen at the same moment.
- [ ] Needs-attention strip appears only when non-empty; counts correct; each segment
      navigates to the right place (uncategorised lands pre-filtered).
- [ ] Search finds by merchant, notes, and category name; filters combine correctly;
      person filter works.
- [ ] Navigation: Overview is home; all 8 product.md screens reachable; real icons.
- [ ] Trip delete unfiles its transactions atomically, only for ended trips.
- [ ] Month rollover: bucket month refreshes without an app restart.
- [ ] All architect-owned tests pass unmodified.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

- Interim implementation: added a shared summary layer used by Bucket status, People totals,
  recurring upcoming/detection, and trip totals, then composed the Overview from those functions.
  Overview is the new home, has needs-attention, bucket, upcoming, active-trip, people, recent
  transaction, and recent-activity sections. Bottom navigation now has Overview/Timeline/Buckets/
  Recurring/Settings with visual platform icons and no dependency change.
- Timeline now supports case-insensitive merchant/note/category search and combined account,
  category, bucket, trip, person, direction, and uncategorised filters in its ViewModel. Bucket
  month state refreshes on source recomputation rather than being a fixed constructor value.
- Added the DAO operations required for atomic deletion of an ended trip, but the delete control
  and the uncategorised deep-link still need their final UI wiring. Settings also still needs its
  Activity card. Phase remains OPEN.
- `./gradlew test` and the debug build pass; the APK was installed over the existing app.
