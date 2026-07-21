<div align="center">
  <img src="app/src/main/assets/logo.svg" width="160" alt="Kiyori Logo">
  <h1>Kiyori</h1>
  <p>An Android AI browser and assistant project under the Kiyori brand</p>
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

## Overview

Kiyori is an Android AI browser and assistant project currently derived from Operit. It retains on-device chat, model configuration, tool calling, browser, workflow, memory, terminal, MCP, Skill, ToolPkg, and local-model capabilities while establishing an independent Kiyori product identity and distribution boundary.

Cloud models are selected and configured by the user with their own API key, model, and endpoint. Requests are sent directly from the device to the selected provider. Kiyori does not provide LLM inference or relay chat requests. Local engines such as MNN and llama.cpp can run on the device after their model files are prepared.

## Capabilities

- AI chat, character cards, memory, context, and conversation management
- Built-in browser, web access, and automation tools
- MCP, Skill, ToolPkg, workflows, and tool calling
- Ubuntu terminal, file management, SSH, and development tools
- MNN and llama.cpp local inference plus configurable third-party model providers
- Voice, images, attachments, floating windows, and Android permission integration

Some markets, model providers, GitHub login, search, speech, image generation, and user-configured remote capabilities contact their respective third-party services. They are not Kiyori update or announcement channels.

## Build

### Requirements

- Windows, macOS, or Linux
- JDK 17
- Android SDK with compile SDK 36
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

## Upstream and License

This project is derived from [Operit](https://github.com/AAswordman/Operit). Historical Operit authorship, contributor attribution, and license notices remain intact. The Kiyori name, artwork, repository, and distribution channels are maintained independently by the Kiyori project.

Source code is provided under the repository's [GNU LGPL v3 license](LICENSE). Before redistributing a modified build, also review the licenses, branding rules, and service terms of included dependencies.

## Feedback

Report problems through [Kiyori Issues](https://github.com/Kiyori-CN/Kiyori/issues) with the device model, Android version, reproduction steps, and relevant logs. Do not upload API keys, tokens, cookies, signing materials, or private conversations.
