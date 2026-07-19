# ARIA — Android voice assistant

Single-module Android app (Kotlin, Gradle Kotlin DSL). See `BUILD_INSTRUCTIONS.md` for the product overview and command reference. Module: `:app` (`com.aria.assistant`), `minSdk 26`, `targetSdk`/`compileSdk 34`.

## Cursor Cloud specific instructions

The dev environment (JDK 17 + Android SDK) is provisioned by the startup update script; the notes below are the non-obvious things needed to build/test/run.

- Toolchain: build with JDK 17 (AGP 8.2.2 does not support the VM's default JDK 21). `JAVA_HOME`, `ANDROID_HOME`/`ANDROID_SDK_ROOT` and SDK `PATH` entries are exported in `~/.bashrc`; run `source ~/.bashrc` if a fresh non-login shell doesn't have them. Installed SDK: `platforms;android-34`, `build-tools;34.0.0`, `platform-tools`.
- `local.properties` (holds `sdk.dir`) is machine-specific and git-ignored; the update script recreates it if missing. Do not commit it.
- Standard commands (run from repo root):
  - Build debug APK: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  - Unit tests: `./gradlew testDebugUnitTest` (JVM, uses Robolectric — no device needed)
  - Lint: `./gradlew lint` → reports in `app/build/reports/`
- Lint gotcha: `./gradlew lint` exits non-zero because of pre-existing app-design findings in `AndroidManifest.xml` (`ProtectedPermissions` for `WRITE_SETTINGS`/`BIND_NOTIFICATION_LISTENER_SERVICE` and missing `uses-feature` tags). Lint tooling itself works; treat these as known and read the report rather than assuming the run is broken.
- No emulator/GUI: the VM has no KVM (`/dev/kvm` absent), so an Android emulator cannot boot and the app UI cannot be launched here. Exercise app logic via the Robolectric unit tests (`./gradlew testDebugUnitTest`) or install `app-debug.apk` on a physical device to run the full UI.
- The AI features need an Anthropic API key entered in-app at runtime (Settings → API key); no build-time secret is required.
