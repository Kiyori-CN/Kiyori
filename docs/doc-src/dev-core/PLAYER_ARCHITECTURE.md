# Player architecture

Kiyori owns one player runtime. Fullscreen playback, browser floating playback, external media intents, settings,
browser candidates, network-log actions and downloads are views or commands over existing owners; none of them creates
another browser, player core or download database.

## Source comparison

The fixed references are:

- `kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766`
- `mpv-android-anime4k@32f5f16988c1b2d5979eef692695bdef7232b7eb`
- `hikerView@5de8809049e4710471f9f42642e54550ecf5dbe3`

The legacy Kiyori checkout is the visual reference and demonstrates useful browser candidate presentation, but its
temporary `MpvSeamlessHandoff.Entry` transfers a mutable engine between two owners. Several failure paths reload the
remote media, use a proxy, resolve another URL or call JavaScript pause/resume hooks. Those behaviors are incompatible
with the current session contract.

The Anime4K checkout is the rendering reference. Kiyori adopts verified mpv options, live shader properties, precise
seek and bounded cache settings. It does not adopt disabled TLS verification, a decoder chain that silently tries multiple policies, a
second media library or a second download store.

HikerView demonstrates that observed request headers, byte-range evidence and HLS identity must remain attached to the
original URL. Kiyori keeps that evidence in the browser candidate and download request, but a captured `Range` describes
one concrete browser transfer rather than reusable playback identity. Its local proxy and cache system is not part of
Kiyori because the existing `BrowserDownloadManager` and mpv cache already own those responsibilities.

## Ownership

`PlayerSession` is a main-process singleton and the only product-state owner. It owns the active media request,
presentation, playback snapshot, explicit media queue, chapters, seek-preview state and Surface lease. It never
constructs mpv or opens the media descriptor.

The non-exported `:player` `PlayerRuntimeService` owns one `HandlerThread`, one `MpvPlayerEngine`, one
`PlayerMediaResolver`, one content descriptor and one remote Surface wrapper. A separate single thumbnail executor may
call the packaged binding's `grabThumbnailFast`, but it does not create another playback core. It keeps at most one
active and one newest pending extraction, buckets requests at two positions per second and returns a maximum `320px`
Bitmap through AIDL. `MpvPlayerEngine` is the only source file allowed to import `is.xyz.mpv.MPVLib` or `MPVNode`.
One-way AIDL messages carry runtime generation, command ID and monotonic event sequence.

`PlayerSettingsStore` owns persisted player preferences. The settings page never writes mpv properties. A live session
observes settings and applies only properties that mpv can consume without creating a new media request.

The player settings page is ordered as playback/queue, gestures/progress, picture/Anime4K, audio/subtitles,
save/download, and window/online. Screenshot and video directories are optional SAF document trees. Blank values mean
that the current `BrowserDownloadSettingsStore` destination is resolved when the action runs; this preserves later
changes to the main downloader instead of copying its settings into the player store.

Screenshots are written to the player tree when configured. Otherwise they use the actual main-download destination:
the Android system downloader maps to the public download directory, while the internal downloader maps to its SAF,
public-transfer or application directory policy. Player video downloads stay in the existing `BrowserDownloadManager`.
An independent player tree is frozen into the request and selects the existing internal engine because Android
`DownloadManager` cannot target an arbitrary SAF tree. Direct files are moved to that tree after the staged download.
M3U8 companion packages remain in the application download directory under the existing package contract. Persisted
tree permission release checks the browser setting, both player directory settings and all retained tasks.

Gravity rotation is an Activity presentation preference. Enabling it selects `FULL_SENSOR`; disabling it after an
enabled state restores the default sensor-landscape policy. The manual rotate control is visibly disabled and labelled
`自动` while gravity rotation owns orientation. The landscape Anime4K control keeps a fixed lower-left width and compact
line heights so its mode label cannot drift toward the centered transport row.

Each `WebSession` owns its in-memory media candidates. The browser top bar, candidate drawer, automatic floating
selection and network log only project candidate IDs; request headers and Cookie remain in the runtime owner. Playback
calls `PlayerSession`, and download calls the existing `BrowserDownloadManager`.

## State and transitions

The presentation state is exactly:

- `BROWSER_ONLY`
- `FLOATING_PLAYER`
- `FULLSCREEN_PLAYER`

A new request ID may execute one mpv `loadfile`. Presentation changes only detach the old Surface and attach the new
Surface. Owner-token checks prevent a late destroyed Surface from detaching its successor.

