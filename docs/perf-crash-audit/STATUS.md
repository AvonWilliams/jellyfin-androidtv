# Perf + Crash Audit — Running State

> Append-only working record for this audit. Do not delete lines; correct with a dated note.
> Paths below are relative to the repo root unless noted.

## Goal

Answer honestly: **why is Jellyfin Android TV a laggy mess that often crashes on low-powered
devices, and what should we do about it — including a possible major overhaul / full rebuild.**

Scope: read-only analysis. No code changes.

## User observations (2026-09-25) — anecdotal, NOT verified

From user memory. Treat as loose pointers to steer where we look, **not** as established
facts or ground truth. Every claim still needs code evidence before it counts.

- User recalls the most noticeable **lag while scrolling items** (browse/grid/home rows).
  Unverified; worth checking the scroll/bind path.
- User recalls **crashes right after a click** — opening a library, or starting a video.
  Unverified; worth checking the click → navigation/load path and the click → player-init path.
- User says **screensaver (DreamService/LibraryDreamService) is irrelevant** unless it itself
  crashes; only foreground matters. Deprioritize background/DreamService findings, but don't
  rule them out without checking.

Symbols worth chasing (hints, not findings): `MainActivity` nav, `DestinationFragmentView`,
`BrowseFolderFragment` / `GenericFolderFragment` open, `PlaybackHelper.getItemsToPlay`,
`VideoManager.start`, `PlaybackController`, `ExternalPlayerActivity.playItem`,
`VideoPlayerFragment`, `EnhancedBrowseFragment`, `BrowseGridFragmentHelper`, card bind path
(`BaseItemDtoBaseRowItem`, `ImageHelper`, `JellyfinImage`).

## Method

Agent team via the `Workflow` tool (ultracode session). Pipeline:

1. **Find** — 9 parallel dimension agents (main-thread, images, compose, lists, leaks, data,
   startup, playback, crash).
2. **Verify** — 2 adversarial skeptics per finding (correctness + low-end-impact lens).
   `confirmed` = both agreed · `disputed` = one agreed · `rejected` = zero.
3. **Critic** — completeness critic found gaps the finders missed.
4. **Design** — 3 approach drafts (targeted / partial overhaul / full rebuild) + 3 judges.
5. **Synthesize** — lead-engineer report.

## Recovery / resume paths

- Workflow task ID: `w0x2tbtee` · run ID: `wf_91e1284e-73d`
- Script: `…/workflows/scripts/jellyfin-androidtv-perf-crash-audit-wf_91e1284e-73d.js`
- Transcript (journal + per-agent jsonl): `…/subagents/workflows/wf_91e1284e-73d`
- Resume: `Workflow({scriptPath: "<script>", resumeFromRunId: "wf_91e1284e-73d"})`

## Prior art

- `docs/OPTIMIZATION_FINDINGS.md` — append-only ledger, prior audit 2026-09-22 (six agents + adversarial pass).
- `docs/OPTIMIZATION_PLAN.md` — verdicts/conclusions of that audit.
- `docs/HANDOFF.md`, `docs/SERVER_THEMING.md`.

## Status

- **2026-09-25** — COMPLETE. 147 agents, ~7.2M subagent tokens, ~63 min. Result below.
  Caveat: one correctness-verify subagent (`verify:correctness:45`) had the safety classifier
  time out; treat that one finding's confirmation with slightly lower confidence.

---

## Verdict

**TARGETED (Approach A), not a rebuild.** Dominant causes are two small, low-risk, in-place
defects, not a diffuse mess:

1. **Images decoded at full server resolution** — backdrops, launcher tiles, trickplay sprite
   sheets all decode/retain full-size bitmaps (up to ~33 MB each) with no size hint anywhere.
   Collapses to ONE missing choke point on the shared Coil `ImageLoader`.
2. **Device profile over-reports decode capability** — `MediaCodecQuery.kt:19` has a pre-Q
   software-codec filter that is a silent no-op, so weak SoCs are told to direct-play 4K/Hi10
   they can't hardware-decode, then ExoPlayer silently falls back to ffmpeg software decode
   (100% CPU, frame drops, ANR/crash).

Judge scores (two panels, 3 judges each): **A = 8–9/10**, B = 6–7/10, **C (rebuild) = 3/10**.
Full rebuild forks permanently from upstream, costs 16–22+ weeks, and has no regression harness.

---

## Confirmed root causes (11)

