# CHANGES

## 0.1.0

- **Fix iOS UIScene crash** — the plugin force-unwrapped `appDelegate.window`,
  which is `nil` under the UIScene lifecycle (Flutter 3.35+), crashing the app
  on launch. Window and view-controller lookup is now scene-safe. Resolves #12
  and #13.
- Add Swift Package Manager support on iOS; CocoaPods still works.
- Drop the `quiver` dependency — hashing now uses the built-in `Object.hash`
  / `Object.hashAll`.
- Android: Kotlin 1.8.22 → 2.3.21, AGP 8.1.0 → 8.11.1, Gradle 8.3 → 8.14,
  `compileSdk` 36, coroutines 1.6.4 → 1.9.0; migrated to the Kotlin
  `compilerOptions` DSL.
- iOS: minimum deployment target 13.0; bundled the privacy manifest; fixed the
  placeholder podspec metadata.
- Removed the stale Android unit test; raised `flutter_lints` to 6.0.
- Fixed the broken changelog (duplicate `0.0.2+1` entries, missing `0.0.3`).

## 0.0.3

- Add a `note` field to `ContactInfo`.

## 0.0.2+1

- Change the `Contact` object to `ContactInfo` in README.

## 0.0.2

- Change the `Contact` object to `ContactInfo`.
- Change the `Item` object to `ValueItem`.

## 0.0.1

- Release the package.
