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

- `./gradlew test` completed with zero failing or erroring tests in the generated unit-test
  reports. `./gradlew assembleDebug` completed and produced the debug APK; no compilation
  errors were emitted. The APK installed successfully over the existing Pixel 9 app data.
- Smoke pass: Overview, Timeline, Buckets, Recurring, and Settings all opened without a crash;
  opening a transaction editor also succeeded. Overview showed the SAFE TO SPEND hero, bills,
  stat strip, and recent rows (there were no non-zero bucket bars in the installed data).
  Timeline showed day headers with signed totals, tags, and the red ADD TRANSACTION bar above
  the navigation. Buckets showed the money map and split-an-amount control; no pending split or
  populated envelope cards existed in this device data. Settings showed the inverted PRIVATE BY
  DESIGN card with CAPTURE and MONEY groups.
- Dark mode was toggled via the system setting: the editor rendered dark ink with light text and
  the warm orange-red accent (#FF563C appearance). The setting was restored to light mode after
  the check. Text rendered as a geometric grotesque consistent with Archivo, not the default
  system fallback. No visual deviation from `design.dc.html` was evident in the inspected states.
