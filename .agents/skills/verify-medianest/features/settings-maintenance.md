# Settings and maintenance

Settings is the app's configuration and repair surface: it holds the download rules, the
display/playback preferences, backup export & import, library repair, the broken-file scanner, the
update check, and the VPS sync configuration.

## Sub-features

- `set-open` — the `Settings` destination renders all five section headers.
- `set-downloads` — `Download Location` / `Storage Directory` / `Apply Location`, then
  `Default Download Quality` / `Max Concurrent Downloads`.
- `set-prefs` — `Show YouTube Shorts`, `Collections Layout` (`Grid` / `List`),
  `Always Show Full Titles`, `Auto-mark as Watched` switches and their DataStore keys.
- `set-backup` — `Export Backup` (SAF create-document), `Import Backup` (SAF open-document),
  `Auto-backup interval`, `Local Backups Log`.
- `set-repair` — `Repair Library` and its `Repair: …` status banner.
- `set-clean` — `Scan for Broken Files`, then the `View & Clean Broken Files` **button** and the
  `Broken Files Found` dialog it opens.
- `set-updates` — `Check for Updates` reaching a terminal state, and the `BuildConfig.DEBUG` guard.
- `set-sync` — `VPS Server URL` / `API Key` / `Register Device` / `Sync Now` / `Auto-sync interval`
  / `Sync Activity Log`, plus `sync_prefs` persistence.
- `set-persist` — a changed preference survives a relaunch (UI + on-disk bytes).

## How to get to it (user POV)

- Tap `Settings` in the bottom navigation (title `Settings`, subtitle
  `App preferences & data management`).
- Or `scripts/ad.sh jump updates`, which fires
  `com.example.medianest.ACTION_NAVIGATE_UPDATES` and lands on Settings/Updates.
- The screen is one long scrollable column, in this order:
  `Downloads & Network` → `Preferences` → `Data Management & Storage` → `About & Updates` →
  `VPS Sync & Cloud`.
