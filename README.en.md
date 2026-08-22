<div align="center">
  <img src="app/src/main/assets/logo.svg" width="152" alt="Kiyori Logo">
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
    <img src="https://img.shields.io/badge/status-development-orange" alt="Development status">
  </p>
</div>

> [!IMPORTANT]
> Kiyori is under active development and does not yet have a public release channel. Build development APKs from this repository; third-party APKs are not official Kiyori releases.

## Overview

Kiyori is an Android application centered on a full browser experience, with Operit AI embedded as its intelligent subsystem. Browser Home and AI browser tools share the same Browser Runtime, WebSession, and real WebView, so tabs, page state, cookies, history, downloads, and automation are not split across competing implementations.

The project retains Operit's chat, model configuration, tool calling, workflows, memory, terminal, MCP, Skill, ToolPkg, and local-model capabilities. Kiyori owns the application shell, global navigation, browser experience, product identity, system settings, and future distribution.

| Item | Current state |
| --- | --- |
| Development version | `0.1.0` (`versionCode 45`) |
| Android application ID | `com.kiyori` |
| Minimum Android version | Android 8.0 (`minSdk 26`) |
| Current APK ABI | `arm64-v8a` |
| Continuous-development branch | `main` |
| Public release | Not available yet |
| License | GNU GPL v3 or later |

## Main capabilities

- **Shared browser runtime** for Browser Home and AI tools, including tabs, WebView state, cookies, history, bookmarks, downloads, userscripts, and window state.
- **Operit AI subsystem** with provider configuration, conversations, character cards, memory, tools, workflows, attachments, voice, and resumable execution.
- **Extension ecosystem** for script packages, ToolPkg plugins, Skills, MCP servers, markets, environment variables, and permissions.
- **Local workspace** with file management, an Ubuntu terminal, SSH, development tools, workspaces, and automation. File Management Home → Phone Storage and Settings Home → File Manager open the same file-manager owner.
- **Legal documents** under Settings Home → More Features → User Agreement and Privacy Policy, exposing the current version and both read-only documents without changing first-run consent state.
- **Media stack** with a current-page resource directory classified by known extensions and preferred request media ranges, SVG-capable image thumbnails and full-screen viewing, browser media discovery, downloads, an mpv-based player, queues, subtitles, Anime4K, and floating/full-screen presentation.
- **Local inference** through MNN, llama.cpp, and related runtimes after the required model files are prepared.
- **Android integration** for assistant entry points, accessibility, overlays, notifications, file providers, Shizuku, and system permissions.

Some pages, physical-device interactions, and release workflows remain under verification. [`CONTEXT.md`](CONTEXT.md) is the source of truth for implementation ownership and compatibility contracts. [`docs/TODO/`](docs/TODO/README.md) records active work and device-acceptance status.

### Application-wide network proxy

Open **Settings Home → More Features → Network Proxy**. This is the single routing owner for the Kiyori
process and covers AI services and speech, AI tools, Browser, Downloads, Player, traditional Scripts, and
Kiyori-owned online services. The default is Direct or Proxy; every module can inherit the default or choose
its own mode. Traditional `JsEngine` packages may add a per-package override in Script Rules. The Environment
drawer links here directly, and ToolPkg is intentionally kept under AI Tools rather than treated as a script.

The Current Route section expands the active subscription's `KIYORI_APP_PROXY` root selection through nested
groups to the terminal node. An unresolved automatic group or cycle is shown as unresolved rather than being
presented as a node. Script Rules lists enabled executable traditional `JsEngine` packages and keeps an explicit
manual package-name entry; an empty list means there is no enabled traditional script, not that AI Tools are missing.

The subscription section accepts a Clash/Mihomo subscription URL or an explicitly selected single-document
UTF-8 YAML file. Requests use the `Clash.Meta` identity so services that negotiate Base64 and YAML return a
Clash YAML mapping. Base64 URI lists, duplicate keys, invalid providers, configurations without usable
outbounds, or Mihomo validation failures are rejected. Local, loopback, link-local, private, and multicast
nodes are isolated and counted while valid proxy groups and their order are retained. Subscription URL,
sanitized YAML, and controller secrets are encrypted with Android Keystore in no-backup storage.

Embedded Mihomo listens only on a random loopback mixed-port; it does not enable TUN, LAN listeners, or a
subscription-provided controller. When an external Clash runs as a system VPN/TUN, no host, port, username,
or password is required. Direct bypasses Kiyori's application proxy but can still be captured by Android's
VPN. External VPN coexistence is denied by default and must be explicitly enabled; authorized proxy traffic
then follows `Kiyori Mihomo → system VPN → node`. The setting affects only Kiyori's own process.

## Product structure

