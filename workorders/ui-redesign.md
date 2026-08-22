# Work order: Phase 12 — Modernist UI re-skin (BUILD & REPORT ONLY)

Status: OPEN
Phase reference: PLAN.md → Phase 12. Design spec: `design_handoff_moneybrain_ui/README.md`.

## Role change for this phase — read carefully

The architect (Claude) wrote ALL the UI code for this phase directly:
- `ui/theme/Theme.kt` (rewritten), `ui/Modernist.kt` (new), `ui/MoneyBrainScreen.kt` (five
  screens restyled), `MainActivity.kt`, `res/values/font_certs.xml` (new),
  `gradle/libs.versions.toml` + `app/build.gradle.kts` (one new dependency:
  `androidx.compose.ui:ui-text-google-fonts`, BOM-managed).

**Codex must NOT modify any Kotlin code in this phase — not even to fix a compile error.**
The architect writes code without a compiler and expects possible errors; fixing them
blind-swaps intent. Instead:

## Tasks

1. Pull latest main.
2. `./gradlew test` — must stay green (no logic was touched; if a unit test fails, STOP and report).
3. `./gradlew assembleDebug`.
   - If compilation FAILS: paste every error message VERBATIM (file, line, message) into the
     Result section below, push, and stop. Do not attempt fixes.
4. If it builds: install on the connected phone over existing data.
5. Smoke pass, and report what you see in Result:
   - All five tabs open; no crash on Overview / Timeline / Buckets / Recurring / Settings.
   - Overview: SAFE TO SPEND hero, bucket bars, bills, stat strip, recent rows.
   - Timeline: day headers with signed totals, red ADD TRANSACTION bar above the nav, tags.
   - Buckets: money map, envelope cards with BALANCE/RESERVED/AVAILABLE, split-this card if
     one is pending.
   - Settings: inverted PRIVATE BY DESIGN card, CAPTURE and MONEY groups.
   - Toggle system dark mode: page goes dark ink, text light, accent turns #FF563C.
   - Fonts: text should render in Archivo (geometric grotesque; compare against the design
     HTML). If it clearly renders as the default system font, the ONE file Codex may replace
     is `app/src/main/res/values/font_certs.xml` — re-copy it verbatim from the official
     AndroidX downloadable-fonts sample (it is a resource, not code) and note that in Result.
6. Push the Result section. Screenshots are not transferable through the repo workflow —
   describe what deviates from `design.dc.html` in words; the owner does the visual review.

## Acceptance

- [ ] `./gradlew test` green, `assembleDebug` builds, app installs and opens over live data.
- [ ] No crash on any tab or on the transaction editor.
- [ ] Result section reports font status, dark-mode status, and any visual deviations.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

(Fill in after execution.)
