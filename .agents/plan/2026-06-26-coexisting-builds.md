# Implementation Plan - Coexisting Debug & Release Builds

Separate the development environment from the official personal-use application to prevent database, settings, or file storage collision.

## System / Contract Summary
- **App Package Name:** Debug uses suffix `.debug` (`com.example.medianest.debug`), while stable release uses official package (`com.example.medianest`).
- **App Launcher Labels:** Release shows "Media Nest", Debug shows "Media Nest (Debug)".
- **Storage Subfolders:** Fallback downloads directory is partitioned into `MediaNest` (Release) and `MediaNestDebug` (Debug).
- **Settings Screen:** Update checking disabled for Debug builds.

---

## Phase Order
1. Phase 1: Build Type Package ID suffix.
2. Phase 2: App Name Differentiator.
3. Phase 3: Storage Isolation Fallback.
4. Phase 4: Disable Update Checks in Debug Mode.

---

## Steps

### Phase 1: Build Type Package ID suffix
- **What:** Configure Gradle to add `.debug` suffix to package name for debug build variant.
- **Where:** [app/build.gradle.kts](file:///d:/dev/media-nest/app/build.gradle.kts#L27-L33)
- **How:** Add the `debug` block under `buildTypes`:
  ```kotlin
      buildTypes {
          release {
              optimization {
                  enable = false
              }
          }
          debug {
              applicationIdSuffix = ".debug"
          }
      }
  ```
- **Why:** Assigns different Application ID to debug builds, enabling simultaneous installation on Android.
- **Edge cases:** None. Implicit debug config exists but adding explicit block configures suffix.
- **Pitfalls / do not:** Do not change package declaration namespace at the top of gradle file.
- **Validation:** Sync project and run `./gradlew assembleDebug` to verify it compiles.
- **Docs:** None.

### Phase 2: App Name Differentiator
- **What:** Override `@string/app_name` for debug build variant.
- **Where:** [NEW] [strings.xml](file:///d:/dev/media-nest/app/src/debug/res/values/strings.xml)
- **How:** Create debug values resource directory and string file:
  ```xml
  <resources>
      <string name="app_name">Media Nest (Debug)</string>
  </resources>
  ```
- **Why:** Gradle automatically overrides string resources in `src/debug` during compilation for debug builds.
- **Edge cases:** None.
- **Pitfalls / do not:** Do not modify the main `strings.xml` file.
- **Validation:** Check the app name after installing the debug build.
- **Docs:** None.

### Phase 3: Storage Isolation Fallback
- **What:** Choose default storage subfolder dynamically.
- **Where:** [DownloadPreferences.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/data/preferences/DownloadPreferences.kt#L30-L36)
- **How:** Modify `downloadFolder` flow to check `com.example.medianest.BuildConfig.DEBUG`:
  ```kotlin
      val downloadFolder: Flow<String> = context.downloadStore.data.map { prefs ->
          prefs[KEY_DOWNLOAD_FOLDER] ?: try {
              val folderName = if (com.example.medianest.BuildConfig.DEBUG) "MediaNestDebug" else "MediaNest"
              File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), folderName).absolutePath
          } catch (_: Exception) {
              val folderName = if (com.example.medianest.BuildConfig.DEBUG) "MediaNestDebug" else "MediaNest"
              File(context.getExternalFilesDir(null) ?: context.filesDir, folderName).absolutePath
          }
      }
  ```
- **Why:** Separates fallback storage path to avoid writing videos to same default folder.
- **Edge cases:** Shared storage permissions.
- **Pitfalls / do not:** Do not hardcode the package string inside preferences.
- **Validation:** Inspect default download path value in App settings.
- **Docs:** None.

### Phase 4: Disable Update Checks in Debug Mode
- **What:** UI check to disable update checking when `BuildConfig.DEBUG` is active.
- **Where:** [SettingsScreen.kt](file:///d:/dev/media-nest/app/src/main/java/com/example/medianest/ui/screens/SettingsScreen.kt#L882-L890)
- **How:** Edit settings update card content:
  ```kotlin
                      val isDebug = com.example.medianest.BuildConfig.DEBUG
                      when (val s = updateState) {
                          is UpdateState.Idle -> {
                              if (isDebug) {
                                  Text(
                                      text = "Updates are disabled in debug builds. Please update via Gradle/Android Studio.",
                                      style = MaterialTheme.typography.bodyMedium,
                                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                                      modifier = Modifier.padding(vertical = 4.dp)
                                  )
                              } else {
                                  Button(
                                      onClick = { viewModel.checkForUpdates() },
                                      modifier = Modifier.fillMaxWidth()
                                  ) {
                                      Text("Check for Updates")
                                  }
                              }
                          }
  ```
- **Why:** Prevents testing builds from triggering downloads of production APKs.
- **Edge cases:** None.
- **Pitfalls / do not:** Do not disable update state flow collection; just hide/replace button action.
- **Validation:** Verify the button is replaced by info text in the debug build settings view.
- **Docs:** None.

---

## Beginner Implementation Guide
For new developers:
1. Open the project in Android Studio or terminal.
2. Complete code changes.
3. Sync Gradle files.
4. Deploy to phone via USB/wireless debugging (installs Debug app).
5. Fetch release APK from GitHub releases page (installs Stable app).

---

## Final Verification Checklist
- [ ] `./gradlew compileDebugSources` succeeds.
- [ ] Debug app shows "Media Nest (Debug)" under icon.
- [ ] Release app shows "Media Nest" under icon.
- [ ] Debug Settings UI displays update warning instead of button.
- [ ] Stable Settings UI displays "Check for Updates" button.

---

## Stop Conditions
- If Gradle sync fails after adding `debug` block.
- If resources conflict due to namespace issues.
