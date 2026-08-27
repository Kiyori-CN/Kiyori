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

The main process `PlayerSession` owns the HTTP(S) transport resolver and loopback-only `PlayerMediaStreamBridge`.
The non-exported `:player` `PlayerRuntimeService` owns one `HandlerThread`, one `MpvPlayerEngine`, one local
`PlayerMediaResolver`, one content descriptor and one remote Surface wrapper. A separate single thumbnail executor may
call the packaged binding's `grabThumbnailFast` directly for non-network media. A network request becomes eligible only
after the same mpv demuxer establishes authoritative `FULL_VIDEO / COMPLETE`; the runtime then uses `dump-cache` to
export a small target-time excerpt to `cacheDir/player-seek-preview` and gives only that private local file to the
thumbnail executor. Ordinary HTTP/HTTPS requests are rejected before extraction and never open an independent
headerless connection. The runtime keeps at most one active and one newest pending extraction, buckets requests at two
positions per second, returns a maximum `320px` Bitmap through AIDL, deletes superseded/active/closed excerpts and
cleans abnormal-exit remnants on the next engine initialization. `MpvPlayerEngine` is the only source file allowed to
import `is.xyz.mpv.MPVLib` or `MPVNode`. One-way AIDL messages carry runtime generation, command ID and monotonic event
sequence. `FILE_LOADED` publishes track/container metadata but does not clear the loading state;
`PLAYBACK_RESTART` is the first runtime event allowed to mark playback output ready.

`PlayerSettingsStore` owns persisted player preferences. Decoder backend owns `hwdec`; rendering profile owns the mpv
profile. Initialization and media load apply the broad profile before explicit backend and seek properties so each
dedicated Kiyori setting remains the final owner of its property. Initialization records those already-applied values,
so the first load does not repeat identical profile, decoder, seek, subtitle, or volume writes. Changing the broad
profile invalidates every explicit-property cache, including shaders, before the live settings command reapplies the
dedicated values. Network cache is one request-scoped four-value policy: `COMPACT`, `BALANCED`, `LARGE`, or
`FULL_VIDEO`; there is no independent full-cache boolean. A live settings update may change rendering, decoder, seek,
subtitles, volume, and shaders, but it cannot change the current request's cache owner. The next media request snapshots
the current policy. The settings page never writes mpv properties.

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

Each `WebSession` owns its current-document static resource directory and in-memory media candidates. The directory
aggregates by document token plus normalized original URL, excludes fragments, preserves queries, increments a request
count, keeps an independent 2,000-identity bound and groups resources by
media/image/document/script/style/data/font/other type instead of displaying a reverse timestamp stream. The browser
top bar, “资源嗅探” drawer, automatic floating selection and network resource directory only project candidate IDs;
request headers and Cookie remain in the runtime owner. Bounded image thumbnails and the single image viewer reuse the
captured request identity. Controlled response MIME and `Content-Disposition` may identify opaque URLs without reading
the response body. Video playback calls `PlayerSession`, media download calls the existing `BrowserDownloadManager`,
and recognized audio remains non-playable until a music-player owner exists.

## State and transitions

The presentation state is exactly:

- `BROWSER_ONLY`
- `FLOATING_PLAYER`
- `FULLSCREEN_PLAYER`

A new request ID may execute one mpv `loadfile`. `FLOATING_PLAYER <-> FULLSCREEN_PLAYER` keeps the same
`PlayerMediaRequest`, playback position, duration, pending seek target, pause/speed state, complete-cache state,
`loadGeneration`, `runtimeGeneration`, runtime PID, mpv core and demuxer cache. Presentation transfer only detaches the
old Surface and attaches the new generation. Owner-token checks prevent a late destroyed Surface from detaching its
successor.

Browser floating to fullscreen to floating to close never calls WebView `loadUrl`, `reload`, page reconstruction,
candidate rescan or JavaScript media control. A page may continue its own media independently; Kiyori does not mutate
that page state. Closing projects `BROWSER_ONLY` immediately so the floating composition disappears before native
detach and runtime close finish. Each `WebSession` owns a current `credentialDocumentToken`, a pending-start token for
app-initiated navigation, and an automatic-floating consumption token beside the candidates rather than in Compose. A
candidate accepted by the single `PlayerSession` records that document token, whether the entry was automatic or
manual. Automatic startup additionally
requires the active document to be fully loaded (`pageLoaded=true`, `isLoading=false`) and filters candidates by the
same document token. Closing, natural completion, or fullscreen `CLOSE` therefore cannot turn a consumed document's
candidate into a new request, second `loadfile`, fresh runtime or fresh cache. Manual playback remains available.
Navigation rotates the document token and clears candidates before the WebView operation. Stale completion callbacks
are ignored until the matching `onPageStarted` consumes the pending-start token, so an old page's player cannot consume
or reopen a candidate discovered by the new page.

