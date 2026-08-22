# Work order: Phase 0 — dev environment & project skeleton

Status: OPEN
Phase reference: PLAN.md → Phase 0

## Goal

Rajnikant's laptop can build this repo into an APK, and the app runs on his physical Android phone.

## Tasks, in order

1. **Toolchain.** Install Android Studio (latest stable) with the default SDK for this machine's OS.
   Confirm `JAVA_HOME`/SDK work by building once. If disk space is a concern on this machine,
   report free space in Result before installing.
2. **Project skeleton.** In the ROOT of this repository (docs and the Android project coexist),
   create a new Android project:
   - Template: Empty Activity (Compose)
   - App name: `Money Brain`
   - Package: `com.rajnikant.moneybrain`
   - Language: Kotlin, build config: Kotlin DSL
   - minSdk 26, targetSdk = latest stable
   - Keep the generated dependencies as-is; add nothing.
3. **Placeholder screen.** Replace the template greeting with a centered "Money Brain" title and
   the text "Phase 0 — skeleton". No other UI.
4. **Build.** Produce a debug APK (`./gradlew assembleDebug`). Record the APK path and size in Result.
5. **Install on the phone.** Guide Rajnikant (plain, numbered steps — he is not technical) to:
   enable Developer Options + USB debugging on his Android phone, connect via USB, accept the
   RSA prompt, then install via `adb install`. If USB fails, fall back to copying the APK to the
   phone and installing directly ("install unknown apps" permission).
6. **Reinstall test.** Change the placeholder text (e.g. append "v2"), rebuild, reinstall,
   confirm the update replaced the old version without uninstalling.
7. **Commit and push.** The full project skeleton, a root `.gitignore` covering Android
   (`.gradle/`, `build/`, `local.properties`, `.idea/`, `*.keystore`), and your Result below.
   Do NOT commit build outputs or `local.properties`.

## Acceptance (from PLAN.md Phase 0 gate)

- [ ] Money Brain icon opens on the physical phone.
- [ ] A code change reached the phone via rebuild+reinstall (task 6).
- [ ] Repo builds from a clean `git clone` on this machine.
- [ ] App survives a phone restart.

## Questions

(Write questions here and push if blocked. Do not guess.)

## Result

(Fill in after execution: what was done, APK path/size, device model + Android version,
anything that deviated from the order, anything the architect should look at.)
