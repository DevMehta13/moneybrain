# Work order: Phase 1 — database, manual entry, timeline

Status: OPEN
Phase reference: PLAN.md → Phase 1

## Goal

A genuinely usable cash-expense tracker: two-tap manual add, a timeline with edit/delete,
data that survives reboot. This phase creates the database everything later builds on.

## Architect-owned files already in the repo (do NOT alter their logic)

- `app/src/main/java/com/rajnikant/moneybrain/money/Money.kt` — the ONLY place rupees are
  formatted/parsed. All amounts everywhere are `Long` paise.
- `app/src/test/java/com/rajnikant/moneybrain/money/MoneyTest.kt` — must pass unmodified.
  If either fails to compile against the project setup, report in Result; do not "fix" them.

## Allowed new dependencies (none beyond these)

Via the version catalog, current stable versions compatible with the existing Kotlin/AGP:
- Room (runtime, ktx, compiler via **KSP** — add the KSP plugin)
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.navigation:navigation-compose`

No Hilt/Koin (manual wiring, below). No image, network, or utility libraries.

## Tasks

### 1. Data layer (Room)

Create `data/` package: entities, DAOs, `MoneyBrainDatabase` (version 1, exported schema on;
NEVER add `fallbackToDestructiveMigration` — user data is sacred).

**AccountEntity** — `id` (PK autoGenerate), `name`, `type` (String: `BANK` | `CARD` | `CASH`),
`createdAt` (epoch millis).

**CategoryEntity** — `id` (PK autoGenerate), `name`, `sortOrder` (Int).

**TransactionEntity** — `id` (PK autoGenerate), `amountPaise` (Long), `direction`
(String: `IN` | `OUT`), `accountId` (FK → Account, RESTRICT on delete), `categoryId`
(FK → Category, nullable, RESTRICT), `merchant` (String, nullable), `occurredAt` (epoch millis),
`notes` (String, nullable), `source` (String, `"MANUAL"` for everything this phase),
`fingerprint` (String, nullable, UNIQUE index — used by SMS dedupe in phase 2),
`referenceNo` (String, nullable), `createdAt` (epoch millis).
Plain indices on `occurredAt`, `accountId`, `categoryId`.

**DAOs** — `TransactionDao`: insert, update, delete, `observeAll(): Flow<...>` newest-first
(by `occurredAt` desc, then `id` desc), getById. `AccountDao` / `CategoryDao`: observeAll,
insert, getById; account also update.

**Seed on first creation** (Room `onCreate` callback): accounts `Bank` (BANK) and `Cash` (CASH);
categories in this order: Groceries, Food & Dining, Transport, Rent & Bills, Shopping,
Entertainment, Health, Personal, Other.

### 2. App wiring (keep it boring)

`MoneyBrainApp : Application` holding the database singleton. ViewModels take DAOs via a simple
`ViewModelProvider.Factory`. No DI framework, no repository layer yet — ViewModels may use DAOs
directly this phase.

### 3. Screens (Compose + navigation-compose)

Replace the placeholder with a Scaffold: bottom bar with **Timeline** and **Settings**
destinations, FAB "+" on Timeline → Add screen.

**Add transaction** (the two-tap promise: FAB → category = saved):
- Large amount field, auto-focused, numeric keyboard. Validate with `Money.parseToPaise`;
  disable saving while invalid or ≤ 0.
- Direction toggle OUT (default) / IN. Account selector defaulting to `Cash`.
- Optional: merchant/label text field, notes field, date-time (defaults now).
- Tapping a category chip SAVES immediately and returns to Timeline.

**Timeline**: reactive list (Flow → collectAsState), newest first, grouped under day headers
("Today", "Yesterday", else "21 Aug 2026"). Row: category name + merchant/notes line, account
name, amount via `Money.formatPaise` — prefix `+` and tint green (Material colorway) for IN.
Tap row → Edit.

**Edit transaction**: same fields as Add pre-filled, Save applies; Delete with a confirm dialog.

**Settings → Accounts**: list accounts with type; add account (name + type picker). Deleting an
account is NOT offered this phase (RESTRICT would make it confusing; skip the UI entirely).

Every rupee value on every screen goes through `Money.formatPaise`. No `String.format("%.2f")`,
no `toDouble()` on money anywhere — treat any such line as a bug.

### 4. Quality bar

- `./gradlew test` green, including `MoneyTest` untouched.
- App builds, installs, and runs on the phone; quick smoke test of add/edit/delete there.
- Rotation must not lose in-progress Add screen input (ViewModel state, not remember-only).

## Acceptance (from PLAN.md Phase 1 gate)

- [ ] Adding a cash expense = two taps + typing the amount.
- [ ] New entry appears at the top of the timeline instantly.
- [ ] Data survives force-close AND phone restart.
- [ ] Edit and delete both work and persist.
- [ ] Amounts display correctly everywhere via Money.kt (₹0.05, ₹1,000, ₹1,23,456.78 cases).
- [ ] `./gradlew test` passes with architect-owned tests unmodified.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

Implemented the Phase 1 database and manual-entry foundation:

- Added Room database version 1 with exported schema at
  `app/schemas/com.rajnikant.moneybrain.data.MoneyBrainDatabase/1.json`; there is no
  destructive-migration fallback. Accounts, categories, and transactions use the requested
  fields, foreign keys, RESTRICT deletion behavior, and indices. The unique nullable
  fingerprint index is present for Phase 2 deduplication.
- First-run seeding creates Bank and Cash accounts plus the nine requested categories in the
  specified order.
- Added `MoneyBrainApp` database ownership, direct DAO ViewModel wiring through a small
  `ViewModelProvider.Factory`, a Timeline/Settings bottom navigation layout, add/edit/delete
  transaction flow, grouped reactive timeline, and Accounts management (add only; no delete).
  The add screen defaults to Cash and saves immediately when a category is tapped. Edit has a
  confirmation dialog before delete. All on-screen amounts use the architect-owned `Money` API.
- Added only the allowed dependencies and catalog versions: Room 2.8.4, KSP
  2.2.10-2.0.2, lifecycle-viewmodel-compose 2.11.0, and navigation-compose 2.9.8.
  `android.disallowKotlinSourceSets=false` is required as a KSP compatibility setting with the
  current AGP built-in Kotlin setup; AGP reports it as experimental, so the architect should
  review this compatibility setting before a future Kotlin/AGP upgrade.
- `./gradlew test` passed. The architect-owned `MoneyTest` ran unchanged: 25 tests, 0 failures,
  0 errors. `./gradlew assembleDebug` also passed.
- Installed and launched on the connected Google Pixel 9 (Android 17). Room created the on-device
  database successfully and Timeline opened with its empty-state screen. No test transaction was
  saved. The automated add/edit/delete smoke flow could not be completed because TalkBack on the
  phone intercepted the adb-driven touch input; its accessibility configuration was left unchanged.
  Manual smoke testing of add, edit, delete, and persistence across a phone restart remains for
  Rajnikant before the phase gate can be declared passed.