The fixed `mpvlibAndroid@168e0a5e` lifecycle remains the native Surface authority: detach sets `vo=null`,
`force-window=no`, and releases the native window; attach restores the configured VO and `force-window=yes`. Kiyori
does not replace that sequence without a reproducible native source/build closure. Fit ownership stays in
`MpvPlayerEngine`: `FIT` resets aspect override and panscan, `CROP` uses panscan, and `STRETCH` sets
`video-aspect-override` to the current accepted Surface width divided by height. A valid resize reapplies stretch, so
landscape, portrait, floating, freeform, foldable, and inset-adjusted layouts follow their real Surface rather than
display metrics.

Fullscreen and floating progress bars keep drag movement in a local draft and submit one seek only on release.
Cancellation submits no seek. `PlayerSession` records and immediately projects the target only after the current
runtime/load accepts the explicit command; ordinary progress snapshots cannot overwrite that projection. mpv events
carry the load command but no seek command ID, so one load keeps at most one runtime seek in flight. Additional rapid
button, gesture, or progress actions update the latest projected target without issuing a second overlapping command.
The matching `MPV_EVENT_SEEK -> MPV_EVENT_PLAYBACK_RESTART` completes the active command; if the latest target differs,
`PlayerSession` then submits that target as the next command. Events caused by the packaged binding's Surface/VO
reconfiguration are logged as internal seeks and do not enter UI seeking or consume a future target. New media, close,
runtime death, command failure and explicit errors clear both pending and visible seek state.

Long-press acceleration feedback is presentation-only. The start message hides after one second while the temporary
speed remains active; release or cancellation restores the exact pre-press speed and shows the restoration message for
one second. Fullscreen and floating preparation indicators also wait 160 ms before appearing, which avoids flashing a
blocking overlay when a fast request reaches first output inside that interval.

## Media requests

Network URLs are passed to mpv unchanged. The media request retains headers observed by the WebSession. Immediately
before writing `http-header-fields`, the `:player` runtime removes `Range`, `Accept-Encoding`, hop-by-hop fields and
Chromium-only `Sec-CH-UA` / `Sec-Fetch-*` metadata case-insensitively. Origin, User-Agent, Referer, Cookie, Accept and
unknown end-to-end authentication fields remain attached when present. FFmpeg owns active byte offsets, content
encoding, connection framing and each seek Range; replaying browser transport metadata would force stale or
WebView-specific behavior. The candidate and download owner continue retaining the original evidence.

The `:player` resolver opens one read-only `ParcelFileDescriptor` for `content://`; its lifetime matches the remote
media request. `file://` resolves to its original local path. System `ACTION_VIEW` creates a new request ID; Activity
recreation reuses the existing ID. External local videos may build a same-directory queue only when normalized series
names match; entries use natural numeric title order. Browser and other network requests remain a one-item queue unless
their caller provides an explicit ordered queue.

MIME and URL suffix are evidence, not absolute identity. A controlled response MIME can identify an opaque direct media
resource, video DOM evidence outranks a misleading audio suffix/MIME, and audio DOM evidence identifies a real audio
candidate. The original URL is never changed. At `FILE_LOADED`, mpv publishes `file-format`, video/audio codecs,
video-track count, active hardware decoder, pixel format, and the selected video track's
`track-list/N/codec-profile`. A stable `VIDEO_RECONFIG` rereads only that light identity, suppresses an identical
snapshot, and sends a dedicated AIDL update without rebuilding the full track list or rerunning full-cache
qualification. Blank, `no`, and `none` `hwdec-current` values all mean that no hardware decoder is active. Actual
demux/track state is authoritative for diagnostics. `blob:`, MSE, WebRTC and DRM entries remain non-executable clues.

## Diagnostics

