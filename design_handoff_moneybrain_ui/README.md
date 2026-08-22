# Handoff: Money Brain — Modernist UI redesign

## Overview
Redesign of Money Brain's five main screens (Overview, Timeline, Buckets, Settings light + Overview dark) in the "Modernist" visual system: flat, architectural, Archivo type, zero corner radius, strong 2px rules, one red accent used sparingly, everything flush left.

## About the Design Files
The files in this bundle are **design references created in HTML** — they show intended look and behavior, not production code. The task is to **recreate these designs in the existing Money Brain codebase**: Kotlin, Jetpack Compose, Material 3 (`app/src/main/java/com/rajnikant/moneybrain/ui/MoneyBrainScreen.kt` and `ui/theme/`). Keep all existing ViewModels, DAOs and navigation exactly as they are — this is a re-skin plus layout restructure of the composables only.

## Fidelity
**High-fidelity.** Recreate pixel-close: exact colors, type sizes, weights, letter-spacing and spacing are listed below. Use dp/sp 1:1 for the px values (designs are at 412dp phone width).

## Implementation strategy (Compose)
1. Replace the Material 3 dynamic theme with a custom `MoneyBrainTheme`:
   - `lightColorScheme(background=0xFFF3F2F2, surface=0xFFEAE9E9, onBackground=0xFF201E1D, primary=0xFFEC3013, error=0xFFAE1800, …)`
   - `darkColorScheme(background=0xFF201E1D, onBackground=0xFFF3F2F2, primary=0xFFFF563C, …accent text 0xFFFF9783)`
   - Typography: Archivo (Google Fonts, weights 400/600/800) for everything. `RoundedCornerShape(0.dp)` for ALL shapes (cards, buttons, chips, dialogs, text fields).
2. Build small shared composables: `SectionRule` (2px divider, ink @40% / white @35% dark), `KickerLabel` (uppercase, 800, letterSpacing 0.06–0.1em, 55% ink), `BucketBar` (6dp flat track/fill, no radius), `MbTag` (small uppercase chip), `MbBottomBar` (5 uppercase labels, active = 3dp red top rule + weight 800).
3. Numbers everywhere use tabular figures: `fontFeatureSettings = "tnum"`.
4. No FloatingActionButton — the add action is a full-width red bar above the bottom nav on Timeline.

## Design tokens
Colors (light): bg `#F3F2F2`, surface `#EAE9E9`, ink `#201E1D`, accent `#EC3013`, accent-deep (text-size red) `#AE1800`, accent-tint bg `#FFE0D9` / `#FFF2EF`, track `#D7D3D3`, divider `rgba(32,30,29,.4)` at 2px, row rule `rgba(32,30,29,.15)` at 1px, muted text = ink at 50–55%.
Colors (dark): bg/ink swap — bg `#201E1D`, text `#F3F2F2`, accent fill `#FF563C`, accent text `#FF9783`, attention strip bg `#4D170E`, track `#444141`, divider `rgba(243,242,242,.35)`.
Radius: **0 everywhere.** Spacing: 4/8/12/16/24/32. Screen gutter 20.
Type scale (sp): screen title 22/800; hero number 46/800, letterSpacing −0.02em, tabular; card money 26/800; section header 13/800 caps +0.06em; kicker 11/800 caps +0.1em; row title 14–14.5/600; row meta 11/400 muted; tags 10/800 caps; amounts 14–15/600 (income: 800, `#AE1800`, "+" prefix); bottom nav 10 caps.

## Screens
### 1. Overview (`design.dc.html` option 1a; dark = 1e)
Top to bottom, each block separated by a 2px rule:
- Header row: "MONEY BRAIN" kicker left, "AUGUST 2026" muted right. 14px top / 10px bottom padding.
- Needs-attention strip (only when non-empty): bg `#FFE0D9`, 10x20 padding, underlined links in `#AE1800`, 12/600: unrecognised SMS count → Activity, uncategorised count → Timeline(filtered), detected recurring → Recurring.
- Hero: kicker "SAFE TO SPEND", then the one number `allocated − spent − reserved` at 46/800, then muted 12px line "of ₹X allocated · ₹Y reserved for bills · N days left".
- Buckets: section header + "EDIT PLAN" red 11/600 right. Per bucket: name (14/600) left, remaining right (over-budget: "−₹430 over" in `#AE1800`/600); 6dp bar below (fill ink; 100% red `#EC3013` when overspent).
- Upcoming bills: header + "₹7,350 this month" muted right; rows name / due date (muted, middle) / amount (600), 1px row rules.
- People + trip strip: three stat cells (kicker + 17/800 number): OWED TO YOU / YOU OWE / ACTIVE TRIP.
- Recent: header + "SEE ALL" red; transaction rows (see Timeline row spec), 3 items.
- Bottom nav: OVERVIEW active.

