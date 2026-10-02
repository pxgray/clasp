# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Clasp: an Android wallet for loyalty cards, boarding passes and Apple Wallet (`.pkpass`) passes. No payments, no accounts, no network. UI polish is the main goal, so visual changes matter as much as behavior. Licensed GPL-3.0-or-later; intended for F-Droid.

## Commands

Gradle must run on Android Studio's bundled JDK (no system JDK is installed), and `local.properties` needs `sdk.dir=$HOME/Android/Sdk`:

```bash
env JAVA_HOME=/opt/android-studio/jbr ./gradlew assembleDebug
env JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest                              # all tests
env JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests '*BcbpParserTest'    # one class
env JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest --tests '*BcbpParserTest.parses*'  # one method (names use backticks with spaces)
env JAVA_HOME=/opt/android-studio/jbr ./gradlew verifyRoborazziDebug   # all tests + compare screenshots with goldens
env JAVA_HOME=/opt/android-studio/jbr ./gradlew recordRoborazziDebug   # re-record goldens after an intended UI change
env JAVA_HOME=/opt/android-studio/jbr ./gradlew lintDebug              # must report no issues
env JAVA_HOME=/opt/android-studio/jbr ./gradlew installDebug
```

CI (`.github/workflows/ci.yml`) runs `assembleDebug`, `verifyRoborazziDebug` and `lintDebug` on pushes to `main` and on PRs. Land changes through pull requests.

## Testing notes

- Every test is a local JVM test; Android-dependent ones use Robolectric (`@RunWith(AndroidJUnit4::class)`), pinned to SDK 36 in `app/src/test/resources/robolectric.properties`. `app/build.gradle.kts` passes `--add-opens`/`--add-exports` so Robolectric works on JDK 25 — keep them.
- Robolectric's `ContentResolver` only serves registered URIs: use `shadowOf(resolver).registerInputStreamSupplier(uri) { … }`. Bitmap code needs `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.
- Screenshot goldens live in `app/src/test/screenshots/` and are rendered from the stateless screen composables (`HomeScreen`, `DetailScreen`, `EditScreen`, …) in `screenshots/ScreenshotTest.kt`. Plain `testDebugUnitTest` skips the comparison.
- Shared fixtures are in `T/testing/` (`loyaltyItem()`, `boardingItem()`, `BCBP_SAMPLE`, `pkpassZip()`, `FakeWalletRepository`, `MainDispatcherRule`, `FixedClock`).
- The home list reads from Room asynchronously; flow tests must `waitUntil…` for new items rather than assert immediately.

## Architecture

Single `:app` module, package `io.github.pxgray.clasp`:

- `domain/` — pure model. `WalletItem` has an `ItemKind` (`LOYALTY`, `BOARDING_PASS`, `PASS`) and a sealed `ItemDetails` (`Loyalty`, `BoardingPass`, `Pass`) that is stored as a JSON column (`WalletJson` in `data/db/Converters.kt`, `type` discriminator). Changing `ItemDetails` fields changes stored and backed-up JSON.
- `data/` — `WalletRepository` (Room implementation + test fake), `ItemFiles` (per-item files under `filesDir/items/<id>/`), logo import, `.pkpass` import, `SettingsRepository` (DataStore), `backup/WalletBackup` (zip of `wallet.json` + item files).
- `barcode/` (ZXing encode/decode, quiet zones), `bcbp/` (IATA boarding-pass barcode parser), `pkpass/` (zip → `PkPass` → `WalletItem`) — pure JVM, no Android UI.
- `ui/` — one package per screen; each `XRoute` composable binds a ViewModel to a stateless `XScreen`.

Cross-cutting patterns:

- **DI**: `AppContainer`, created by `ClaspApplication`. ViewModel factories read it via `this[APPLICATION_KEY] as ClaspApplication` and read route arguments with `createSavedStateHandle().toRoute<Destination.X>()`.
- **Navigation**: type-safe `Destination` routes in `ClaspNavHost`, wrapped in `SharedTransitionLayout`. The card → detail transition uses `Modifier.sharedCardBounds(id)` / `sharedLogo(id)`, which are no-ops outside the nav host (previews, tests, the edit preview card passes `sharedKey = null`).
- **Delete with undo**: Detail puts the id into the Home back-stack entry's `savedStateHandle`; `HomeViewModel` hides it as a pending delete and commits it when the snackbar is dismissed.
- **Scan → edit**: the scanner navigates to `Destination.Edit(scannedValue, scannedFormat)`; `EditFormState.fromScan` decides boarding pass (BCBP) vs loyalty card. Scanned values are stored byte-for-byte — never trim a BCBP value.
- **Incoming passes**: `MainActivity` (singleTop) puts `ACTION_VIEW` URIs into `ImportInbox`; `ClaspNavHost` pops to Home, whose `ImportViewModel` shows the preview sheet. pkpass item ids derive from `passTypeIdentifier`/`serialNumber`, so re-imports update rather than duplicate.
- **Logos**: written as `items/<id>/logo-<timestamp>.png` (unique per import, cached by path in `LogoTile`); after save/cancel, `ItemFiles.pruneLogos` removes the unused ones.
- **Theme**: `MainActivity` waits for `SettingsRepository` before drawing, resolves light/dark, and restyles the system bars. Screens with custom-colored headers use `SystemBarIconsEffect`.

## Constraints and gotchas

- F-Droid friendly: FOSS dependencies only (no Play Services, ML Kit, Firebase), no `INTERNET` permission, no build-time network beyond Gradle repos. New dependencies must be Apache-2.0-compatible and added to the list in `ui/settings/LicensesScreen.kt`.
- AGP 9 compiles Kotlin itself — don't apply `org.jetbrains.kotlin.android`. Material3 is pinned to a `1.5.0` alpha for the Expressive APIs; `ExposedDropdownMenu` must be imported explicitly.
- User-visible strings go in `res/values/strings.xml`.
- Untrusted input (`.pkpass` files, backups) goes through size caps and path validation — keep that when touching the importers.
- Local AI planning files (`docs/superpowers/`, `.remember/`) are gitignored and must not be committed or mentioned in commit messages. Commits keep the `Co-Authored-By: Claude` trailer (the README discloses AI use).
