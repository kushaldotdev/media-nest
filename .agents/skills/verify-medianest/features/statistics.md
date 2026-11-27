# Statistics

`App Statistics` is a read-only report over the local database: storage footprint, library ratios,
watch metrics, download health, link-extraction and folder counts, and ranked breakdowns of the
most-played content by resolution, channel and folder.

## Sub-features

- `stat-overview` — `Overview & Storage` hero: `Total Disk Footprint` plus the `Video` / `Audio` /
  `On Disk` meters.
- `stat-library` — `Library Overview`: `Tracked Media`, `Library Ratio`, `Completion`,
  `Total Plays`, `Favorites`, `Subscriptions`.
- `stat-watch` — `Watch Metrics`: `Total Watch Time`, `Watch This Week`, `Average Session`,
  `Longest Session`.
- `stat-storage` — `Storage Details`: `Total Downloads`, `Average File Size`, `Video Storage`,
  `Audio Storage`, `Audio Extractions`, `Completed on disk`, `Total download footprint`.
- `stat-health` — `Download Health`: `Success Rate`, `Completed`, `Active / Queued`,
  `Failed / Canceled`.
- `stat-links` — `Link Extraction & Folders`: `Extracted Links`, `Single Videos`, `Playlist Links`,
  `Channel Links`, `Total Folders`.
- `stat-top` — `Top Content`: most-played videos with `N plays`, paginated by
  `Show more top content (N remaining)`.
- `stat-breakdowns` — `By Resolution`, `By Channel`, `By Folder`, each conditional and each
  paginated (`Show more channels …` / `Show more folders …`).
- `stat-cross-check` — every number agrees with the rows in `media_nest.db`.

## How to get to it (user POV)

- `Collections` (bottom nav) → the `Statistics` icon in the top bar (a chart glyph; its
  `content-desc` is `Statistics`).
- `Settings` → `Data Management & Storage` → the `App Statistics` card
  (`Storage usage meters, watch metrics & library breakdown`).
- Both land on the same screen: title `App Statistics`, subtitle `Library & storage insights`, back
  arrow top-left.

## Driving it with ad.sh

Preconditions:

- `scripts/nest.sh up` ran; `scripts/ad.sh launch` succeeded.
- `.agents/skills/verify-medianest/scripts/nest-state.sh doctor` passes.
- Know the library size. An empty install shows `0` almost everywhere and the three breakdown
  sections are **absent entirely** (they are `if (…isNotEmpty())`), which is a correct render, not a
  missing feature.

```bash
S=.agents/skills/verify-medianest/scripts/nest-state.sh
ART=$($S evidence-dir statistics)
```

- **Entry A — Collections.** `scripts/ad.sh launch`, tap `Collections`, run
  `scripts/ad.sh snapshot -i`, and click the `Statistics` icon's `@eN` ref
  (`scripts/ad.sh click @eN --settle`). Note: `LibraryScreen` passes `contentDescription = "Statistics"`
  to both `MediaNestIconButton` (`onClickLabel` unless explicitly exposed as `contentDescription`) and
  the inner `Icon`, so taking the `@eN` ref from `snapshot -i` works reliably regardless of whether the
  outer `[button]` or inner `[group]` carries `"Statistics"`. Assert `App Statistics` within 10 s.
- **Entry B — Settings.** `scripts/ad.sh launch`, tap `Settings`, then
  `scripts/ad.sh scroll down --until 'label="App Statistics"'`, `click` it, and assert the same
  title. Both paths must be driven; proving one is incomplete.
- **Overview.** Assert `Overview & Storage`, `Total Disk Footprint`, and the three meter labels
  `Video`, `Audio`, `On Disk`.
- **Library Overview.** Assert `Library Overview`, `Tracked Media`, `Library Ratio`, `Completion`,
  `Total Plays`, `Favorites`, `Subscriptions`. Its badge reads `N tracked items`.
- **Watch Metrics.** `scroll down --until 'label="Watch Metrics"'` then assert `Total Watch Time`,
  `Watch This Week`, `Average Session`, `Longest Session`.
- **Storage Details.** Assert `Storage Details`, `Total Downloads`, `Average File Size`,
  `Audio Extractions`, `Completed on disk`, `Total download footprint`.
- **Download Health.** Assert `Download Health` and its badge `N% success rate` plus the four cards
  `Success Rate`, `Completed`, `Active / Queued`, `Failed / Canceled`.
- **Link Extraction & Folders.** Assert the header and `Extracted Links`, `Single Videos`,
  `Playlist Links`, `Channel Links`, `Total Folders`.
- **Top Content.** Assert `Top Content` with badge `N most played`, or the empty card
  `No playback history yet`. With more than 5 entries a `Show more top content (N remaining)`
  button appears; one tap raises the limit by 5, so the visible count grows by `min(5, remaining)` —
  with 7 entries one tap shows 7 and the button disappears (it is rendered only while
  `size > limit`).
- **Breakdowns.** `By Resolution` (badge `N qualities`), `By Channel` (`N channels`), `By Folder`
  (`N folders`) render only when their map is non-empty. Each has its own `Show more …` pagination:
  +5 for top content, +10 for channels and folders, again only while more rows remain.