1. **[critical] Software codecs folded into device profile** — weak SoC direct-plays 4K/Hi10, silently falls back to ffmpeg software decode.
   - `MediaCodecQuery.kt:19` defines `isSoftwareCodec = AndroidVersion.isAtLeastQ && isSoftwareOnly`; on pre-Q (Android 8/9 — most low-end TV boxes) software decoders (`OMX.google.`/`c2.android.`) are ALWAYS included in `decoderInfos()` (:24). `getMaxResolution` (:53-67) takes the MAX across hardware AND software decoders → feeds H264/HEVC/AV1/VC1 direct-play conditions (`deviceProfile.kt:130-133`). `softwareCodecsEnabled` defaults true (`UserPreferences.kt:287`). `ExoPlayerBackend.kt:100-107` (`setEnableDecoderFallback(true)` + always-on ffmpeg renderer) silently software-decodes the direct-played stream.
   - Fix: exclude software decoders via pre-Q name heuristic; default `softwareCodecsEnabled=false`; clamp max resolution to display size; disable video decoder-fallback (keep ffmpeg for audio). Force server to transcode down.
   - refs: `app/src/main/java/org/jellyfin/androidtv/util/profile/codec/MediaCodecQuery.kt:18-24,53-67`; `util/profile/deviceProfile.kt:130-133,397-438`; `preference/UserPreferences.kt:287`; `playback/media3/exoplayer/src/main/kotlin/ExoPlayerBackend.kt:100-107`

2. **[high] BackgroundService decodes every backdrop at full original resolution and retains all at once**
   - `BackgroundService.kt:73-74` maps backdrop URLs via `getUrl(api)` with no maxWidth/maxHeight; `:110-115` `ImageRequest` with no `.size()`, then `.toBitmap()` into `_backgrounds` held by a Koin single (`AppModule.kt:172`). 4K backdrop ≈ 33 MB; several held at once. `MainScope()` (:44) never cancelled + 30s slideshow timer (:160-168).
   - Fix: `getUrl(api, maxWidth = m.widthPixels, maxHeight = m.heightPixels)` + `.size()`/`maxBitmapSize` (pattern already at `DreamViewModel.kt:139-140`, `PhotoPlayerContent.kt:37-38`); `take(3)`; cancel scope on stop.
   - refs: `app/src/main/java/org/jellyfin/androidtv/data/service/BackgroundService.kt:44,73-74,110-115,160-168`; `di/AppModule.kt:172`

3. **[high] Launcher channel tiles decoded at original resolution, copied + re-encoded at quality 95**
   - `ImageProvider.kt:38-45` `ImageRequest` no `.size()`, so Coil decodes `Size.ORIGINAL`; source is full-original URL (`LeanbackChannelWorker.kt:270`). `:56-62` `drawable.toBitmap()` + `compress(WEBP_LOSSY, 95)`. 2000×3000 poster peaks >48 MB transient for a ~150 px tile.
   - Fix: `.size(~512px)`/`maxBitmapSize`; bounded `maxWidth/maxHeight` at `LeanbackChannelWorker.kt:270`; drop re-encode quality to ~80.
   - refs: `app/src/main/java/org/jellyfin/androidtv/integration/provider/ImageProvider.kt:38-45,56-62`; `integration/LeanbackChannelWorker.kt:270`

4. **[medium] Trickplay sprite sheets decoded at Size.ORIGINAL with bitmap cap explicitly disabled**
   - `CustomSeekProvider.kt:106-107` `size(Size.ORIGINAL)` + `maxBitmapSize(Undefined)`; whole sheet (e.g. 3840×2160, ~33 MB) decoded, then `SubsetTransformation` (`Bitmap.createBitmap`) copies one tile while full sheet stays in memory cache. Scrubbing accumulates full sheets.
   - Fix: bound `maxBitmapSize` to tile dims (≥ tile size for crispness) or request downscaled sheet.
   - refs: `app/src/main/java/org/jellyfin/androidtv/ui/playback/overlay/CustomSeekProvider.kt:104-131`; `util/coil/SubsetTransformation.kt:15-18`

