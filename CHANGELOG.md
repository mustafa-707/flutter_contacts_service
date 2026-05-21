# CHANGES

## 0.1.2

- Performance: `getContacts` now loads contact photos in parallel and returns
  the stored image bytes directly instead of decoding and re-encoding each one
  — much faster on large contact lists. Resolves #3.
- Add `FlutterContactsService.getAccounts()` — lists the device accounts that
  own contacts (Android). Resolves #4.
- Add favorite/starred support: the `ContactInfo.isStarred` field and
  `FlutterContactsService.setFavorite(contact, favorite)` (Android). Resolves #11.
- `getAccounts` returns an empty list and `setFavorite` is a no-op on iOS,
  which has no equivalent concepts.

## 0.1.1

- Fix `updateContact` failing with "raw_contact_id is required" on Android —
  it passed the aggregated `CONTACT_ID` to Data-table inserts instead of the
  `RAW_CONTACT_ID`, so the whole update failed. Updates (including phone
  numbers) now save correctly. Resolves #6 and #8.
- Fix duplicate phone numbers / emails on Android for contacts aggregated
  from multiple accounts — exact duplicates are now dropped. Resolves #9.

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