```text
Kiyori App Shell
├── Software Home
│   └── Minus-One Page <- Software Home -> AI Home
├── Browser Home
├── Mini App Home
├── File Management Home
└── Settings Home
```

The browser is the product center; Operit AI is the embedded intelligent subsystem. Kiyori capabilities may expose both a human-facing page and a controlled AI capability contract. AI integrations do not directly drive Activities, Composables, or ViewModels, and they do not create a second browser, player, download manager, or settings owner.

## Privacy and network boundaries

- Users select and configure their own cloud model provider, API key, model, and endpoint. Chat requests go directly from the device to that provider.
- Kiyori does not relay LLM chat requests and does not connect to Operit application-update, patch, or remote-announcement services.
- Markets, model providers, GitHub sign-in, web search, speech, image generation, and other user-configured remote capabilities contact their respective third-party services.
- New public data uses Kiyori-owned paths such as `Download/Kiyori` and `Pictures/Kiyori`. The application does not automatically scan, merge, or delete `Download/Operit`.
- Never commit or publish API keys, tokens, cookies, signing material, private logs, or private conversations.

## Clone

```bash
git clone https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git switch main
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

Do not clone every submodule recursively. `terminal` is the normal build dependency and is pinned to a
KiyoriTerminalCore commit. `tools/hotbuild/OperitNightlyRelease` is an independent optional private
submodule and is not part of the normal Debug build path.

## Development environment

The complete Android build baseline uses:

- JDK 21, with Java/Kotlin bytecode targeting JVM 17
- Android SDK Platform 37, target SDK 34, and Build Tools 36.0.0
- Android NDK 28.2.13676358 and CMake 3.22.1
- Rust 1.88.0 with the `aarch64-linux-android` target
- Node.js 22 and npm
- Python through the project `.venv`

The repository does not store every large model or local runtime artifact. Paths such as `app/libs/`, `app/src/main/assets/models/`, `app/src/main/assets/subpack/`, and `app/src/main/jniLibs/` may contain machine-prepared build inputs and must not be treated as disposable caches. See the [build guide](docs/doc-src/dev-core/BUILDING.md) for dependency preparation and troubleshooting.

## Build a Debug APK

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

On macOS or Linux:

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

The output is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The current Debug product contract packages only `arm64-v8a`. Release builds, AABs, signing, store publication, and physical-device acceptance have separate authorization and verification requirements.

## Documentation

| Document | Purpose |
| --- | --- |
| [`CONTEXT.md`](CONTEXT.md) | Product language, ownership, states, protocols, compatibility, and invariants |
| [`docs/README.md`](docs/README.md) | Documentation hub |
| [Build guide](docs/doc-src/dev-core/BUILDING.md) | Environment, dependencies, builds, and troubleshooting |
| [Contributing guide](docs/doc-src/dev-core/CONTRIBUTING.md) | Development, change, and validation workflow |
| [Repository layout](docs/doc-src/dev-core/REPOSITORY_LAYOUT.md) | Modules, generated directories, and local-input boundaries |
| [Formal development readiness](docs/TODO/formal_development_readiness/index.md) | Branch, reproducibility, CI, security, and device gates |
| [`ci/README.md`](ci/README.md) | Local and CI validation contracts |

## Compatibility and migration

Kiyori uses the new Android application ID `com.kiyori`, so it cannot update an existing `com.ai.assistance.operit` installation in place. Export a backup from the old application and import it through an explicit Kiyori migration path.

The following identifiers remain stable for data and ecosystem compatibility:

- source namespace `com.ai.assistance.operit`
- the `operit://` OAuth callback and existing Intent, AIDL, JNI, and IPC identifiers
- database, preference, backup, workspace, and file-format identifiers
- plugin, ToolPkg, MCP, and market protocol identifiers

Any protocol-level rename requires a dedicated versioning, data-migration, and rollback design.

## Contributing and feedback

Read the [contributing guide](docs/doc-src/dev-core/CONTRIBUTING.md), [`AGENTS.md`](AGENTS.md), and the relevant task documents before making changes. Report issues with the device model, Android version, reproduction steps, and only the minimum redacted logs needed to diagnose the problem:

- [Issue tracker](https://github.com/Kiyori-CN/Kiyori/issues)
- [Issue templates](https://github.com/Kiyori-CN/Kiyori/issues/new/choose)

## Upstream and license

Kiyori is derived from [Operit](https://github.com/AAswordman/Operit). Historical Operit authorship, contributors, source attribution, and license notices remain intact. The Kiyori name, artwork, repository, and distribution channels are maintained independently by the Kiyori project.

This repository is provided under the [GNU General Public License version 3 or later](LICENSE). Before redistributing a modified build, also review [`NOTICE`](NOTICE), bundled third-party licenses, corresponding-source obligations, branding requirements, and applicable service terms.
