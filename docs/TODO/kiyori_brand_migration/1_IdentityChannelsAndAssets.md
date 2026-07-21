# Identity, channels, and assets

## Old state

The app label is `Operit Clone`, the Gradle project is `Operit`, visible resources still say Operit AI, and project links target the upstream repository and website. Startup and About surfaces can fetch upstream releases, while the root app can fetch and display remote Operit announcements.

## Intended state

The app presents Kiyori consistently, exposes only Kiyori project links, uses the Kiyori v3 brand asset, and contains no reachable Operit application-update or remote-announcement receiver.

## Verification

- Inspect the merged resources and manifest through the Debug build.
- Search runtime source for active update and announcement entry points.
- Compare copied icon hashes with the canonical Kiyori source assets.
- Install and visually verify launcher, About, share image, notification, and assistant-selection surfaces on Android hardware.

## Evidence

- The four copied Kiyori v3 source/target asset pairs have matching SHA-256 hashes.
- `git diff --check` passes for the parent repository and `terminal` submodule.
- `assembleDebug --console=plain` succeeds and produces `app/build/outputs/apk/debug/app-debug.apk`.
- `aapt dump badging` reports package `com.kiyori`, version code `45`, and version name `0.1.0`.

[DONE]
