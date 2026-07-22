# Kiyori shared context

This file defines the stable product language and compatibility boundaries for this repository.
Current work status and implementation notes belong in `docs/TODO/`.

## Product identity

- **Kiyori** is the brand.
- **Kiyori** is the Android application and user-visible product name.
- **Kiyori** is the repository and Gradle root-project name.
- The project repository is `https://github.com/Kiyori-CN/Kiyori`.
- The Android application ID is `com.kiyori`.

## Product position and ownership

- **Kiyori** is an all-purpose browser powered by Operit AI. Web access and content handling are the product center; video, music, reading, downloads, files, ad blocking, and later content capabilities are browser-owned domains.
- **Operit AI** is Kiyori's embedded AI subsystem. It owns AI conversation and AI-specific runtime capabilities, but it does not own the Kiyori application shell, global navigation, product identity, or system-wide settings.
- Every Kiyori capability may provide a human-facing page and an AI-facing capability contract. AI integrations call those contracts instead of directly driving Activities, Composables, or ViewModels.
- Kiyori has never had a user-facing release. The inherited Operit navigation drawer is not a published Kiyori interface, so the product-shell migration removes that design instead of maintaining a parallel legacy route.

## UI design sources

- **Operit Original UI** is the visual-language authority for Kiyori. New pages use its theme tokens, typography, shapes, component treatment, icon behavior, dialogs, settings rows, glass effects, motion, and interaction density so AI and non-AI surfaces form one product.
- **Legacy Kiyori Design Reference** is `Kiyori-CN/kiyori-android@24a2dfa91f0a4166dc58e5c4732d11861173f766`. It provides the page composition, navigation intent, browser behavior, and feature layout for Kiyori-owned surfaces.
- When the two sources differ, an accepted Kiyori product contract determines behavior, Operit Original UI determines visual treatment, and Legacy Kiyori Design Reference determines the Kiyori page's content structure.
- Source references are design and migration evidence, not runtime dependencies. New hard-coded visual systems must not be introduced beside the Operit theme.

## Product shell language

| Term | Definition |
| --- | --- |
| **Kiyori App Shell** | The Kiyori-owned root navigation, adaptive layout, window-inset, Back, and top-level state boundary. Operit AI is hosted inside this shell. |
| **Software Home** | The default center page shown after launch and the first of five primary destinations. It owns the three-page horizontal home space. |
| **Minus-One Page** | The page immediately left of Software Home. It is part of the home pager and hides the five-item bottom navigation. |
| **AI Home** | The full-screen Operit AI conversation page immediately right of Software Home. It hides the five-item bottom navigation and has no edge-drawer gesture. |
| **AI Center** | A Kiyori full-screen AI navigation destination opened from the hamburger button on AI Home. It replaces the inherited Operit drawer as a page, not as an overlay. |
| **AI Settings** | The settings owned specifically by the AI subsystem. AI Center and Kiyori Settings both navigate to this single settings destination. |
| **Permission Center** | A Kiyori system-level read-only overview and navigation destination that separates device capabilities from AI tool authorization. Its overview contains no direct permission switches and does not own another copy of either domain's persisted state. |
| **Device Capability Authorization** | Android and privileged-execution capabilities granted to Kiyori itself, including runtime permissions, file access, overlay, accessibility, battery exemption, Shizuku, Root, and debug capability. It is distinct from permission to invoke an AI tool. |
| **AI Tool Authorization** | The Operit AI policy that decides whether a registered tool is allowed, requires confirmation, or is forbidden. `ToolPermissionSystem` remains its single persisted source of truth. |
| **Software Home Search Card** | The central Software Home input surface with separate Search and AI buttons. Search opens Full-Screen Web Search; AI moves the home pager to AI Home. |
| **Full-Screen Web Search** | The web-only search page opened by the Search button in the Software Home Search Card. It does not search bookmarks, browser history, files, mini apps, or AI conversations; those domains own their search pages. |
| **Kiyori Capability API** | UI-independent contracts through which Operit AI observes and operates Kiyori browser, media, reader, file, download, and later domains. |
| **High-Impact AI Action** | An AI-requested operation with destructive, privacy, financial, external-communication, account, permission, or persistent-state impact. It requires explicit authorization and an inspectable operation record. |

## Navigation contract