- The bell in the top bar (`content-desc "Notifications"`) opens the notifications screen, not a
  settings sub-page.

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` ran; `scripts/ad.sh launch` succeeded.
- `.agents/skills/verify-medianest/scripts/nest-state.sh doctor` passes, so `run-as` reads work.
- Know the starting value of any switch before flipping it — these recipes restore it.

```bash
S=.agents/skills/verify-medianest/scripts/nest-state.sh
ART=$($S evidence-dir settings)
```

- **Land on the screen.** `scripts/ad.sh launch`, then tap the `Settings` nav ref from
  `snapshot -i` (the nav label and the screen title are the same string), then
  `scripts/ad.sh wait text "Downloads & Network" 10000`.
- **Section presence.** `scripts/ad.sh snapshot -i` after scrolling; assert each header in order:
  `Downloads & Network`, `Preferences`, `Data Management & Storage`, `About & Updates`,
  `VPS Sync & Cloud`. `scripts/ad.sh scroll bottom --settle` reaches the last one; then
  `scripts/ad.sh scroll up --until 'label="Downloads & Network"' --settle` to start over.
- **Download rules.** The quality field is labelled `Quality Preset` and the concurrency field
  `Concurrency Limit` (their *row titles* are `Default Download Quality` and
  `Max Concurrent Downloads` — two different strings, match the row title to scroll, the field to
  interact). Pick a value, then prove it on disk:
  `$S pref-get download_prefs default_resolution` and `$S pref-get download_prefs max_concurrent`.
  Restore the previous values.
- **Switch state, via snapshot ref.** `MediaNestSwitch` surfaces in `agent-device` as an outer
  clickable `[group]` holding `[checked]` / `[unchecked]` with no label, wrapping an inner child
  `[group]` holding `label="<label>"` (`checked=false`). Neither switch-role selectors nor
  `is checked 'role=group label="X"'` nor `is checked @eN` work (`is` requires a selector
  expression, and `group label=` reads the inner child's `checked=false`). Take the outer
  switch's `@eN` ref from `snapshot -i`, click `@eN --settle`, and read the resulting
  `- [checked] / + [unchecked]` diff:

  ```bash
  scripts/ad.sh snapshot -i                  # locate @eN [group] "Always Show Full Titles" [unchecked]
  scripts/ad.sh click @eN --settle           # diff prints - [unchecked] / + [checked]
  scripts/ad.sh snapshot -i                  # confirm updated state if needed
  ```

- **Switch state, on disk.** The tap is not proof. `Always Show Full Titles` is the good probe
  because its default is `false` (`CollectionsPreferences.DEFAULT_FULL_TITLES`), so a flip has one
  direction to go:

  ```bash
  $S pref-bool collections_prefs full_titles      # true|false, decoded from the protobuf
  ```

  Then relaunch (`scripts/ad.sh launch`), go back to Settings, and read the switch again — that is
  the "worked once" catch. **Restore the starting value** afterwards, or the next run flips back
  and passes for the wrong reason.
- **Other preference probes.** `Show YouTube Shorts` → `$S pref-bool subscriptions_prefs show_shorts`;
  `Collections Layout` (`Grid`/`List` chips) → `$S pref-get collections_prefs view_mode`;
  `Auto-mark as Watched` → `$S pref-bool playback_prefs auto_mark_watched`.
- **Export.** Tap `Export Backup`; the dialog `Export Backup Options` appears with the radio rows
  `Metadata Only` and `Full Backup`, then `Export` / `Cancel`. `Export` launches a **system**
  create-document picker pre-named `MediaNest_Metadata_Backup.zip` or `MediaNest_Backup.zip`.
  Either complete it and assert the file exists, or record "picker appeared, not completed". Success
  is reported by an **inline status card inside the Backup & Restore card**, not a snackbar: an
  `Exporting... <n>%` row with a progress bar, then `Export complete` in a bordered success card. Only
  claim it if you saw that card.
- **Import.** Tap `Import Backup`; a system open-document picker (`application/zip`) opens. What
  happens next depends on the archive's contents: a ZIP that **contains `media/` entries** stops at
  an `Import Options` dialog, while a **metadata-only** ZIP skips the dialog and starts restoring
  immediately (`Restore complete`). So a missing `Import Options` is not a broken import — inspect
  what you picked. **Do not complete an import** against data you need: restoring overwrites the
  database.
- **Auto-backup.** `Auto-backup interval` offers `Disabled (Off)`, `Every 6 hours`,
  `Every 12 hours`, `Every 24 hours`, `Every 7 days`; picking a non-zero value surfaces
  `Auto-Backup: Active` and `Next: in <countdown>`. Prove the stored integer with
  `$S pref-get download_prefs auto_backup_interval_hours`. Auto-backup runs through
  `AutoBackupWorker` at that interval — a fresh interval does not fire a backup immediately.
- **Repair Library.** `scripts/ad.sh click 'label="Repair Library"' --settle` and wait for a terminal
  banner: `Repair: <n> files, <n> paths fixed, <n> orphans removed` (success) or `Repair failed: …`,
  again rendered as an inline card in the same section, not a snackbar. It is safe on seeded data
  but it **clears offline flags for files it cannot find on disk**, so assert the banner rather than
  assuming nothing changed.
- **Broken files.** Tap `Scan for Broken Files`; the button reads `Scanning...` while running, then
  either `No broken files found. Storage is clean!` or `Found <n> broken files (<size>)` with a
  `View & Clean Broken Files` **button**. Only when a scan found something does that button appear,
  and only its dialog exposes `Delete All Broken Files` — the dialog's title is
  `Broken Files Found`, **not** the button's label. **Do not press the delete button**; it deletes
  files one-way. Open the dialog, assert `Broken Files Found`, and `Close`.
- **Updates.** `Check for Updates` → `Checking GitHub releases...` → one of
  `New Version Available: v<version>`, `You are on the latest version.`, or an error row. Silence is
  a failure to report, not a pass. On the **debug** build the update block shows
  `Debug build active: Updates cannot be auto-installed. Please update via Android Studio / Gradle.`
  and only a `Dismiss` button: `UpdateManager.downloadAndInstallUpdate` and `installApk` return
  `UpdateState.Error("Updates are disabled in debug builds.")` under `BuildConfig.DEBUG`, and
  `UpdateCheckWorker` skips the release-update notification. The **check itself still runs** in
  debug — only download/install/notification are gated.
- **Update deep link.** `scripts/ad.sh jump updates` and assert the same Settings screen arrives;
  the action does not scroll to the update card by itself.
- **VPS sync config.** Type a server URL into `VPS Server URL` (placeholder
  `https://your-vps-ip:8000`) and a key into `API Key`, then prove persistence:
  `$S pref-dump sync_prefs` shows `server_url`, `api_key`, `device_id`, `last_sync_at`,
  `last_sync_version`, `sync_interval_hours`. Note everything in `sync_prefs` is stored as a
  **string**, so use `pref-get`/`pref-dump`, never `pref-bool`. `API Key` is a password field, so
  its on-screen value is masked — assert the disk value instead.
- **Sync button gating.** `Register Device` is enabled only when the URL is non-blank; `Sync Now`
  needs URL **and** key. With both set but no reachable server, `Sync Now` must end in a
  `SyncState.Error` row — that error is the correct local proof. Timing differs by state:
  `Syncing with VPS...` stays for as long as the request runs, while a `Success` / `Error` row is
  cleared after `delay(3000)` (`SettingsScreen` `LaunchedEffect(syncState)` only schedules the reset
  for those two). So `wait text "..." 4000` for the terminal row — a fixed sleep can miss it, and the
  progress row is not evidence of anything.
