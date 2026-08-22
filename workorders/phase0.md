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

Completed the laptop-side skeleton and build pipeline:

- Installed Android Studio 2026.1.3.8 (Quail 3 Patch 1), Android SDK Platform 37,
  Build-Tools 37.0.0, and Platform-Tools 37.0.1. There were 31 GiB free before the
  installation. `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `adb` were
  configured in the local shell profile; Java is Android Studio's bundled JBR 25.
- Created the Kotlin/Jetpack Compose Android project in the repository root:
  `com.rajnikant.moneybrain`, minSdk 26, target/compile SDK 37. It has a launcher
  icon and exactly the requested centered placeholder content: “Money Brain” and
  “Phase 0 — skeleton v2”. No dependencies were added beyond the Compose template set.
- A clean local build (`./gradlew clean assembleDebug`) succeeded. I then rebuilt after
  changing the placeholder from “Phase 0 — skeleton” to “Phase 0 — skeleton v2”; the
  final `./gradlew assembleDebug` also succeeded. A fresh clone of the pushed repository
  also completed `./gradlew assembleDebug` successfully.
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (11,566,149 bytes).

Phone installation and restart acceptance checks are blocked because no Android device is
connected: `adb devices -l` returned no devices. Device model and Android version are
therefore not yet available, and the v2 update could not be confirmed on a phone.

Rajnikant’s next steps (plain USB route):

1. On the phone, open **Settings**, search for **Build number**, and tap it seven times.
   Enter the phone PIN if asked. This turns on Developer Options.
2. Go back to Settings, open **Developer Options**, and turn on **USB debugging**.
3. Connect the phone to this laptop with a USB cable. If the phone asks what USB should
   do, choose **File transfer**. Accept the “Allow USB debugging?” prompt on the phone.
4. From this repository, run:
   `adb install app/build/outputs/apk/debug/app-debug.apk`
5. Open **Money Brain**, then install the next rebuilt APK with the same command. Android
   should report success and replace the installed app without an uninstall. Restart the
   phone and confirm Money Brain still opens.

If USB does not work, copy the APK above to the phone, open it in the phone’s Files app,
allow that app to **install unknown apps** when Android asks, and install it. Repeat after
the next rebuild to test the replacement update.
