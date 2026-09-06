<div align="center">
  <img src="app/src/main/assets/logo.svg" width="128" height="128" alt="Kiyori logo">
  <h1>Kiyori</h1>
  <p><strong>A collaborative AGI system for Android</strong></p>
  <p>People and AI working in the same environment to understand information, use tools, and complete tasks.</p>
  <p>AI Chat · Terminal · Browser · Player · Files · Extensible Workflows</p>
  <p>
    <a href="#getting-started">Get started</a> ·
    <a href="#core-capabilities">Capabilities</a> ·
    <a href="#building-from-source">Build from source</a> ·
    <a href="docs/README.md">Documentation</a> ·
    <a href="https://github.com/Kiyori-CN/Kiyori/issues">Issues</a> ·
    <a href="README.md">简体中文</a>
  </p>
  <p>
    <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84" alt="Android 8.0 or later">
    <img src="https://img.shields.io/badge/ABI-arm64--v8a-1E88E5" alt="ARM64 architecture">
    <img src="https://img.shields.io/badge/status-in_development-D97706" alt="In development, no public release">
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0--or--later-555555" alt="GPL v3 or later"></a>
  </p>
</div>

**Kiyori aims to build the world's most capable Android AGI system, bringing AI chat, a terminal, a browser, a media player, file management, and extensible tools into a comprehensive environment for human-AI collaboration.**

We want you to be able to set a goal on your phone, investigate and plan with AI, work with real web pages, commands, and files, and inspect both the process and the result. Think of the intended interaction as "Codex on a phone": collaboration organized around tasks, connecting discussion, action, and artifacts, with the experience designed for Android touch input, permissions, storage, and application lifecycles.

**AGI describes the long-term research direction; "the world's most capable" is an ambition.** Today, Kiyori is an independent Android application evolving from Operit. It already has foundations for AI, browsing, terminal execution, files, and media, while integration across these areas and the device experience continue to develop.

> [!IMPORTANT]
> **There is no public release yet.** Build a development APK from this repository; no public official APK download channel is available. Implementation, automated checks, physical-device behavior, and release readiness are evaluated separately. See the [development plans](docs/TODO/README.md) for the status of each workstream.

## Contents