`PlayerDebugLogBuffer` is the single fullscreen log-view owner. A new media request clears the previous in-memory
segment, after which the main process records session, command and Surface transitions. `MpvPlayerEngine` registers the
packaged binding's `MPVLib.LogObserver`, sets `msg-level=all=warn,ffmpeg=info,demux=info` before `mpv_initialize`, and
sends bounded native file/network/error evidence from `:player` through the existing ordered AIDL callback. Progress
snapshots are not logged every 250 ms.

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
Anime4K, track counts and visible error. The `:player` runtime registers one default-network callback on its existing
serial Handler, emits an initial snapshot, coalesces callback bursts for 250 ms and appends only changed redacted facts.
Every media load records another request-start snapshot; report export records the main-process snapshot at report
time and points to the ordered `PlayerNetwork` entries for player-process history. Each snapshot contains
active/process-bound network presence, separate active/effective VPN/Wi-Fi/cellular/ethernet/Bluetooth transports,
validation, metering, captive portal, background restriction, Private DNS presence, IPv4/IPv6 DNS/default-route counts
and absent/static/PAC proxy type. It never records IPs, DNS names, proxy addresses, interface names or network handles
and never binds around a VPN. If Android reports a process-bound network, that network is the effective owner for
transports, DNS, routes, proxy and metering; active and effective transports remain separate evidence so a VPN or
unexpected binding is visible without claiming causality.
Media-load diagnostics record input/forwarded header counts, forwarded field names and whether an observed Range was
left to mpv; values remain omitted. Concrete native evidence such as DNS failure, TCP refusal/timeout, unreachable
route, TLS certificate failure or HTTP error outranks generic `loading failed`. `END_FILE` reads mpv's node schema as
the string `reason` plus optional string `file_error`.

After `MPVLib.init()` succeeds, `MpvPlayerEngine` strictly queries the required `mpv-version`, `ffmpeg-version`,
`protocol-list`, `demuxer-lavf-list` and `decoder-list` properties exactly once. Hardware-decoder metadata is read from
the `option-info/hwdec` Node map because mpv defines `choices` as optional and the fixed runtime exposes `hwdec` as a
string-list option without a choices list. When choices are exposed, a pure policy projects the fixed MediaCodec
targets as confirmed available or unavailable. Missing option/map/choices metadata projects those targets as unknown
with explicit evidence; malformed map, array or entry nodes additionally produce a warning but do not abort the
otherwise valid mpv core. The stable digest includes this evidence, so unknown introspection is distinct from a
confirmed empty list. The complete native lists are not sent through Binder, and a browser extension or MIME hint
never becomes runtime capability evidence.

Every cache policy uses explicit startup/non-startup semantics: `cache=yes`, `cache-pause-initial=no`,
`cache-pause=yes`, `cache-pause-wait=1.0`, and its fixed forward/backward/time limits. The single setting is:

| Policy | Forward | Backward | Time | Session disk cache |
| --- | ---: | ---: | ---: | --- |
| `COMPACT` / 省流模式 | 64 MiB | 32 MiB | 60 s | no |
| `BALANCED` / 智能均衡 | 128 MiB | 64 MiB | 180 s | no; fresh-install default |
| `LARGE` / 流畅优先 | 256 MiB | 128 MiB | 300 s | no |
| `FULL_VIDEO` / 完整缓存 | 256 MiB | 128 MiB | 300 s initially | prepared before `loadfile` |

`FULL_VIDEO` uses the same mpv request, headers, demuxer, and an app-private immediate-unlink session directory. It
becomes active after `FILE_LOADED` confirms `demuxer-via-network=yes`, at least one actual video track, a non-HLS/DASH
actual format, finite duration no longer than four hours, `seekable=yes`, `partially-seekable=no`, a positive
`file-size` no larger than 20 GiB, and available space of at least
`file-size + max(1 GiB, ceil(file-size * 0.15))`. Qualification does not read `stream-start`, `stream-end`, URL
suffixes, or `demuxer-cache-state`. An ineligible request disables disk cache, deletes only its verified session
directory, records the exact reason, and retains `FULL_VIDEO`'s own 256/128 MiB and 300-second base playback cache
without changing policy.

For an active request, `cache-secs` expands to finite duration plus 60 seconds.
`demuxer-max-bytes` and `demuxer-max-back-bytes` are bounded packet-metadata budgets (`128..256 MiB` by duration) and
never shrink the base values. `demuxer-cache-state` reads are capped at 1 Hz and classified as
`AVAILABLE`, `UNAVAILABLE`, or `MALFORMED`; the latter two are observable but do not fail ordinary playback or form
completion evidence. `file-cache-bytes` may exceed `file-size`, but independently enforces the actual 20 GiB disk-cache
limit. Free-space checks run at most every five seconds, and 512 MiB is a hard stop.
`bof-cached=yes + eof-cached=yes + exactly one seekable range` is the only first completion proof. Once that proof
establishes `COMPLETE`, a transient unavailable, malformed, or incomplete node cannot revoke the same media session's
completion fact. Replacement, close, engine destruction, and the next runtime initialization clean only the canonical
`noBackupFilesDir/player/mpv-session-cache` scope after rejecting symlink or boundary violations. This is not a
download or offline-library path. A completed request may export only a small cached excerpt for seek preview; the
remote URL is never passed to `grabThumbnailFast`, and active, pending, superseded, closed, or next-runtime cleanup is
bounded to the canonical private `cacheDir/player-seek-preview` scope.

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
