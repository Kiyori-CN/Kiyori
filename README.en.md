<div align="center">
  <img src="app/src/main/assets/logo.svg" width="128" alt="Kiyori logo">
  <h1>Kiyori</h1>
  <p><strong>Browse the web. Understand content. Put ideas to work.</strong></p>
  <p>An Android browser with embedded Operit AI · Web · AI · Media · Workspace</p>
  <p><a href="README.md">简体中文</a> · <a href="docs/user-guide/README.md">User guides</a> · <a href="docs/README.md">Documentation</a> · <a href="https://github.com/Kiyori-CN/Kiyori/issues">Issues</a></p>
</div>

> [!IMPORTANT]
> Kiyori is under development and has no public release channel. Build a development APK from this repository. Third-party APKs are not official releases.

## What is Kiyori?

Kiyori brings web browsing, AI conversation, media handling and a local workspace into one Android application. Human browsing and AI browser tools share the same page sessions. Kiyori owns the product shell, browser experience and application settings; embedded Operit AI provides conversations, models, tools and automation.

## Capabilities

| Area | Available capabilities |
| --- | --- |
| Browsing | Windows, bookmarks, history, search engines, custom or blank home pages, ordinary-session restoration and site settings |
| Reading | Ad subscriptions and element rules, text zoom, image viewing, userscripts and extensions |
| AI | Multiple providers and protocols, character cards, memory, attachments, speech, tools, workflows and conversation details |
| Media | Resource discovery, downloads, mpv playback, subtitles, queues, Anime4K and floating/fullscreen presentation |
| Workspace | File management, Ubuntu terminal, SSH, code execution and workspace tools |
| Extensions | Scripts, ToolPkg, Skill, MCP, marketplace installation, environment variables and permissions |

Some standalone music, document-reader and mini-app surfaces remain under construction. Local inference requires compatible model files. Device and service acceptance are tracked separately in the [development plans](docs/TODO/README.md).

## Getting started

### Requirements

- Android 8.0 or later; the APK currently includes only `arm64-v8a`.
- Application ID: `com.kiyori`.
- Development version: `0.1.0`, version code `45`; check the actual APK and [build configuration](app/build.gradle.kts).
- No public APK download is available yet.

### Build and install

```bash
git clone https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

Follow the [build guide](docs/doc-src/dev-core/BUILDING.md) to prepare JDK 21, Android SDK/NDK, Rust, Node.js and the required large AAR/model/JNI/subpack inputs. Cloning alone is not sufficient. Do not recursively initialize every submodule: the optional private nightly-build submodule is not needed for a normal Debug build.

After preparing the environment:

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

On Windows, use `./gradlew.bat` with the same arguments. The output is `app/build/outputs/apk/debug/app-debug.apk`. Transfer it to a compatible device, or install on an already authorized ADB device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### First launch

1. Read the introduction, User Agreement and Privacy Policy.
2. Select only the permissions you need. You may enter without granting them and manage them later under Settings → More Features → Permissions Management.
3. Open Browser, or choose a custom URL or blank page under Settings → Browser → Home Page.
4. For AI, open AI Home and configure your provider, protocol, model and API key. A model key is not required to browse or remain on AI Home.
5. Prepare the terminal environment or local models when those capabilities are needed.

The [user guides](docs/user-guide/README.md) are maintained in Chinese. The [Chinese README](README.md) is the complete product entry.

## Your workspace

Software Home sits between the Minus-One page and AI Home. The bottom navigation opens Home, Browser, Mini Apps, Files and Settings. The AI menu provides Extensions, Toolbox and Workflows; deep pages follow their Back chain. Settings opened from a browser or AI page retains that source.

- Configure home-page and ordinary-session restoration independently; incognito windows do not enter startup restoration records.
- Adjust ad blocking, scripts, zoom and external-app behavior for a particular website.
- Use context actions for links, images and media; editable fields retain Android's native selection and clipboard controls.
- Configure each search or remote tool independently. The built-in Bilibili package offers read-only retrieval, export and media processing with host-owned credentials.
- Inspect locally recorded requests, tools, revisions and failures in Conversation Details.
- Use four playback-cache policies. Full-video caching is eligible session caching, not an offline download.
- Application proxy settings affect Kiyori requests and do not change other applications or replace Android's system VPN.

See [extensions](docs/user-guide/extensions.md) and [network/media settings](docs/user-guide/network_and_media.md) for configuration details.

## Data and connections

Cloud-model requests go to the provider you configure. Kiyori does not provide an inference relay or connect to Operit update and announcement services. Web browsing, search, speech, marketplaces, GitHub login and remote tools connect to their respective services.

New public files use `Download/Kiyori` and `Pictures/Kiyori`. Kiyori and Operit have different application IDs; export a backup from the old application and explicitly import it. Existing Operit public directories are not automatically scanned, merged or deleted.

Website credentials use device-bound encrypted private storage and are not captured or filled in incognito profiles. AI diagnostic Markdown is plaintext and may contain private conversation or tool content even after credentials are redacted. Review every export before sharing. See [privacy and data](docs/user-guide/privacy_and_data.md).

## Troubleshooting and contributing

| Question | Guidance |
| --- | --- |
| Why does installation fail? | Check Android version, ARM64 support, storage and signing identity. Back up data before resolving a signature mismatch. |
| Can it replace an Operit installation? | No. Use explicit backup import; the application IDs differ. |
| Does a successful build prove every feature works? | No. Device, OEM, network and release checks are separate acceptance layers. |
| What should a bug report include? | Device/Android version, application version or commit, reproduction steps, expected/actual behavior and necessary redacted diagnostics. |

- [Issues](https://github.com/Kiyori-CN/Kiyori/issues)
- [Contribution guide](docs/doc-src/dev-core/CONTRIBUTING.md)
- [Build guide](docs/doc-src/dev-core/BUILDING.md)
- [Project context](CONTEXT.md) and [runtime contracts](docs/doc-src/contracts/README.md)
- [Repository architecture](docs/doc-src/architecture/repository_architecture.md), [layout and naming](docs/doc-src/dev-core/REPOSITORY_LAYOUT.md), and [developer tools](tools/README.md)
- [Documentation catalog](docs/CATALOG.md)
- [Script development](docs/SCRIPT_DEV_GUIDE.md) and [ToolPkg format](docs/TOOLPKG_FORMAT_GUIDE.md)

## Upstream and license

Kiyori evolves from [Operit](https://github.com/AAswordman/Operit) and retains applicable authorship and source attribution. See [NOTICE](NOTICE) and the in-app open-source inventory for component origins.

This repository is provided under [GNU GPL v3 or later](LICENSE). Component licenses and accompanying notices remain applicable. Kiyori maintains its own brand, repository and distribution channel.