- **Cross-check the numbers against the DB.** This is the whole point — a chart that renders is not
  a correct chart:

  ```bash
  $S db-count videos                                     # = Tracked Media / "N tracked items"
  $S db-count videos "favorite=1"                        # = Favorites
  $S db-count videos "watchCount>0"                      # = "N of N watched"
  $S db-query "select coalesce(sum(watchCount),0) from videos"        # = Total Plays
  $S db-count downloads "status='COMPLETED'"             # = Completed Downloads
  $S db-count downloads "status in ('DOWNLOADING','QUEUED','PAUSED')" # = Active / Queued
  $S db-count downloads "status in ('FAILED','CANCELED')"             # = Failed / Canceled
  $S db-count subscriptions                              # = Subscriptions
  $S db-count folders                                    # = Total Folders
  $S db-query "select count(*) from link_history"        # = Extracted Links
  $S db-count playback_history                           # one row per video with history
  ```

  `status` stores the **enum name** (`DownloadStatus.COMPLETED`), `videos.favorite` is a 0/1
  integer, and `Total Watch Time` is read from `playback_history.totalWatchTimeMillis` (with a
  duration-based fallback when history is empty), so a mismatch there is expected on a library whose
  history was cleared — check `db-count playback_history` before calling it a bug.
- **Ratios are integer division.** `Library Ratio` is `videoRatioPct / audioRatioPct`, computed as
  `(count * 100) / total` with `Int` math, so the two can sum to 99 or 101 on a small library. The
  same applies to `Completion` and `Success Rate`; do not assert an exact 100 unless the counts
  divide cleanly.
- **`Success Rate` is not `completed / total`.** With no finished downloads it falls back to
  `completed / totalDownloads`, and with no downloads at all it is a hard-coded **100** — an empty
  install legitimately shows `100% success rate`.
- **Read-only.** Nothing here writes. If a run needs different numbers, seed them with
  `$S db-push-sql "…"` (which force-stops the app — `scripts/ad.sh launch` afterwards) and remove the
  seed at the end.
- **Crash gate and evidence.** `$S crash-check`; screenshot per section, `snapshot -i` for the
  breakdown badges, and `$S db-pull "$ART/db"` so the numbers stay auditable next to the screens.

## Gotchas

- **The `Statistics` top-bar icon's accessible tree depends on `MediaNestIconButton`.**
  `LibraryScreen` passes `contentDescription = "Statistics"` to both `MediaNestIconButton`
  (`onClickLabel` unless exposed as `contentDescription`) and the inner `Icon`. Take the `@eN` ref
  from `snapshot -i` rather than relying on `'role=button label="Statistics"'` (which returns zero
  nodes when `MediaNestIconButton` only sets `onClickLabel`) or bare `label="Statistics"` (which
  returns two candidates if both button and icon expose `contentDescription`).
- **`Statistics` is not a bottom-nav destination.** Four destinations only: `Home`, `Collections`,
  `Downloads`, `Settings`. Statistics is a top-bar action plus a Settings card.
- **Three sections are conditional.** `By Resolution`, `By Channel` and `By Folder` are wrapped in
  `if (resolutionMap/channelMap/folderMap.isNotEmpty())`; on a fresh install they do not exist.
  Reporting them as broken turns a correct empty library red.
- **`By Resolution` has a fallback that is not a download count.** With no `downloads` rows but a
  non-empty library, the map is filled with `Video Streams` / `Audio Streams` counted from `videos`
  instead of from qualities. So on a library that has videos but no downloads, the section is
  present with those two labels — correct, not a missing-quality bug.
- **`By Folder` says `0 items` for an empty folder.** Rows come from the `video_folder_join` map, so
  a folder with no videos still appears once any folder exists, showing `0 items`. A zero row is a
  real render; do not report it as a counting bug.
- **`Video Storage` / `Audio Storage` appear twice.** Each is a `StatCard` title *and* a
  `StatBarRow` label in the same `Storage Details` section, so a bare `label="Video Storage"`
  selector is ambiguous. Assert on a unique sibling (`Average File Size`, `Completed on disk`) or
  take the ref from `snapshot -i`.
- **Content sits inside a `LazyColumn`.** `snapshot -i` only returns what is composed on screen.
  Scroll with `--until 'label="…"'` (positional amount is a viewport *fraction*: `scroll down 0.5`,
  or `--pixels 400`) before concluding a section is missing.
- **`Total Watch Time` has a fallback.** When `playback_history` is empty the screen synthesises
  `watchCount * duration * 0.7`, so the number can be non-zero with an empty history table. Compare
  against the same rule, not against `sum(totalWatchTimeMillis)` blindly.
- **`Average Session` divides by history rows, not by sessions.** It is
  `totalWatchTimeMillis / history.size` — one row per *video*, not per playback. A video watched ten
  times counts once.
- **`Tracked Media` counts videos, not files.** `videos` rows include entries with no local file, so
  `Tracked Media` can exceed `Completed` downloads. That is intended; do not report it as drift.
- **`Audio` vs `Video` is heuristic.** `audioTrackCount` also counts `.m4a`/`.mp3`/`.aac`/`.opus`
  `localFilePath`s and `mediaType = "AUDIO"`, so a mislabelled row moves itself between the two
  ratios. Recompute with the same rule in SQL or accept the app's definition.
- **Loaded once in `init`, then only on re-entry.** `StatisticsViewModel` calls `loadStatistics()`
  from its `init {}` block, and `refresh()` is only reachable programmatically — there is no pull-to-
  refresh. A DB write made while the screen is open (e.g. `db-push-sql`, which force-stops anyway)
  needs a re-entry — back out and open it again — before the numbers are trustworthy.