5. **[high] No architectural guard on the shared image pipeline or HTTP layer** (root enabler of #2/#3/#4)
   - Shared Coil `ImageLoader` (`AppModule.kt:124-137`) has no default `sizeResolver`, no `maxBitmapSize`, no memory-cache policy. `JellyfinImage.getUrl` defaults `maxWidth/maxHeight=null` (`:22-40`). `HttpClientOptions()` (`AppModule.kt:83`) has no OkHttp cache.
   - Fix: screen-bounds default `sizeResolver` + bounded `MemoryCache`; lint/CI ban on size-less `ImageRequest`; OkHttp disk cache.
   - refs: `app/src/main/java/org/jellyfin/androidtv/di/AppModule.kt:83,124-137`; `util/apiclient/JellyfinImage.kt:22-40`

6. **[medium] `runBlocking { peekNext(100) }` on main thread in audio now-playing row adapter**
   - `AudioQueueBaseRowAdapter.kt:39` runs `runBlocking { playbackManager.queue.peekNext(100) }` on main, from `lifecycleScope.launch` (:21-24) and queue flows (:28-30). `peekNext` is suspend, constructs `QueueEntry` objects + emits `StateFlow` synchronously (`QueueService.kt:203-208`).
   - Fix: make `updateAdapter()` suspend, drop `runBlocking` (all call sites already suspend).
   - refs: `app/src/main/java/org/jellyfin/androidtv/ui/playback/AudioQueueBaseRowAdapter.kt:21-39`; `playback/core/src/main/kotlin/queue/QueueService.kt:203-208`

7. **[medium] MediaSessionPlayer.getState does runBlocking + full playlist rebuild on every state-flow emission**
   - `MediaSessionPlayer.kt:40-44` invalidates state on every `queue.entry`/`playState`/`videoSize`/`speed`/`playbackOrder` emission; `getState` (:54-135) `runBlocking` (:91) + `peekPrevious()`/`peekNext()` + `setPlaylist` wholesale rebuild each invalidation.
   - Fix: precompute prev/current/next `MediaItemData` in observers, cache; `getState()` reads cache + diff-setters.
   - refs: `playback/media3/session/src/main/kotlin/MediaSessionPlayer.kt:40-51,54-135`

8. **[medium] rememberPlayerProgress = per-frame Animatable in composition; seekbar recomposes every frame**
   - `playback.kt:76-97` `Animatable` + `animateTo(1f, tween(remaining))` driven by Compose frame clock (~60fps) → `animatable.asState()`. `PlayerSeekbar.kt:24-35` reads it in composition → recomposes seekbar subtree every frame for whole video. A redundant 1s poller (`rememberPlayerPositionInfo`) already exists (`playback.kt:42-52`).
   - Fix: read progress in draw phase (`drawWithContent`, pattern at `NowPlayingView`) or switch to the 1s poller; delete full-duration Animatable.
   - refs: `app/src/main/java/org/jellyfin/androidtv/ui/composable/playback.kt:42-52,76-97`; `ui/player/base/PlayerSeekbar.kt:24-45`

9. **[high] Home screen re-fetches every row on every socket event and on every resume, no cache**
   - `HomeRowsFragment.kt:167-179` debounces UserData/LibraryChanged 1.5s then `refreshRows(force=true)` loops `repeat(adapter.size())` calling `Retrieve()` on every row (:220-230). `onResume` (:200-206) also full refresh. No HTTP/in-memory cache (`AppModule.kt:83`).
   - Fix: min-interval throttle; scope refresh to rows whose triggers match; `force=false` on resume; in-memory item cache gated by `DataRefreshService` timestamps.
   - refs: `app/src/main/java/org/jellyfin/androidtv/ui/home/HomeRowsFragment.kt:167-179,200-206,220-230`; `ui/itemhandling/ItemRowAdapterHelper.kt`

10. **[medium] Process-lifetime scopes and singleton retention never released**
    - `BackgroundService` never-cancelled `MainScope` (`:44`, Koin single); `PlaybackControllerContainer` process-scoped single never cleared (`AppModule.kt:141`, `CustomPlaybackOverlayFragment.java:162`); `ItemRowAdapterHelper` ~20 coroutines on `ProcessLifecycleOwner` capturing Activity; `TvManager` static Live TV/EPG caches.
    - Fix: lifecycle-scope BackgroundService; clear container in `endPlayback()`; fragment-scoped adapter coroutines; bound/release Live TV caches.
    - refs: `app/src/main/java/org/jellyfin/androidtv/data/service/BackgroundService.kt:44`; `di/AppModule.kt:141-142`; `ui/itemhandling/ItemRowAdapterHelper.kt:75,101`

11. **[low] Cold-start and background-work overhead** (one skeptic agreed)
    - ACRA init in `attachBaseContext` on every cold start, `DEV_LOGGING` forced true (`JellyfinApplication.kt:8-11`, `TelemetryService.kt:34-46`); session restore + `getCurrentUser` network round-trip before home (`StartupActivity.kt:111-127`); full playback stack constructed eagerly; WorkManager cancel+re-enqueue per launch (`StartupActivity.kt:148-158`).
    - Fix: gate `DEV_LOGGING` on `BuildConfig.DEBUG`; non-blocking session restore; defer playback-stack to first playback; stop cancel/re-enqueue.
    - refs: `app/src/main/java/org/jellyfin/androidtv/telemetry/TelemetryService.kt:34-46`; `JellyfinApplication.kt:8-11`; `SessionInitializer.kt:20-23`; `ui/startup/StartupActivity.kt:111-127,148-158`

---

## Fixes ranked (11)

1. **Fix device-profile codec bug (ship first, behind a preference flag)** — S-M effort, MEDIUM risk, CRITICAL impact.
2. **Size BackgroundService backdrops to display, cap count, lifecycle-scope** — S, LOW, HIGH.
3. **Size hint + bitmap cap on launcher channel tile provider** — S, LOW, HIGH.
4. **Centralize image policy on shared Coil ImageLoader (architectural guard)** — S-M, LOW-MED, HIGH.
5. **Bound trickplay sprite-sheet decode** — S, LOW, MEDIUM.
6. **Remove main-thread `runBlocking` in AudioQueueBaseRowAdapter** — S, LOW, MEDIUM.
7. **Make MediaSessionPlayer state incremental** — M, LOW, MEDIUM.
8. **Read seekbar progress in draw phase, not composition** — M, LOW, MEDIUM.
9. **Scope + cache home-screen refreshes** — M, LOW-MED, HIGH.
10. **Startup + background-work hygiene** — S-M, LOW, MEDIUM.
11. **Regression guards (StrictMode, lint ban, CI heap sampling)** — S, LOW, MEDIUM.

## Migration plan

**Targeted fixes only — no overhaul/rebuild in this response.**

- Week 1: codec fix (behind flag) + image-size fixes (#2/#3/#5) + shared ImageLoader policy (#4).
- Week 2: `runBlocking` removals (#6/#7), seekbar draw-phase (#8), home-refresh scoping (#9), startup hygiene (#10), regression guards (#11).
- Weeks 3-4: on-device matrix (pre-Q low-end box, Q+ device, Shield-class 4K) across H264/HEVC/AV1/VC1; verify direct-play vs transcode; image quality spot-checks; heap sampling.

**Separate follow-up projects (NOT part of this fix):**
- (a) audit un-covered subsystems: photo slideshow player (`ui/player/photo/`), `util/speech` SpeechRecognizer, baseline profile/ProfileInstaller, full-screen Gaussian blur GPU cost.
- (b) Compose browse migration (Approach B Phase 4, ~6-10 wk) — only for maintainability of the 1,480-LOC `ItemRowAdapter`/Helper pair.
- (c) full rebuild (Approach C, 16-22+ wk) — only if upstream tracking stops being a constraint.

## Low-end reality

Targets are 4× Cortex-A53 ~1.5 GHz (Amlogic S905/S912 class), 1-2 GB RAM, ~128-256 MB app heap (`android:largeHeap`), Android 8/9 (pre-Q — exactly where the codec filter is a no-op), 1080p panels.
- **Can**: hardware-decode H.264 up to 1080p (some HEVC 8-bit); play server-transcoded streams; run Compose if recomposition bounded + images downsampled.
- **Cannot, ever**: software-decode 4K HEVC/AV1/Hi10 in real time (ffmpeg on 4× A53 = few fps); decode/retain repeated 2-4K full bitmaps (~33 MB each vs 128-256 MB heap); sustain 60fps subtree recomposition or per-frame GPU blur.
- Post-fix expectation: server transcodes high-bitrate/4K/Hi10 to 1080p H.264 (correct outcome, modest server cost, not a regression); UI smooth at 1080p. No fix ever makes these boxes play 4K/Hi10 natively.

---

## Critic gaps (6) — subsystems the finders missed or under-scoped

1. **[high] Seekbar-recomposes-every-frame is under-scoped** — also the currently-playing card overlay (`ItemCardBaseItemOverlay.kt:159-164`) and lyrics box read `rememberPlayerProgress` in composition.
2. **[high] Codec-finding root cause deeper** — on pre-Q the software-codec filter is a silent no-op and every capability gate takes MAX across hardware+software.
3. **[medium] Photo slideshow player (`ui/player/photo/`, 7 files) unaudited** — full-screen decode + crossfade double-buffer + unbounded advance loop.
4. **[low] Voice search (`util/speech/`) unaudited** — `SpeechRecognizer.isRecognitionAvailable` + `createSpeechRecognizer` in composition (main-thread binder).
5. **[medium] No baseline profile / ProfileInstaller** — dex/JIT warmup ignored; app is large with R8 minify.
6. **[low] Full-screen Gaussian blur on home background** — constant GPU fill-rate cost (`AppBackground.kt:90-92`).

## Disputed (50) — one skeptic agreed, worth noting

Key themes: main-thread `runBlocking` (MediaContentProvider, RewriteMediaManager, AuthenticationStore); Compose cost (AsyncImage BlurHash decode, `colorScheme(branding)` re-parse, unstable `BaseItemDto`, per-text `CompositingStrategy.Offscreen`, ComposeView per card); list/N+1 (unpaginated rows, 30 concurrent tag queries, duplicate item-detail fetches, full `BaseItemDto` payloads, no `fields` restriction, `DiffUtil` deep-equality on main); lifecycle (process-scope coroutines capturing Activity, `InteractionTrackerViewModel` as `single`); playback (decoder-fallback forcing software decode, libass per-frame OpenGL, forced audio offload, `TransactionTooLargeException` from full `BaseItemDto` in savedInstanceState, `requireNotNull(timer.id)` NPE).

Full list recorded in the journal (`…/subagents/workflows/wf_91e1284e-73d/journal.jsonl`), not duplicated here.

## Rejected (4)

1. `ui/AsyncImageView.kt` builds `ImageRequest` with no size hint (rejected as already covered / mitigated).
2. `di/AppModule.kt` ImageLoader no explicit cache sizing (folded into confirmed #5).
3. `ui/playback/overlay/SkipOverlayView.kt` self-referential `derivedStateOf` → StackOverflow (refuted).
4. Image loads scoped to lifecycle not view attachment (refuted as not a real defect).

## Approaches + judge scores

- **A — Targeted fixes**: ~2 dev-weeks. Judges: overall 9/9/8 (impact 8, risk 3, effort 2-3).
- **B — Partial overhaul** (image pipeline + browse/data-cache rebuild, keep Media3/SDK): 10-15 dev-weeks. Judges: overall 6/7/6 (impact 8-9, risk 5, effort 6-7).
- **C — Full rebuild** (Compose-first, Media3 kept): 16-22 dev-weeks. Judges: overall 3/3/3 (impact 7-8, risk 8-9, effort 9).

---

## Fork housework (2026-09-25)

Executed the verdict's #1 fix + fork cleanup.

**Codec fix** (the critical root cause) — committed on new `perf` branch:
- `MediaCodecQuery.kt`: `isSoftwareCodec` now detects pre-Q software decoders via `OMX.google.*`
  name prefix (was a silent no-op because `isSoftwareOnly` only exists API 29+).
- `UserPreferences.kt`: `softwareCodecsEnabled` default `true` → `false`.
- Verified: `./gradlew :app:testDebugUnitTest --tests "...util.profile.*" --tests "...BrandingCssParserTest"`
  → BUILD SUCCESSFUL (JDK 21 via `JAVA_HOME=.toolchain/jdk21`).

**`perf` branch** = `fork/browse-modes-v20` base + 13 commits:
- 8 perf fixes folded from opt (formatter cache, search fields, home-refresh debounce,
  Retrieve guard, screensaver cap, NowPlaying recompose, runBlocking, LocalContext fix).
- 3 server-branding commits folded from opt (per user: "styling cues" valued).
- 1 codec fix + 1 `.perf` identity ("Jellyfin Perf", `org.jellyfin.androidtv.perf`).

**Parked**: `parked-opt` tag marks Jellyfin Opt build as fully superseded by `perf`.

**Deleted**: `browse-modes-v20-compose` branch — stale checkpoint, zero unique commits
(its tip is an ancestor of `fork/browse-modes-v20`; compose v20 port already merged into the base).

**Fork**: GitHub repo `AvonWilliams/jellyfin-androidtv-perf` (private) created; remote `perf`
added; push of `perf` branch + `parked-opt` tag (in progress / see git status).

Remotes: `origin`=upstream, `fork`=AvonWilliams/jellyfin-androidtv, `opt`=AvonWilliams/jellyfin-androidtv-opt,
`perf`=AvonWilliams/jellyfin-androidtv-perf.