- [Project vision](#project-vision)
- [How people and AI collaborate](#how-people-and-ai-collaborate)
- [Capability overview](#capability-overview)
- [Core capabilities](#core-capabilities)
- [Current status](#current-status)
- [Getting started](#getting-started)
- [Configuring models and extensions](#configuring-models-and-extensions)
- [Building from source](#building-from-source)
- [Architecture and repository guide](#architecture-and-repository-guide)
- [Data, privacy, and permissions](#data-privacy-and-permissions)
- [Frequently asked questions](#frequently-asked-questions)
- [Documentation and contributing](#documentation-and-contributing)
- [Upstream, acknowledgments, and license](#upstream-acknowledgments-and-license)

## Project vision

Phones already support much of our reading, communication, creation, and file handling. Kiyori aims to connect that work into collaborative tasks: people establish goals, exercise judgment, and make important decisions; AI contributes understanding, reasoning, and execution; the application supplies a real environment that both can observe and use.

Development follows five directions:

1. **Move from conversation to tasks.** Connect model responses to web interaction, code execution, file processing, and media tools so that work can produce actual artifacts.
2. **Share the working environment.** Use shared browser sessions, visible terminals, explicit working directories, and common tool entry points to maintain context and let people inspect and continue the work.
3. **Keep execution observable and under control.** Make tool calls, output, errors, and final states inspectable, with authorization appropriate to the operation's risk.
4. **Keep models and capabilities open.** Support cloud models, self-hosted services, on-device inference, scripts, ToolPkg, Skill, MCP, and workflows so that different tasks can use suitable capabilities.
5. **Design for real Android use.** Account for touch interaction, system permissions, application lifecycles, network conditions, file access, and resource consumption.

The current engineering foundation consists of the Kiyori product shell, a shared browser, and the embedded Operit AI runtime. These domains are progressively being connected into a unified task experience. The sections below describe implemented boundaries; further development is tracked in the project plans.

## How people and AI collaborate

A typical collaboration has five stages:

1. **Define the goal.** Describe the problem, completion criteria, and permitted scope. Supply context through chat, characters and prompts, attachments, and a workspace.
2. **Understand the situation.** Open pages, select files, and add background. Use page snapshots, file reading, search, and memory retrieval to establish the task's evidence.
3. **Work together.** Review important actions and handle authorization. AI uses browser tools, visible terminals, the code runner, extensions, or workflows; you can participate manually as needed.
4. **Inspect the result.** Check page changes, command output, and generated files in the browser, terminal, file manager, and player. Review execution records in Conversation Details.
5. **Iterate.** Refine requirements and adjust tools. Keep useful context and artifacts in chat history, workspaces, memory, downloads, or exported files.

**Shared state is the most direct foundation for collaboration today.** Manual browsing and AI browser tools use one Browser Runtime and real WebView sessions. Terminal execution through the code runner uses a visible PTY session. The file manager and AI file tools reuse existing file capabilities. Media discovered in the browser can be handed to the shared player or download manager.

### Tasks to start with

These examples illustrate how to organize a task. Configure the relevant models, tools, and permissions first. Results depend on the model, website restrictions, file formats, and device environment.

**Research** · Browser, search, AI chat, file tools

> Compare the technical approaches on these pages, keep the sources, and save the findings in the selected working directory.

**Mobile development** · Workspace, file tools, terminal, code runner

> Read this project, explain its entry point, edit the specified file, and run the project's existing checks in the terminal.

**File organization** · File manager, AI tools, operation authorization

> Propose a plan for organizing these files, rename them after confirmation, and produce a change list.

**Media processing** · File manager, FFmpeg tools, player

> Inspect this video's metadata, process it with the configured media tools, and open the output for review.

**Repeated work** · Workflows, scripts, ToolPkg, conversations and logs

> Turn these verified processing steps into a workflow and retain its execution results.

Android storage and the Ubuntu filesystem have different paths and permissions. Moving files between environments requires an actually supported copy or mount path.

## Capability overview

- **[AI chat](#ai-conversations-models-and-memory):** Multiple providers and protocols, streaming, thinking mode, character cards, attachments, speech, and tool calls. Start in AI Home or AI assistant settings.
- **[Browser](#browser-and-web-collaboration):** Windows, bookmarks, history, search, session restoration, site settings, ad blocking, and page tools. Open Browser or the Minus-One page.
- **[Terminal and code](#terminal-code-execution-and-remote-development):** Ubuntu/PRoot, visible sessions, multiple languages, and SSH. Use Toolbox, the code runner, and relevant extensions.
- **[Files and workspaces](#file-management-and-project-workspaces):** Directories, tabs and panes, search, authorized file tools, workspace binding, and project rules. Open Files or a conversation workspace.
- **[Media and downloads](#player-downloads-and-media-processing):** Discovery, downloads, mpv, subtitles, queues, Anime4K, floating playback, and FFmpeg tools. Browser, Downloads, and the player connect these entry points.
- **[Memory and automation](#extensions-tools-and-workflows):** Memory spaces, documents and graphs, retrieval, visual workflows, and background scheduling. Open Memory or Workflows in the AI menu.
- **[Extensions](#extensions-tools-and-workflows):** JavaScript/TypeScript, ToolPkg, Skill, MCP, marketplaces, environment variables, and tool authorization. Manage them through Extensions and Toolbox.
- **[Device and connections](#android-capabilities-and-cross-device-connections):** Central permissions, Android automation, Shizuku/Root, app proxy, HTTP, and remote extensions. Use Settings and the relevant enabled tools.

## Core capabilities

### AI conversations, models, and memory

AI is the entry point for discussing and coordinating a task. Configure models, characters, and tools for different uses, attach relevant content or bind a workspace, and inspect execution as it happens.

- **Model choice:** Connect providers such as OpenAI, Anthropic, Google, DeepSeek, and Qwen, as well as compatible services and custom endpoints. The provider and protocol configuration jointly determine supported combinations.
- **Interaction and presentation:** Streaming output, thinking mode, Markdown, code, and mathematical notation. Attachment understanding and speech depend on the selected model and service configuration.
- **Characters and context:** Character cards, user preferences, prompts, history management, context compaction, and workspace rules supply ongoing task context.
- **Memory management:** Memory spaces, document import, search, and relationship graphs organize reusable information. Retrieval quality depends on the content and configuration.
- **Execution records:** Conversation Details exposes locally recorded requests, tools, errors, revisions, and final states, with diagnostic export.
- **Failure and recovery:** Request failures and unknown submission states remain visible. Protocols that support continuation follow their specific execution contracts; unconditional resumption is not promised for every service.

Reasoning quality, tool selection, and execution success depend on the actual model and environment. Kiyori provides the runtime and collaboration environment; model judgments still require review.

### Browser and web collaboration

The browser is both an everyday browsing interface and a real environment that AI can operate.

- **Everyday browsing:** Multiple windows, bookmarks, history, search engines, custom or blank home pages, and configurable ordinary-session restoration.
- **Page experience:** Per-site settings, text zoom, forced zoom, desktop/mobile access settings, context actions for links and media, and fullscreen image viewing.
- **Content management:** Ad subscriptions, custom URL and element rules, an ad-marking workbench, userscripts, and the Browser Plugin Center.
- **Page tools:** Navigation, snapshots, screenshots, clicks, typing, form filling, option selection, file upload, window operations, and inspection of source, console messages, and network resources.
- **Session continuity:** Human interaction and AI tools share WebSession state and operate on the same actual page.
- **Website identity:** Ordinary/incognito Profile boundaries, website password management, and a built-in plugin for manually reading the current page's cookies.

Browser tools implement a defined API subset, not the full desktop Playwright API. Ad rules and userscripts also have specific compatibility boundaries. Chrome extensions are not a general installable plugin format here.

See the [browser contract](docs/doc-src/contracts/browser.md) and [browser extension platform](docs/doc-src/architecture/browser_plugin_platform.md).

### Terminal, code execution, and remote development

Kiyori integrates an Ubuntu terminal through [KiyoriTerminalCore](https://github.com/Kiyori-CN/KiyoriTerminalCore), providing a foundation for command execution and mobile development.

- **Real terminal sessions:** Ubuntu/PRoot, session management, visible commands and output, execution state, and exit codes.
- **Multiple languages:** The code runner provides source-string and file entry points for JavaScript, Python, Ruby, Go, Rust, C, and C++. JavaScript has separate ES5 and Node.js paths.
- **Environment preparation:** Prepare runtimes and compilers through the relevant tool screens, then check the actual environment before execution. Python and Node packages use their own persistent environments.
- **Collaboration:** AI terminal execution can appear in a visible session so that you can inspect commands, output, and the working directory, then continue working on the project.
- **Remote access:** Connect configured targets through SSH, remote Kiyori, Windows companion tools, and other extensions.

An identity inside Ubuntu is not Android system Root. `super_admin:terminal` operates in Ubuntu/PRoot; `super_admin:shell` targets Android Shell/Root and requires actual device authorization.

See [platform, storage, and terminal contracts](docs/doc-src/contracts/platform_storage.md) and the [code-runner workstream](docs/TODO/code_runner_terminal_toolchain/index.md).

### File management and project workspaces

The file manager lets you inspect and organize actual data. A workspace connects a directory to an AI conversation.

- The file-manager interface supports directory browsing, tabs and panes, sorting, hidden files, search, file/folder creation, and other connected actions.
- Underlying file tools provide reading, writing, copying, moving, deletion, and archive operations for authorized AI calls. Some current context-menu buttons still await business-action integration; a visible label alone does not establish readiness.
- Use Android-authorized directories and existing file-access capabilities. Reachable files depend on the OS version, permissions, and selected environment.
- Bind a conversation to a working directory, read project files, observe changes, and incorporate relevant content into context.
- Read root `AGENT.md` / `AGENTS.md` files according to the workspace contract, retaining the existing `.operit/config.json` format.
- Inspect artifacts through Files, Downloads, and the workspace instead of relying solely on a model's completion message.

Establish the target directory, file scope, and overwrite intent before batch operations. See [extensions and workspaces](docs/doc-src/contracts/extensions_workspace.md).

### Player, downloads, and media processing

Media capabilities span discovery, saving, playback, and tool-based processing.

- **Resource discovery:** The browser catalogs video, audio, images, scripts, and other page resources, deriving media candidates from actual page and request evidence.
- **Download management:** An internal engine and an Android system-download entry point, task state, filtering, sorting, concurrency configuration, and batch actions. Removing a record and deleting its file are different operations.
- **mpv player:** Local and network media, queues, subtitles, playback speed, gestures, rotation, floating/fullscreen presentation, and Anime4K shader settings.
- **Cache policies:** Data Saver, Smart Balanced, Smooth Priority, and Full Cache. Full Cache applies only to eligible media and is a session cache.
- **Media tools:** Use the FFmpeg extension for supported metadata, transcoding, and processing operations, then inspect the result with the player or file manager.

**Playback caching is not offline downloading.** Playback and processing also depend on source access, encoding, device decoding capabilities, and configuration. Resource discovery does not guarantee access to every site's content. Respect source-site rules and content rights when using media tools.

See the [network and media guide](docs/user-guide/network_and_media.md) and [media/download contracts](docs/doc-src/contracts/media_downloads.md).

### Extensions, tools, and workflows

Extensions allow capabilities to be combined for a task without putting every feature directly into the Android application.

| Type | Purpose and documentation |
| --- | --- |
| **Scripts** | Provide tools through JavaScript and host APIs; TypeScript can be used for development. See [script development](docs/SCRIPT_DEV_GUIDE.md). |
| **ToolPkg** | Organize tools, resources, configuration, and supported host UI extensions in structured packages. See [ToolPkg format](docs/TOOLPKG_FORMAT_GUIDE.md). |
| **Skill** | Import and manage reusable skill instructions and resources. See the [extension guide](docs/user-guide/extensions.md). |
| **MCP** | Connect configured MCP services and use their tools. See the [extension runtime contract](docs/doc-src/contracts/extensions_workspace.md). |
| **Workflows** | Organize triggers and processing steps as nodes, with manual execution and existing scheduling entry points. See the [workflow implementation](app/src/main/java/com/ai/assistance/operit/core/workflow). |

Bundled tools cover search, academic retrieval, code, files, media, system operations, and remote connections. See the [production package list](tools/example_packages/packages_whitelist.txt) and [extension examples](examples/README.md). Bundled does not mean enabled by default or that every external service has already been configured.

For example, OpenAI Search, Brave Search, and academic tools use their own configuration. The Bilibili toolkit provides read-only retrieval, content export, and media processing. Configure packages that require environment variables before explicitly enabling them.

Workflow scheduling uses Android background execution mechanisms. Battery optimization, background restrictions, and device state affect execution; it should not be relied on for precise real-time timing.

### Android capabilities and cross-device connections

- Use central permission management to inspect access needed for files, notifications, the microphone, overlays, accessibility, and other functions.
- Access device capabilities through enabled automation and system tools. Shizuku, Root, and virtual-display capabilities have separate environment requirements.
- Configure application-level routing for Kiyori's AI, browsing, downloads, player, and tool requests, with Rule, Global, and Direct modes.
- Connect other interfaces or devices through WebChat, external HTTP chat, and remote extensions, managing access tokens and network reachability through their respective settings.

These entry points require explicit configuration and permissions. The app proxy changes Kiyori's request paths; it is not a system VPN and does not configure proxy routing for other apps.

## Current status

Use this overview to choose how to try the project. The [development plans](docs/TODO/README.md) retain detailed implementation and acceptance evidence.

| Scope | Current position |
| --- | --- |
| **Implemented foundations** | AI chat and tools, shared browser, terminal, file manager, player, downloads, memory, extensions, workflows |
| **User preparation required** | Model services and keys, on-device models, terminal toolchains, extension variables, remote services, permissions |
| **Continuing development** | Broader collaboration across modules, file context-menu action integration, the standalone mini-app entry, dedicated music and document-reading experiences |
| **Separate acceptance** | Physical touch interaction, OEM behavior, long-running use, network services, model compatibility, performance |
| **Not provided yet** | A public production release channel or a completion guarantee across all devices and services |

A mini-app entry does not mean a complete mini-app platform has shipped. Collaboration examples and ambitions in this README do not substitute for end-to-end acceptance. Workstreams marked `verification_pending` still require their specified device or external-environment checks.

## Getting started

### 1. Check the device and installation path

| Item | Requirement or description |
| --- | --- |
| Android | Android 8.0 / API 26 or later |
| CPU architecture | The APK currently includes only `arm64-v8a` |
| Application ID | `com.kiyori` |
| Version snapshot | `0.1.0`, `versionCode 45`, checked on 2026-09-06; the [build configuration](app/build.gradle.kts) and actual APK are authoritative |
| Storage | Reserve space for the APK, terminal environment, models, workspaces, and downloads; requirements vary with usage |
| Acquisition | Build a Debug APK from source as described below |

The current APK does not target devices or emulators that support only 32-bit ARM or x86/x86_64. Kiyori and Operit have different application IDs and can be installed as independent apps. Data migration requires explicit import.

### 2. Complete first-run setup

1. Install the development APK and read the introduction, User Agreement, and Privacy Policy.
2. Select permissions as needed. You can enter without granting them and return to **Settings → More Features → Permissions Management** later.
3. Open Browser from the bottom navigation. Choose a custom URL or blank page under **Settings → Browser → Home Page** when needed.
4. Open AI Home, configure the provider, protocol, model, endpoint, and API key, then send a short message to verify the setup.
5. Select a working directory for file or project tasks. Before code execution, prepare the terminal environment and required toolchains.
6. Configure and enable the packages you need in Extensions, then try one task with a clearly bounded scope.

Browsing does not require a model subscription or configuration; AI Home can also be opened without a key. Cloud models, speech, search, and remote tools may have separate charges under their providers' terms.

### 3. Learn the navigation

```text
Software Home area
├── Left: Minus-One page, bookmarks, history, and downloads
├── Center: Software Home
└── Right: AI Home, conversations, models, and tools

Bottom navigation
├── Software Home
├── Browser
├── Mini Apps
├── Files
└── Settings

AI page menu
├── Extensions / Toolbox / Workflows
├── Conversations and memory
└── AI assistant settings and related entry points
```

AI Home and AI top-level pages show the menu; deeper pages follow their Back chain. Settings opened from Browser or AI retains that source and returns to the original page or AI route when closed. See the [first-use guide](docs/user-guide/getting_started.md).

## Configuring models and extensions

### Choose where the model runs

| Mode | Use case and preparation |
| --- | --- |
| **Cloud provider** | Hosted models for conversations and tool tasks. Prepare an account, API key, model name, endpoint, and the correct protocol. |
| **Compatible API** | An existing gateway or custom endpoint. Confirm the service's actual protocol, authentication, and model capabilities. |
| **Self-hosted** | Ollama, LM Studio, or another local service. Prepare a phone-reachable address, model, and required authentication. |
| **On-device** | Compatible models through MNN or llama.cpp. Prepare model files in the matching format and sufficient device resources. |

**Provider and protocol are separate choices.** The project supports OpenAI Chat Completions, OpenAI Responses, Anthropic Messages, and native paths for Google and on-device engines. Not every provider supports every protocol. A compatible endpoint does not automatically support all extensions of the corresponding official service.

On a phone, `localhost` and `127.0.0.1` refer to the phone itself. To reach a model service on a computer, use an address reachable from the phone and check the server's listening interface, authentication, and network settings.

On-device inference does not make the whole application offline. Browsing, web search, marketplaces, remote tools, and some speech capabilities have their own network requirements.

### Configure tools and services separately

- **Main model:** Save the provider, protocol, address, and authentication in model settings. Set tool and thinking options according to the actual model's capabilities.
- **Speech:** Recognition and synthesis use their own service profiles. A working chat model does not prove that speech is configured.
- **Search:** Standalone tools such as OpenAI Search read their own package configuration rather than inheriting the chat model's endpoint and key.
- **Extensions:** Review declared environment variables, permissions, and startup requirements before enabling scripts, ToolPkg, or MCP.
- **Network:** Manage application routing under **Settings → More Features → Network Proxy**. Direct bypasses the embedded proxy; a system VPN can still affect traffic.

After configuration, run a small query or operation and inspect the actual output. Diagnose authentication, rate-limit, protocol, and network problems from their reported errors. Repeated submissions may incur additional charges or execute state-changing actions again.

See the [extension guide](docs/user-guide/extensions.md), [network guide](docs/user-guide/network_and_media.md), and [AI request contract](docs/doc-src/contracts/ai_execution.md).

## Building from source

This section outlines the route from source to APK. The [build guide](docs/doc-src/dev-core/BUILDING.md) is authoritative for complete dependency preparation, platform differences, native inputs, and troubleshooting.

### 1. Prepare the toolchain

The following baseline was checked on 2026-09-06. Repository configuration and CI remain authoritative as development continues.

| Tool | Baseline |
| --- | --- |
| JDK | 21; application Java/Kotlin bytecode targets JVM 17 |
| Gradle | Use the supplied Wrapper |
| Android SDK | Platform 37; application target SDK 34 |
| Android Build Tools | 36.0.0 |
| Android NDK | 28.2.13676358 |
| CMake | 3.22.1 |
| Rust | 1.88.0, target `aarch64-linux-android` |
| Node.js | 22, using npm and committed lockfiles |
| Python | Python 3; repository checks use the project `.venv` |

Version sources: [Gradle version catalog](gradle/libs.versions.toml), [Wrapper](gradle/wrapper/gradle-wrapper.properties), [Gradle properties](gradle.properties), and [CI workflows](.github/workflows).

### 2. Obtain the source and terminal submodule

```bash
git clone https://github.com/Kiyori-CN/Kiyori.git
cd Kiyori
git switch main
git submodule sync -- terminal
git submodule update --init --recursive terminal
```

**Initialize only `terminal` for a normal build.** Do not use `git clone --recurse-submodules` for the whole repository. The optional private nightly-build submodule is not required for ordinary Debug builds.

### 3. Prepare build inputs

After cloning, complete the following steps in the [full build guide](docs/doc-src/dev-core/BUILDING.md):

1. Install the fixed Android SDK, NDK, CMake, and Rust target, and configure Java and Android environment paths.
2. Install npm dependencies for the root tools, WebChat, and required example projects; create the project `.venv`.
3. Use `local.properties.example` to create untracked local configuration, preserving any existing private configuration.
4. Obtain `libs.zip`, `models.zip`, `subpack.zip`, and `jniLibs.zip`, prepare them with the controlled scripts, and validate the selected player and other native inputs.
5. Generate WebChat and the example inputs required by the guide. Gradle generates directory-based production ToolPkg assets during the build.

**Cloning alone is not sufficient.** Not all large AAR, model, JNI, and subpackage inputs are stored in Git. Do not delete protected inputs as if they were disposable caches, or bypass input validation to work around missing dependencies.

### 4. Build the Debug APK

Linux / macOS:

```bash
./gradlew :app:assembleDebug --no-daemon --console=plain
```

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

Standard output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 5. Install and verify

Transfer the APK to a compatible device, or install it on an already connected and authorized ADB development device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

An APK cannot directly replace another build with the same application ID but a different signing key. Back up data before resolving a signature mismatch. Check the APK's application ID, version, ABI, signature, and packaging, then test startup and actual operations. Commands are listed under [APK verification](docs/doc-src/dev-core/BUILDING.md#9-独立核验-apk).

For contribution checks, see the [contribution guide](docs/doc-src/dev-core/CONTRIBUTING.md) and [CI guide](ci/README.md). A successful build establishes local build readiness, not physical-device or production-release acceptance.

## Architecture and repository guide

Kiyori uses Kotlin, Jetpack Compose, and native Android capabilities for the product interface, integrates the Operit AI runtime, and uses separate Android/native modules for terminal, inference, scripting, and rendering. WebChat uses React, TypeScript, and Vite.

### Key design choices

- **Navigation through the product shell:** Home, Browser, AI, Files, and Settings are organized within one shell while retaining their own state and return destinations.
- **Shared real runtimes:** People and AI tools reuse the same browser. Player, downloads, permissions, and storage each have an explicit state owner.
- **Tools call existing capabilities:** AI, extensions, and interfaces collaborate through existing capabilities and adapters instead of duplicating page, download, or file state.
- **Native task isolation:** Player and FFmpeg tools use separate non-exported processes. Terminal integration uses submodule services and AIDL.
- **Explicit compatibility:** Protocols, persistence formats, AIDL/JNI names, and upstream ecosystem identifiers with active consumers are retained.

### Main directories

```text
Kiyori/
├── app/              Android application, shell, AI, browser, files, media integration
├── terminal/         KiyoriTerminalCore submodule, Ubuntu terminal and sessions
├── llama/            llama.cpp on-device inference
├── mnn/              MNN inference and speech-related native capabilities
├── quickjs/          JavaScript engine and host bridge
├── dragonbones/      DragonBones animation
├── mmd/              MMD models, physics, and rendering
├── fbx/              FBX parsing and rendering integration
├── showerclient/     Virtual-display, input, and screenshot client
├── web-chat/         WebChat frontend
├── examples/         Scripts, ToolPkg, and companion-tool examples
├── buildSrc/         Custom Gradle tasks and behavioral tests
├── tools/            Development, build, environment, and diagnostic tools
├── ci/               Reproducible checks, dependency preparation, and tests
├── config/           Machine-readable architecture and engineering constraints
└── docs/             User guides, designs, workstreams, and licensing material
```

Root settings declares nine Android modules. `buildSrc` is build infrastructure and is not packaged into the APK. `com.kiyori` contains product, platform, and integration boundaries; `com.ai.assistance.operit` still contains AI and some product implementations. Package names alone do not fully define feature ownership.

See [project context](CONTEXT.md), [repository architecture](docs/doc-src/architecture/repository_architecture.md), [layout conventions](docs/doc-src/dev-core/REPOSITORY_LAYOUT.md), and [runtime contracts](docs/doc-src/contracts/README.md).

## Data, privacy, and permissions

### Where data goes

| Data or capability | Processing boundary |
| --- | --- |
| Cloud-model requests | Sent to the configured provider or endpoint, following selected routing and service terms |
| On-device inference | Uses the configured local engine and model; network tools are evaluated separately |
| Browsing, search, marketplaces, speech | Connect to their respective sites or services; integration does not make them local operations |
| Remote tools and HTTP interfaces | Use explicitly enabled targets, access tokens, and network configuration |
| New public files | Use product directories such as `Download/Kiyori` and `Pictures/Kiyori` |
| Browser restoration | Saves ordinary-window URLs, titles, and necessary navigation information according to enabled settings; no incognito restoration records |
| Website passwords | Device-bound encrypted private storage for ordinary Profiles; no saving or filling passwords in incognito |
| Conversation audit and diagnostics | Audit payloads use encrypted app-private storage; exports must be reviewed according to their format and content |

Kiyori does not provide an LLM inference relay and does not connect to Operit's update, patch, or remote-announcement services. Configured services, marketplaces, and extensions still have their own data policies and charges.

### Authorization and execution

Android system permissions, permission to invoke a tool, and the risk of a specific action are different control layers. Grant permissions as needed and establish targets and scope before batch file operations, privileged device access, account changes, or external actions.

AI actions in the application distinguish read-only, low-impact, high-impact, and critical operations. Allowing a tool does not automatically remove the confirmation requirements of a high-risk action. See the [tool authorization contract](docs/doc-src/contracts/extensions_workspace.md#应用内-ai-动作授权).

### Backups, migration, and diagnostics

- **Migrating from Operit:** Export a backup from the old app, then explicitly import it into Kiyori. Keep the original backup until conversations, characters, settings, and workspaces have been checked.
- **Public directories:** Kiyori does not automatically scan, merge, or delete `Download/Operit`. Select migration inputs explicitly.
- **Private information:** API keys, Authorization values, cookies, passwords, tokens, private keys, and signing material should never be included in public issues or the repository.
- **Diagnostic export:** AI diagnostic Markdown is plaintext UTF-8. Credential redaction can still leave private conversations, tool output, and business information. Share complete `.kiyori-audit` exports only with trusted, explicitly authorized recipients as well.

See [privacy, data, and migration](docs/user-guide/privacy_and_data.md). The full User Agreement and Privacy Policy are available under **Settings → More Features** in the app.

## Frequently asked questions

<details>
<summary><strong>Has Kiyori achieved AGI, or can it operate a phone completely autonomously?</strong></summary>

AGI is the long-term research direction. The current product is a developing application integrating models, tools, and an Android working environment. Its execution scope depends on implemented tools, model capabilities, system permissions, and real device conditions. Comprehensive collaboration remains under development.

</details>

<details>
<summary><strong>Is there an official APK? Why are there no production-release installation instructions?</strong></summary>

There is no public release yet. Build a development APK from this repository. Release signing, distribution channels, and release acceptance are separate work; a Debug build does not replace them. Third-party APKs should not be treated as official releases.

</details>

<details>
<summary><strong>Do I need an API key, Root, or Shizuku?</strong></summary>

Ordinary browsing does not require a model key or all privileged permissions. Cloud models require their service authentication; on-device inference requires model files. Shizuku/Root is needed only for capabilities that depend on those execution identities. Configure capabilities as you need them.

</details>

<details>
<summary><strong>Can I work offline, connect a model on my computer, or use any compatible API?</strong></summary>

Compatible MNN or llama.cpp models can use on-device inference. Models on a computer or server need an endpoint reachable from the phone. Protocol compatibility does not guarantee every feature: test tool calls, vision, thinking, and recovery separately. Online tools still require a network connection.

</details>

<details>
<summary><strong>Do AI, the browser, and the terminal share the same working environment?</strong></summary>

People and AI page tools share real browser sessions. Terminal execution through the code runner uses visible sessions, and the file manager and AI file tools reuse existing capabilities. These connections retain explicit session, path, and permission boundaries. They do not automatically make every player or other subsystem feature callable as an AI tool.

</details>

<details>
<summary><strong>Can I install Chrome extensions or use the full Playwright API?</strong></summary>

Do not assume that compatibility. Kiyori has its own userscript, browser-plugin, and tool interfaces, with support defined by implementation and contracts. Chrome extensions, userscripts, ToolPkg, and MCP are different extension formats.

</details>

<details>
<summary><strong>Why does installation fail, or why can't it replace Operit?</strong></summary>

Check Android version, ARM64 support, storage, and signing identity. Kiyori uses `com.kiyori`, so it is a separate application and cannot directly replace an Operit installation. Different signatures for the same package also prevent direct replacement. Back up data first and use explicit import for migration.

</details>

<details>
<summary><strong>Why do features remain pending verification after a successful build?</strong></summary>

Compilation and packaging cannot prove touch behavior, system restrictions, decoding, network services, or model behavior on real devices. The project distinguishes automated checks, builds, devices, and external environments. `verification_pending` means that the corresponding field acceptance remains outstanding.

</details>

<details>
<summary><strong>What should a bug report include?</strong></summary>

Provide the device, Android version, Kiyori version or commit, relevant model and tools, minimal reproduction steps, expected and actual behavior, and necessary redacted logs. For build failures, also include the command, toolchain versions, and the first root-cause error. Review screenshots, exports, and logs for private content before submitting.

</details>

## Documentation and contributing

### Find the right guide

| Goal | Reading |
| --- | --- |
| First steps after installation | [User guides](docs/user-guide/README.md) · [First use](docs/user-guide/getting_started.md) |
| Extensions, search, and remote tools | [Extension guide](docs/user-guide/extensions.md) |
| Network, caching, and device privileges | [Network and media](docs/user-guide/network_and_media.md) |
| Privacy, exports, and importing old data | [Data and migration](docs/user-guide/privacy_and_data.md) |
| Build an APK from source | [Build guide](docs/doc-src/dev-core/BUILDING.md) |
| Understand the project and runtime | [Project context](CONTEXT.md) · [Repository architecture](docs/doc-src/architecture/repository_architecture.md) · [Domain contracts](docs/doc-src/contracts/README.md) |
| Develop scripts or ToolPkg | [Script guide](docs/SCRIPT_DEV_GUIDE.md) · [ToolPkg format](docs/TOOLPKG_FORMAT_GUIDE.md) · [Examples](examples/README.md) |
| Follow plans and pending acceptance | [Development task index](docs/TODO/README.md) |
| Locate all documentation and tools | [Documentation center](docs/README.md) · [Catalog](docs/CATALOG.md) · [Tool index](tools/README.md) |

Chinese is the primary documentation language. This English README provides the corresponding overview and usage/build entry points; most linked detailed guides are maintained in Chinese. See the [Chinese README](README.md) for the primary product entry.

### Contribute

Contributions can include code, documentation, translations, tool packages, reproducible issues, and device-verification results. Current areas of interest include collaboration across modules, mobile interaction, execution reliability, performance, and model/extension compatibility.

1. Check [Issues](https://github.com/Kiyori-CN/Kiyori/issues) and relevant workstreams. Explain the problem, scenario, and expected result.
2. Read the [contribution guide](docs/doc-src/dev-core/CONTRIBUTING.md) and organize changes around existing modules and interfaces.
3. Provide verification appropriate to the change, identifying any device or external-service checks not performed.
4. Target Pull Requests at `main`, preserving attribution, licenses, and compatibility contracts.

## Upstream, acknowledgments, and license

Kiyori evolves from [Operit](https://github.com/AAswordman/Operit). Its authors and contributors established the AI, tool, and Android integration foundations; applicable author information, source attribution, and license notices are retained. Terminal integration uses the independent [KiyoriTerminalCore](https://github.com/Kiyori-CN/KiyoriTerminalCore) submodule.

The project also uses or integrates open-source components including mpv, FFmpeg, Anime4K, llama.cpp, MNN, QuickJS, and Mihomo. See [NOTICE](NOTICE), [third-party legal materials](docs/legal/third_party), and the in-app open-source inventory for origins, fixed-artifact information, and component licenses.

This repository is provided under **[GNU General Public License v3.0 or later](LICENSE)**. Individual component licenses, notices, and corresponding-source obligations still apply when modifying or redistributing the software. Kiyori independently maintains its product name, icons, repository, and distribution channels.
