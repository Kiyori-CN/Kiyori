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

HikerView demonstrates that Range, request headers and HLS identity must remain attached to the original URL. Its local
proxy and cache system is not part of Kiyori because the existing `BrowserDownloadManager` and mpv cache already own
those responsibilities.

## Ownership

`PlayerSession` is a main-process singleton and the only product-state owner. It owns the active media request,
presentation, playback snapshot and Surface lease. It never constructs mpv or opens the media descriptor.

The non-exported `:player` `PlayerRuntimeService` owns one `HandlerThread`, one `MpvPlayerEngine`, one
`PlayerMediaResolver`, one content descriptor and one remote Surface wrapper. `MpvPlayerEngine` is the only source file
allowed to import `is.xyz.mpv.MPVLib` or `MPVNode`. One-way AIDL messages carry runtime generation, command ID and
monotonic event sequence.

`PlayerSettingsStore` owns persisted player preferences. The settings page never writes mpv properties. A live session
observes settings and applies only properties that mpv can consume without creating a new media request.

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

Network URLs are passed to mpv unchanged. The request carries only headers actually observed by the WebSession,
including Origin, User-Agent, Referer, Cookie, Range and Accept when present. Missing fields remain missing.

The `:player` resolver opens one read-only `ParcelFileDescriptor` for `content://`; its lifetime matches the remote
media request. `file://` resolves to its original local path. System `ACTION_VIEW` creates a new request ID; Activity
recreation reuses the existing ID.

MIME is evidence, not a URL resolver. An API request observed with a video MIME does not become a direct media file
without exact URL or DOM evidence. `blob:` and MSE entries remain non-executable clues.

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
observed through mpv `eof-reached` and accepted only when the finite position
is within the end tolerance. `MPV_EVENT_END_FILE` is not treated as natural completion because replacing or stopping a
file emits the same event.

`shouldInterceptRequest` runs on Chromium worker threads. Media observation therefore reads the volatile
`WebSession.appliedUserAgent` snapshot plus `WebResourceRequest` and profile Cookie state; it never calls
`WebView.getSettings()` or another WebView method from that callback.