- **Sync without a device id errors immediately.** `SyncManager.sync()` short-circuits when the URL,
  the key **or** `deviceId` is blank, returning
  `Sync not configured. Set server URL and API key in Settings.` So a URL+key pair with no
  registration still produces an error, and `Register Device` is what fills `device_id`. Note that
  `Register Device` itself needs a reachable server, so on a server-less instance the honest report
  is: sync error path verified, registration **not driven**.
- **Sync Activity Log.** The card has `Show` / `Hide`; expanded it lists entries, or
  `No sync activity recorded yet.` when empty, plus a `Clear` action once entries exist. This is
  verifiable with no server, unlike a live sync.
- **Cleanup.** Restore every switch and dropdown you changed (read the `pref-*` value before, write
  it back with the UI). Clear any test URL/API key you typed. Leave the SAF pickers closed.
- **Crash gate and evidence.** `$S crash-check` must pass; screenshot + `snapshot -i` per section,
  and `$S db-pull "$ART/db"` if a repair changed rows.

## Gotchas

- **Switches surface as `[group] "<label>" [checked|unchecked]`, never as a switch role.** The
  outer clickable `[group]` holds `[checked|unchecked]` while an inner child `[group]` holds
  `label="<label>"` (`checked=false`), and the visible row title is a separate `Text`. Always take
  the outer switch's `@eN` ref from `snapshot -i`, run `click @eN --settle`, and assert the
  `- [checked] / + [unchecked]` diff (never switch-role selectors, `is checked 'role=group label="X"'`,
  or `is checked @eN`).
- **`pref-bool` exits 3 for a key that was never written.** DataStore only creates
  `files/datastore/<name>.preferences_pb` and its keys on the first `.edit`, so "unset" means
  *default*, not *broken*. Read the default from the Kotlin file (`DEFAULT_FULL_TITLES=false`,
  `DEFAULT_SHOW_SHORTS=false`, `DEFAULT_AUTO_MARK_WATCHED=true`) instead of forcing a write.
- **Backup size is wrong by construction.** `BackupRepository.getBackupSizes()` opens
  `context.getDatabasePath("medianest.db")` but `DatabaseModule` opens `media_nest.db`. The file
  never exists, so `Metadata Only (~…)` always falls back to the hard-coded `100_000L` estimate.
  Read it as a placeholder, not a measurement. The exported ZIP itself is fine — it does not go
  through that path.
- **Export goes through a system picker, import overwrites.** `Export Backup` uses SAF
  `CreateDocument`, so the next screen is system UI, not the app. `Import Backup` replaces the
  whole database; never complete one on an instance whose rows you need.
- **Two destructive buttons in this screen.** `Delete All Broken Files` (inside the broken-files
  dialog) and a completed import are one-way. Opening and asserting the dialog is the correct,
  non-destructive proof — say "dialog verified, action not driven".
- **Auto-backup is not instant.** An interval change schedules `AutoBackupWorker`; it does not run
  on the spot. Prove the interval preference, and only claim a backup if a new
  `backup_metadata_<timestamp>.zip` (or `backup_full_<timestamp>.zip` when media was included)
  appears under the download folder's `backup/` directory. Only the **last three** are kept, so an
  older file disappearing is intended, not data loss.
- **`Sync Now` without a server is an error, not a no-op.** A `Success`/`Error` row is dropped after
  ~3 s (`delay(3000)` then `resetSyncState()`), so read it with
  `scripts/ad.sh wait text "..." 4000` before any screenshot dump eats the window. `Syncing with
  VPS...` is different: it stays until the request returns, so its absence means the tap never
  landed — not that sync finished.
- **Live cross-device sync is out of scope here.** `sync-server/` has no verification recipe yet
  (`verify-sync-server` does not exist). Anything requiring a running server — device registration
  against a real backend, payload upload, cross-device restore — is **not driven** and must be
  reported that way. Only the client UI, the `sync_prefs` bytes, the short-circuited
  `Sync not configured …` error and the `Sync Activity Log` are locally verifiable.
- **Only two navigation deep links, plus a restart one.** `MainActivity` handles
  `ACTION_NAVIGATE_DOWNLOADS` and `ACTION_NAVIGATE_UPDATES` (`scripts/ad.sh jump`); the third action,
  `ACTION_CONFIRM_RESTART`, opens the paused-download restart confirmation and is not a navigation
  target. Notifications, statistics and every settings section below the header are reached by
  scrolling and tapping.
- **Everything lives in one scroll container.** `snapshot -i` returns only what is on screen, so
  sections below the fold look "missing". Use `scripts/ad.sh scroll down --until 'label="VPS Sync & Cloud"'`
  (the positional amount is a viewport *fraction*: `scroll down 0.5`, or `--pixels 400`) instead of
  concluding the row is gone.
- **Release signing.** `signingConfigs.release` reads `$user.home/.android/debug.keystore` — always
  drive the debug build (`com.example.medianest.debug`).