- App launch opens the center Software Home page.
- The Software Home horizontal order is Minus-One Page, Software Home, then AI Home.
- The five primary destinations are Software Home, Browser Home, Mini App Home, File Management Home, and Settings Home.
- The five-item bottom navigation is visible only on those five root pages. It is hidden on Minus-One Page, AI Home, Full-Screen Web Search, child pages, and immersive content pages.
- The hamburger button on AI Home opens AI Center. AI Settings is a child destination of AI Center and is also reachable from Settings Home.
- The Permission quick action in AI Center and the permissions entry in Settings Home open the same Permission Center route. Permission Center separates device capabilities from AI tool authorization and uses each domain's single owner.
- The AI Center Permission quick action keeps the short Permission label and Operit quick-card treatment. Its badge shows only the dependency-aware count of actionable device-capability issues, or Normal when that count is zero; AI tool policy is not aggregated into this badge.
- Conversation history, new-chat, search, and delete actions remain in the original AI Home history selector. They are not duplicated in AI Center.
- AI Center does not contain an AI Dialogue entry. Its page Back action and system Back return to the existing AI Home conversation.
- The Terminal button remains in the upper-right AI Home toolbar during the product-shell migration. This plan does not add another Terminal entry.
- Opening AI Center never translates, scales, tilts, rounds, shadows, or otherwise transforms AI Home. Drawer-coupled content perspective is not part of Kiyori navigation.
- AI Center follows the shared Operit theme tokens. The removed top-level drawer's dedicated glass, background-color, and accent-color preferences are not AI Center settings and are not retained as inactive options.
- Back from AI Center returns to AI Home. Back from AI Home or Minus-One Page returns to Software Home. Back at a child page returns to its owning root; Back at a non-Software root returns to Software Home. Back at the center Software Home requests exit confirmation.
- Each primary destination retains its own child stack and scroll state while the user switches roots.
- Compact windows present AI Center as a full-screen page and push child destinations full-screen. Medium and expanded windows show the AI Center navigation list and child detail side by side inside the same route. Window-size and fold-posture changes preserve the current child route, form state, and scroll state; foldable layouts respect separating hinges and do not split interactive content across them.
- Detailed ownership, gesture, adaptive-layout, and migration rules are defined in `docs/doc-src/architecture/kiyori_product_shell_and_navigation.md`.

## Settings ownership

- Kiyori system settings own application language, product-wide appearance, storage, downloads, notifications, privacy, shared permissions, browser, player, reader, file management, and other cross-product behavior.
- AI Settings own model providers, model routing, prompts, personas, AI memory and context, AI tool authorization, MCP, Skill, ToolPkg, workflows, conversation policy, and AI usage information.
- A setting has one owner and one persisted source of truth. Other pages may deep-link to that owner but do not duplicate its state.
- Permission Center is a coordination surface, not another settings owner. Device-capability controls remain in Kiyori system security settings, and AI tool policy remains in the existing Tool Permission destination backed by `ToolPermissionSystem`.
- Permission Center groups High-Impact Action policy and AI Operation Records under one AI Security section. Neither entry is shown until its complete policy or record page exists.
- Backup, conversation-data management, token statistics, and the Toolbox utility that changes permissions of other installed applications are not Permission Center domains.

## AI action authorization

- Risk is assigned to a concrete command and its target, scope, reversibility, data sensitivity, and external impact. A tool name alone never determines risk.
- **R0 Read-Only** operations do not change local or external state and need no operation confirmation after the tool gate permits them.
- **R1 Low-Impact** operations are local, bounded, and easy to reverse or stop. Persistent tool authorization may permit them without per-operation confirmation.
- **R2 High-Impact** operations change persistent state, access sensitive data, affect multiple objects, or prepare external impact. They require one-time confirmation by default; an explicit session grant is allowed only for a fixed target and scope.
- **R3 Critical** operations are difficult to reverse, privileged, financial, account-sensitive, or create an external action that cannot be recalled. Every operation requires confirmation; session and persistent bypass are prohibited.
- `FORBID`, `ASK`, and `ALLOW` control whether a tool may be invoked. The R0-R3 contract separately controls whether the concrete operation may produce side effects. `ALLOW` cannot bypass R2 or R3 confirmation.
- When `ASK` and an R2 or R3 confirmation apply to the same command, Kiyori presents one combined authorization decision rather than consecutive prompts.

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

## Development identity and migration

- The continuous-development branch is `main`; parent and `terminal` remotes publish Kiyori changes only to their respective `main` branches.
- The `terminal` directory is the `KiyoriTerminalCore` submodule and is pinned by a parent gitlink.
- `com.kiyori` is a new Android application identity. It cannot in-place upgrade `com.ai.assistance.operit`; users must export and import supported backups.
- `Download/Operit` paths and inherited backup/protocol identifiers remain data-compatibility boundaries even when visible text says Kiyori.
- Root JavaScript metadata is private development tooling (`kiyori-tooling`), not a runtime package or public npm contract.