### 2. Timeline (1b)
- Title "Timeline" 22/800; search field: 2px ink@40% border, 0 radius, placeholder muted 13px.
- Filter chips row (wrap, 8 gap): active chip solid ink with bg-color text; others 1px outline; "UNCATEGORISED 4" outline with `#AE1800` text.
- Day groups: header row "TODAY · 22 AUG" kicker left + signed day total right (tabular), groups separated by 2px rule.
- Transaction row: left column merchant 14.5/600, meta line "1:12 pm · HDFC · SMS" 11 muted, tag row (6 gap): category tag (bg `#EAE9E9`), "AUTO" tag (bg `#FFE0D9`, text `#AE1800`) when source is SMS-auto; uncategorised rows show dashed-outline "PICK CATEGORY" tag instead. Right column: amount 15/600 tabular (income +₹ in 800 `#AE1800`); auto rows get a red "UNDO" 10/800 text button under the amount.
- Above bottom nav: full-width red bar `#EC3013`, white text "ADD TRANSACTION" left, "+" right, 12x20 padding.

### 3. Buckets (1c)
- Header "Buckets" + month right, 2px rule.
- Salary-detected card (when pending): 2px `#EC3013` border, bg `#FFF2EF`; kicker "SALARY DETECTED · 1 AUG" in `#AE1800`; ₹85,000 26/800; per-bucket preview lines (name·plan left, amount 600 right) + muted "Unallocated" line; footer split by 2px red rule into two flush-left actions: solid red "SPLIT NOW" and ghost "NOT THIS TIME" (`#AE1800`).
- "THIS MONTH" header, then per-bucket cards: bg `#EAE9E9`, 1px ink@25% border (overspent: 2px `#EC3013` + "OVERSPENT" kicker); name 15/800 + plan note right (muted caps 11); 6dp bar; 4-col grid ALLOC/SPENT/RESERVED/LEFT (labels 9.5/800 caps muted, values 13/600; LEFT 800, negative in `#AE1800`).
- Footer row button: 2px ink border, "EDIT SPLIT PLAN & ORDER" + →.

### 4. Settings (1d)
- Title, 2px rule; inverted card: bg ink `#201E1D`, kicker "PRIVATE BY DESIGN" in `#FF9783`, body 13px light text.
- Groups CAPTURE / MONEY / DATA: group kicker, 2px rule, rows (title 14/600, sub 11 muted, → or status tag right, 1px rules). Contents mirror existing routes: SMS capture (ON tag), Category rules, Activity log, Accounts, Salary plan, Category→bucket mapping, People, Trips, Export, Backup.

### 5. Dark theme (1e)
Same layout as Overview with dark tokens above. Bars: fill `#F3F2F2` on `#444141` track; overspent fill `#FF563C`.

## Interactions & behavior
- All list rows navigate as the current code already does (edit transaction, bucket plan, etc.). UNDO calls the existing action-undo path. Attention links carry the existing filters (`uncategorised=true` etc.).
- Press states: darken fills one ramp step (red → `#DD2B0F`); no ripples with rounded bounds — keep rectangular.
- No elevation/shadows on cards; structure comes from rules and borders.

## Assets
No images or icon fonts. Icons, if needed later, are Lucide (lucide.dev), 1.5–2dp stroke, ink color. The "→" and "+" glyphs are plain text in the mocks.

## Files
- `design.dc.html` — the annotated mockups (open in a browser; five phone frames labeled 1a–1e).
- `ds.css` — the Modernist token sheet the design is derived from (hex ramps, spacing, type).
- `android-frame.jsx` — device-frame chrome only; ignore for implementation.
