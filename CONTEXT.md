# Kiyori shared context

This file defines the stable product language and compatibility boundaries for this repository.
Current work status and implementation notes belong in `docs/TODO/`.

## Product identity

- **Kiyori** is the brand.
- **Kiyori** is the Android application and user-visible product name.
- **Kiyori** is the repository and Gradle root-project name.
- The project repository is `https://github.com/Kiyori-CN/Kiyori`.
- The Android application ID is `com.kiyori`.

## Distribution contract

- This private development build does not check the Operit release channel or receive Operit remote announcements.
- User-facing project, help, issue, and release links must use the Kiyori repository unless a feature explicitly documents a third-party service.
- Updates are distributed deliberately from the Kiyori repository. The application does not perform automatic or manual in-app update checks at this stage.

## Compatibility boundary

The following names are implementation or interoperability identifiers, not the Kiyori brand, and are not renamed by a visual-brand migration:

- the source namespace `com.ai.assistance.operit`;
- the `operit://` OAuth callback and other externally consumed URI schemes;
- persisted database, preference, backup, workspace, plugin, ToolPkg, MCP, Intent action, and file-format identifiers;
- attribution, license history, source references, and upstream documentation that must continue to identify Operit accurately.

Changing one of these identifiers requires a separate compatibility design and migration plan.
