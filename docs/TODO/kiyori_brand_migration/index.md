---
fork: https://github.com/Kiyori-CN/Kiyori
upstream: https://github.com/AAswordman/Operit
status: verification_pending
---

# Kiyori brand migration

## Baseline

The repository was copied from Operit at `ef00abc5099187b4665957e9697cb743c81fa154`.
The Android application ID has already been changed to `com.kiyori`, and the Compose dependency upgrade is present as an uncommitted, verified change.
The visible application identity, project links, launcher assets, update client, and announcement client still point to Operit.

## Goal

Ship a development baseline named **Kiyori**, branded as **Kiyori**, using the Kiyori v3 launcher artwork and the Kiyori repository URL.
The build must not contact Operit for application updates or remote announcements.

## Scope

- Set `versionCode` to `45` and `versionName` to `0.1.0` so it can update the already installed `com.kiyori` build with version code `44`.
- Replace user-visible Operit product branding in the application shell, About page, share-image branding, service labels, and supported locale resources.
- Replace project, help, release, and issue links with `https://github.com/Kiyori-CN/Kiyori`.
- Remove startup update checks, About-page update actions, patch/full update download UI, remote announcement polling, and the remote announcement dialog.
- Synchronize the Kiyori v3 launcher, adaptive, round, in-app, and README assets from `D:/10_Project/kiyori-android`.
- Update the Chinese and English README files and root project context.

## Non-goals

- Do not rename the source package namespace or Kotlin/Java class names in this change.
- Do not rewrite persisted storage, backup formats, ToolPkg/plugin protocol names, Intent actions, or `operit://` compatibility URIs.
- Do not remove third-party provider URLs or the package/Skill/MCP market as part of the update and announcement shutdown.
- Do not commit, push, sign, or build a release APK.

## Acceptance

- Source search finds no active Operit update or remote announcement fetch from application startup or About UI.
- Manifest/application resources resolve to Kiyori and the Kiyori launcher icon.
- The Kiyori repository URL is used by About, Help, README, badges, release, and issue links.
- `git diff --check` passes.
- A Debug APK builds successfully and is reported from `app/build/outputs/apk/debug/`.

## Current result

- Static source and documentation audits find no active Operit application-update, patch, remote-announcement, remote denylist, or upstream quick-setup download endpoint in the Android runtime.
- `assembleDebug` completed successfully on 2026-07-22.
- APK metadata reports `com.kiyori`, version code `45`, and version name `0.1.0`.
- Physical Android verification remains pending for launcher, About, share-image, notification, and assistant-selection surfaces.
