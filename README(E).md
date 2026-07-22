<div align="center">
  <img src="app/src/main/assets/logo.svg" width="160" alt="Kiyori Logo">
  <h1>Kiyori</h1>
  <p>An all-purpose Android browser powered by Operit AI</p>
  <p>
    <a href="README.md">简体中文</a> |
    <a href="https://github.com/Kiyori-CN/Kiyori">Repository</a> |
    <a href="https://github.com/Kiyori-CN/Kiyori/issues">Issues</a>
  </p>
  <p>
    <img src="https://img.shields.io/github/last-commit/Kiyori-CN/Kiyori" alt="Last Commit">
    <img src="https://img.shields.io/github/license/Kiyori-CN/Kiyori" alt="License">
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0+">
  </p>
</div>

## Project Status

The current version is the `0.1.0` development baseline with Android application ID `com.kiyori`. There is no public official release channel yet. Build development APKs from this repository and do not treat third-party APKs as official Kiyori releases.

Kiyori does not connect to Operit application-update, patch, or remote-announcement services. It currently provides neither automatic nor manual in-app update checks. Project status, source code, and future distribution information are published through the [Kiyori repository](https://github.com/Kiyori-CN/Kiyori).

The [formal development readiness](docs/TODO/formal_development_readiness/index.md) checklist defines the main-only branch policy, compatibility boundaries, reproducible clone checks, CI gates, and device acceptance queue. Continuous development uses `main`; the `terminal` submodule is pinned to a KiyoriTerminalCore commit.

## Overview

Kiyori is an all-purpose Android browser powered by Operit AI and currently derived from Operit. The browser is the product center; Operit AI is an embedded subsystem for conversation, reasoning, and automation. Web browsing, video, music, reading, downloads, file management, and ad blocking will become dedicated Kiyori pages exposed to AI through controlled capability contracts.

Kiyori is an independent product, not an Operit brand replacement. It retains existing on-device chat, model configuration, tool calling, workflow, memory, terminal, MCP, Skill, ToolPkg, and local-model capabilities while Kiyori owns the application shell, global navigation, system settings, distribution, and future browser domains.

Cloud models are selected and configured by the user with their own API key, model, and endpoint. Requests are sent directly from the device to the selected provider. Kiyori does not provide LLM inference or relay chat requests. Local engines such as MNN and llama.cpp can run on the device after their model files are prepared.

## Target Product Structure

The following structure is the accepted development target. It is still being planned and migrated and does not claim that every page is implemented:

```text
Kiyori App Shell
├── Software Home
│   └── Minus-One Page <- Software Home -> AI Home
├── Browser Home
├── Mini App Home
├── File Management Home
└── Settings Home
```

The app launches into Software Home. Minus-One Page, Software Home, and AI Home share one pager state, release velocity, and snap physics; movement follows the pointer and an unfinished fling can be interrupted by an immediate reverse drag. Its central search card keeps separate Search and AI buttons: Search opens a full-screen page dedicated to web search, while AI moves to AI Home. History, bookmarks, files, and mini apps provide search within their own pages. The five-item bottom navigation appears only on the five root pages. Minus-One Page and the full-screen AI Home hide it. AI Home always shows the conversation surface and input controls even before a model or API Key is configured; model setup lives on its own settings page and missing credentials do not block entering AI Home. AI Home and every AI top-level root show the hamburger button, including roots opened by shortcuts or external routes; deep pages show Back. AI Settings is also reachable from Settings Home as the same page, but crossing between Settings Home and the AI drawer opens the AI Settings root so it cannot inherit the other source's deep Back path.

Software Home, Minus-One Page, all five bottom destinations, and AI pages share one edge-to-edge status-bar policy. Page backgrounds extend to the physical top edge while interactive content avoids the status bar and cutout. The drawer scrim still covers the full window, but the drawer panel begins below the status bar and does not apply that top inset twice. A visible status bar is always transparent; Appearance keeps Hide status bar but removes the transparent and custom-color controls.

The drawer shows live network status plus Packages, Permission Grant, Workflow, AI Dialogue, Assistant Configuration, Memory, Toolbox, ToolPkg destinations, and AI Settings; a WiFi connection is labeled exactly "WiFi". Native AI roots retain independent state and child stacks, while a ToolPkg page retains state only when its route declares `keepAlive=true`. Conversation history, new-chat, and delete-chat actions remain in the existing AI Home history selector. Terminal remains only in the upper-right AI Home toolbar. The first Minus-One Page follows the legacy Kiyori design and includes Favorites, Bookmarks, History, and Downloads.

The drawer keeps the original high-frequency Permission entry and continues to open `Screen.ShizukuCommands`. Permission Center is entered from Kiyori Settings, while AI Tool Authorization remains owned by `ToolPermissionSystem`; these routes do not copy or merge state. High-impact-operation policy and operation records remain part of the security-contract design. Backups, conversation-data management, token statistics, and the Toolbox utility for changing other applications' permissions stay in their own domains.

The Permission quick card keeps the original Operit short label and visual treatment. Its badge shows only the number of necessary device authorizations missing for enabled capabilities, or Normal when none require attention; AI tool policy is not included in this badge.

High-impact-action confirmation and AI Operation Records share one AI Security group in Permission Center. AI operations use R0 Read-Only, R1 Low-Impact, R2 High-Impact, and R3 Critical levels; allowing a tool never bypasses R2 or R3 operation confirmation. Neither entry is shown until its complete policy or record page is implemented.

New Kiyori pages follow the original Operit visual language, while page structure, browser behavior, and other Kiyori features reference [kiyori-android@24a2dfa9](https://github.com/Kiyori-CN/kiyori-android/tree/24a2dfa91f0a4166dc58e5c4732d11861173f766). Phones, tablets, and foldables share one navigation state. The AI drawer uses `75%`, `320dp`, or `360dp` according to window width and is capped to the physical region left of a separating hinge. See the [Kiyori product shell and navigation architecture](docs/doc-src/architecture/kiyori_product_shell_and_navigation.md).

## Capabilities

- AI chat, character cards, memory, context, and conversation management
- Built-in browser, web access, web search, and automation tools
- Planned dedicated video, music, reading, download, file-management, and ad-blocking pages
- MCP, Skill, ToolPkg, workflows, and tool calling
- Ubuntu terminal, file management, SSH, and development tools
- MNN and llama.cpp local inference plus configurable third-party model providers
- Voice, images, attachments, floating windows, and Android permission integration

Some markets, model providers, GitHub login, search, speech, image generation, and user-configured remote capabilities contact their respective third-party services. They are not Kiyori update or announcement channels.

## Build

### Requirements

- Windows, macOS, or Linux
- JDK 21 for running Gradle (Java/Kotlin bytecode remains targeted to JVM 17)
- Android SDK Platform 36, target SDK 34, Build Tools 35.0.0, NDK 28.2.13676358, and CMake 3.22.1
- Git with submodule support

### Clone

```bash
git clone --recurse-submodules https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
```

Some large model and native-runtime artifacts are not stored directly in Git. If the build reports missing `models`, `subpack`, `jniLibs`, or `libs` content, provide the corresponding local dependencies expected by the project build configuration.

### Build a Debug APK

Windows:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

macOS or Linux:

```bash
./gradlew assembleDebug --console=plain
```

The APK is written to `app/build/outputs/apk/debug/`.

## Compatibility

To avoid breaking existing data and ecosystem integrations, this branding migration does not directly rename these implementation identifiers:

- source namespace `com.ai.assistance.operit`
- the `operit://` OAuth callback and existing Intent actions
- database, preferences, backup, workspace, and file-format identifiers
- plugin, ToolPkg, MCP, and selected legacy file-path identifiers

These identifiers do not define the current product brand. Any protocol-level rename requires a separate compatibility migration design.

Because `com.kiyori` is a new application identity, an existing `com.ai.assistance.operit` installation cannot be upgraded in place. Export a backup from the old app and import it into Kiyori; the `Download/Operit` paths and inherited backup formats remain supported for compatibility.

## Upstream and License

This project is derived from [Operit](https://github.com/AAswordman/Operit). Historical Operit authorship, contributor attribution, and license notices remain intact. The Kiyori name, artwork, repository, and distribution channels are maintained independently by the Kiyori project.

Source code is provided under the repository's [GNU LGPL v3 license](LICENSE). Before redistributing a modified build, also review the licenses, branding rules, and service terms of included dependencies.

## Feedback

Report problems through [Kiyori Issues](https://github.com/Kiyori-CN/Kiyori/issues) with the device model, Android version, reproduction steps, and relevant logs. Do not upload API keys, tokens, cookies, signing materials, or private conversations.