Browser floating to fullscreen to floating to close never calls WebView `loadUrl`, `reload`, page reconstruction,
candidate rescan or JavaScript media control. A page may continue its own media independently; Kiyori does not mutate
that page state.

## Media requests

Network URLs are passed to mpv unchanged. The media request retains only headers actually observed by the WebSession,
including Origin, User-Agent, Referer, Cookie, Range and Accept when present. Missing fields remain missing. Immediately
before writing `http-header-fields`, the `:player` runtime removes `Range` case-insensitively and records that decision
without its value. FFmpeg owns the active byte offset and generates the Range required for each open or seek; replaying
one browser request's captured Range would force every transfer to that stale position. The candidate and download
owner continue retaining the original Range evidence.

The `:player` resolver opens one read-only `ParcelFileDescriptor` for `content://`; its lifetime matches the remote
media request. `file://` resolves to its original local path. System `ACTION_VIEW` creates a new request ID; Activity
recreation reuses the existing ID. External local videos may build a same-directory queue only when normalized series
names match; entries use natural numeric title order. Browser and other network requests remain a one-item queue unless
their caller provides an explicit ordered queue.

MIME is evidence, not a URL resolver. An API request observed with a video MIME does not become a direct media file
without exact URL or DOM evidence. `blob:` and MSE entries remain non-executable clues.

## Diagnostics

`PlayerDebugLogBuffer` is the single fullscreen log-view owner. A new media request clears the previous in-memory
segment, after which the main process records session, command and Surface transitions. `MpvPlayerEngine` registers the
packaged binding's `MPVLib.LogObserver`, sets `msg-level=all=v` before `mpv_initialize`, and sends MPV verbose,
file-event and error messages from `:player` through the existing ordered AIDL callback. Progress snapshots are not
logged every 250 ms.

The buffer keeps at most 2,000 timestamped entries and reports how many older entries were dropped. Each entry receives
a stable sequence ID and one or more topics when it is appended, so filtering does not repeatedly classify the full
buffer. The dialog samples the buffer revision at a bounded cadence and renders structured entries newest-first in a
lazy list. Its single non-wrapping horizontal strip provides all, error, warning-and-error, network/loading, playback,
Surface/render, track/subtitle and runtime/MPV views.

The dialog uses one screen-bounded Surface with fixed header, filter and footer regions around a weighted log viewport.
Close stays in the header; clear, copy and export stay in the footer, and clear requires a second tap. Clipboard copy and
text export build a chronological full report from the current view. Exports are written to
`Download/Kiyori/exports`.

Diagnostics retain the online scheme, host, port and path shape needed to identify stream behavior, while URL query
values, request-header values, Cookie, Authorization, titles and private local paths are removed. The report also
contains the app version, device/Android version, runtime generation/PID, presentation, Surface lease, decoder,
Anime4K, track counts and visible error. Media-load diagnostics record input/forwarded header counts, forwarded field
names and whether an observed Range was left to mpv; values remain omitted. `END_FILE` reads mpv's node schema as the
string `reason` plus optional string `file_error`, so native loading failures remain ERROR entries and become a visible
session error instead of `unknown / none`.

## Native and class loading

The mpv AAR is an explicit Gradle input. Pre-build validation opens its `classes.jar` and verifies the public MPV binding
classes as well as the two arm64 native libraries. Post-build validation scans every DEX for the MPV binding and Kiyori
engine descriptors.

Recoverable command or linkage failures become visible player errors. A fatal native exit is isolated to `:player`;
Binder death moves `PlayerSession` to `DEAD`, writes a bounded redacted report and does not reconnect until the user
explicitly chooses restart. Kiyori does not retry with another engine. The correct Debug artifact must pass the symbol,
`DT_NEEDED`, ABI and 16 KB alignment audit in [Player native stack](PLAYER_NATIVE_STACK.md).

Every JNI entry in `MpvPlayerEngine` converts `LinkageError` into `MpvRuntimeException`; the runtime reports command
failure to `PlayerSessionState.error`. Native signal termination is diagnosed by Binder death and
`ApplicationExitInfo`, while the main process and WebView remain outside the player process. Natural completion is
observed through mpv `eof-reached`. `loop-file` stays disabled so `PlayerSession` can advance the real queue and apply
the configured final-item action. `MPV_EVENT_END_FILE` is not treated as natural completion because replacing or
stopping a file emits the same event.

`shouldInterceptRequest` runs on Chromium worker threads. Media observation therefore reads the volatile
`WebSession.appliedUserAgent` snapshot plus `WebResourceRequest` and profile Cookie state; it never calls
`WebView.getSettings()` or another WebView method from that callback.
