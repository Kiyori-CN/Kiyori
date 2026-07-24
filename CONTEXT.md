# Kiyori shared context

This file defines the stable product language and compatibility boundaries for this repository.
Current work status and implementation notes belong in `docs/TODO/`.

## Product identity

- **Kiyori** is the brand.
- **Kiyori** is the Android application and user-visible product name.
- **Kiyori** is the repository and Gradle root-project name.
- The project repository is `https://github.com/Kiyori-CN/Kiyori`.
- The Android application ID is `com.kiyori`.
- Android system surfaces, the default assistant persona, Kiyori-owned file-provider titles, WebChat, generated workspace display text, and Kiyori-owned companion UI use the Kiyori brand.

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
| **Software Home** | The default center page shown after launch and the first of five primary destinations. It owns the three-page horizontal home space and uses compact, medium, and expanded layouts at the `<600dp`, `600-839dp`, and `>=840dp` width gates. |
| **Minus-One Page** | The page immediately left of Software Home. It is part of the home pager and hides the five-item bottom navigation. |
| **AI Home** | The full-screen Operit AI conversation page immediately right of Software Home. It hides the five-item bottom navigation, shares Software Home's pager state and fling physics, and has no edge-drawer gesture. |
| **Browser Runtime** | The single in-process WebSession state owner shared by Browser Home, the browser overlay, and Operit AI browser tools. |
| **WebSession Profile** | The immutable website-data boundary of a WebSession: `normal` uses the default WebView profile, while `incognito` uses the Kiyori AndroidX WebKit private profile shared only by live incognito windows and deleted after the last one closes. |
| **Browser Home** | The immersive Kiyori browser root. It hides the five-item Kiyori bottom navigation and presents the shared Browser Runtime through browser-owned top-bar search, full-screen search, normal/incognito window overview, fixed browser-menu drawer, draggable child drawers, and bottom-bar chrome. |
| **Browser Presentation Owner** | The exclusive host currently mounting the active browser WebView: either `APP_SHELL` or `OVERLAY`. Ownership changes reattach the same View and never navigate or reload it. |
| **Modal AI Drawer** | The Kiyori-owned, button-triggered left overlay that exposes AI top-level navigation without moving or rebuilding the page below it. Its scrim covers the window while its panel begins below the status bar. It has no edge or drag gesture. |
| **AI Top-Level Page** | AI Home, Packages, Permission Grant, Workflow, Assistant Configuration, Memory, Toolbox, a ToolPkg drawer destination, or AI Settings when entered from the Modal AI Drawer. Native roots retain independent state and child stacks; ToolPkg roots retain them only when their route declares `keepAlive=true`. |
| **AI Settings** | The settings owned specifically by the AI subsystem. Modal AI Drawer and Kiyori Settings both navigate to this single settings destination while preserving their distinct Back origins. |
| **AI Navigation Root** | An AI top-level route explicitly identified by its registered navigation-entry ID. Root ownership is stored on the route entry itself and is never inferred from route arguments, stack depth, or an instance-ID naming convention. |
| **Kiyori Edge-to-Edge Shell** | The window contract in which Kiyori-owned page backgrounds and the Modal AI Drawer scrim paint behind a transparent status bar. Interactive page content applies system insets, while the drawer panel itself starts below the status bar. |
| **Permission Center** | A Kiyori system-level read-only overview and navigation destination that separates device capabilities from AI tool authorization. Its overview contains no direct permission switches and does not own another copy of either domain's persisted state. |
| **Device Capability Authorization** | Android and privileged-execution capabilities granted to Kiyori itself, including runtime permissions, file access, overlay, accessibility, battery exemption, Shizuku, Root, and debug capability. It is distinct from permission to invoke an AI tool. |
| **AI Tool Authorization** | The Operit AI policy that decides whether a registered tool is allowed, requires confirmation, or is forbidden. `ToolPermissionSystem` remains its single persisted source of truth. |
| **Software Home Search Card** | The central Software Home input surface with separate Search and AI buttons. Search opens Full-Screen Web Search; AI moves the home pager to AI Home. |
| **Full-Screen Web Search** | The web-only search page opened by the Search button in the Software Home Search Card. It shares `WebSessionHistoryStore` search-engine and recent-record state with Browser Home, exposes the Browser Runtime default normal/incognito Profile, and always creates and activates a new shared WebSession instead of navigating the active tab. It does not search bookmarks, browser history, files, mini apps, or AI conversations; those domains own their search pages. |
| **Kiyori Capability API** | UI-independent contracts through which Operit AI observes and operates Kiyori browser, media, reader, file, download, and later domains. |
| **High-Impact AI Action** | An AI-requested operation with destructive, privacy, financial, external-communication, account, permission, or persistent-state impact. It requires explicit authorization and an inspectable operation record. |

## Navigation contract

- App launch opens the center Software Home page.
- The Software Home horizontal order is Minus-One Page, Software Home, then AI Home.
- All three home pages use one `PagerState` and one fling behavior. Movement follows the pointer, and a new reverse drag can cancel an unfinished fling; Shell state changes only after the pager settles.
- The five primary destinations are Software Home, Browser Home, Mini App Home, File Management Home, and Settings Home.
- The five-item bottom navigation is visible on Software Home, Mini App Home, File Management Home, and Settings Home. Browser Home is an immersive root with its own bottom bar; Minus-One Page, AI Home, Full-Screen Web Search, child pages, and immersive content pages also hide the Kiyori bottom navigation.
- Browser Home and the browser overlay are presentations of one Browser Runtime. Tabs, the active WebView, history, bookmarks, downloads, and userscripts are not copied between them. Website data is shared only inside each WebSession Profile: normal and incognito CookieManager, WebStorage, userscript Cookie/XHR, and download headers remain separated. The expanded overlay paints through the status-bar and display-cutout bounds while browser chrome applies content insets. Android 13+ routes system Back through the overlay `OnBackInvokedDispatcher`; Android 12 and earlier route `KEYCODE_BACK` from the overlay ViewRoot. Both inputs call the same browser Back state machine. When the overlay is expanded, system Back and the top-left browser return minimize it to the floating indicator before webpage history is considered. Browser Home entered from AI Home records that source and returns to AI Home while restoring the indicator.
- Operit AI discovers human-created browser state through `browser_tabs list`. The list enumerates the same Browser Runtime used by Browser Home and the overlay, exposes each stable `session_id`, `profile`, title, URL, index, and active state, and requires the intended tab to be selected before `browser_snapshot`, click, type, or navigation operations. `browser_tabs create` accepts `profile=normal|incognito`; omission uses the Browser Runtime default Profile. Incognito isolates website data, not Kiyori AI actions already authorized by the user.
- Browser Home's top bar is ordered Back, Search, and Refresh/Stop. The top-left action exits Browser Home according to its recorded Shell source; webpage history is owned by the bottom-bar Back and AI `browser_navigate_back`. Search opens a full-screen web search overlay without recreating the active WebView; its engine selection and recent records are browser-owned state. The bottom bar is ordered Back, Forward, Home, Tabs, and Browser Menu. Browser Menu opens a fixed-height, edge-to-edge four-row menu; history, bookmarks, downloads, userscripts, user-agent mode, network logs, page source, and capability explanation pages open draggable child drawers. The menu's Incognito action opens the real window overview, AI Dialogue returns to AI Home, and Exit Browser removes the presentation layer while retaining sessions. The window overview has only normal/incognito selectors at the top, uses 2/3/4 columns, filters the one WebSession registry by immutable Profile, and displays bounded in-memory thumbnails captured after a committed visual state. New and clear actions affect only the selected Profile. Unsupported Multi-Profile or failed private-profile cleanup disables incognito explicitly and never substitutes a normal window.
- Software Home's Search action opens Full-Screen Web Search above the existing home pager without rebuilding Software Home or AI Home. The input is focused on entry; Back closes an open search-engine panel before leaving the page. Blank input creates no state. Its incognito button changes the Browser Runtime default new-window Profile when AndroidX Multi-Profile is available. `BrowserAddressResolver` resolves a submitted address or query with the selected engine, `BrowserPresentationCoordinator.openUrlInNewSession` creates and activates a new shared WebSession with that Profile, and the Shell then enters Browser Home with Software Home as its return target. Search records are written to the same `WebSessionHistoryStore` observed by Browser Home. Because the session is created through the shared Browser Runtime, `browser_tabs list` exposes it to AI without a second registry or UI-specific adapter.
- AI Home and every explicitly identified AI Navigation Root show the hamburger button, including roots opened by shortcuts, widgets, raw routes, or router-gateway requests. AI deep pages show Back until navigation reaches an explicit root. Browser, Mini App, File Management, and Kiyori Settings pages do not expose the drawer button.
- Host navigation roots match their registered route ID. ToolPkg plugin roots match both route ID and registered route arguments. Route arguments, stack depth, and instance-ID prefixes never establish root ownership.
- The drawer contains Packages, Permission Grant, Workflow, AI Dialogue, Assistant Configuration, Memory, Toolbox, ToolPkg dynamic destinations, and AI Settings. AI Dialogue returns to the existing AI Home without creating a conversation or clearing its draft.
- AI Home always renders the conversation surface and input controls, even when no usable model configuration or API key exists. Model configuration is opened as a separate settings route; missing credentials are reported when the user attempts model-dependent work, never by replacing or trapping the AI Home.
- The Permission Grant quick action opens `Screen.ShizukuCommands`. The permissions entry in Settings Home opens Permission Center, while AI Tool Authorization continues to use `ToolPermissionSystem`; these three routes do not share or copy persisted state.
- The Permission Grant quick action keeps the short Permission label and Operit quick-card treatment.
- Conversation history, new-chat, search, and delete actions remain in the original AI Home history selector. They are not duplicated in the drawer.
- The Terminal button remains in the upper-right AI Home toolbar during the product-shell migration. This plan does not add another Terminal entry.
- Opening the Modal AI Drawer never translates, scales, tilts, rounds, shadows, fades, or otherwise transforms the page below it. The page continues rendering but cannot receive touch while the drawer is visible.
- The Modal AI Drawer follows shared Operit theme tokens. Dedicated drawer glass, background-color, and accent-color preferences are not restored.
- Back first closes an open drawer. Back from an AI Top-Level Page returns to AI Home, and Back from a deep AI page returns to its owning top-level page. Back from AI Home or Minus-One Page returns to Software Home; Back at a non-Software root returns to Software Home; Back at the center Software Home requests exit confirmation.
- Each primary destination retains its own child stack and scroll state while the user switches roots.
- Native AI roots use stable host instances. Every ToolPkg root entry creates a new route instance; only a ToolPkg route declaring `keepAlive=true` retains its composition key and saved child stack across drawer switches.
- AI Settings retains one page composition, form state, scroll state, and persisted settings. Re-entry from the same source family may restore its child stack; crossing between the AI drawer family and Kiyori Settings always opens the AI Settings root so the visible Back contract matches the current source.
- The Modal AI Drawer is `75%` of window width below `600dp`, `320dp` from `600dp` through `839dp`, and `360dp` from `840dp`. A separating hinge caps it to the left physical region. Window-size and fold-posture changes preserve the current route, form state, scroll state, and AI Home lifecycle.
- Detailed ownership, gesture, adaptive-layout, and migration rules are defined in `docs/doc-src/architecture/kiyori_product_shell_and_navigation.md`.

## System bar contract

- The Kiyori App Shell uses one edge-to-edge status-bar policy for Software Home, Minus-One Page, AI Home, all five primary destinations, and AI pages. Page backgrounds paint to the physical top edge. The Modal AI Drawer's full-window scrim follows that policy, while the drawer panel is an explicit exception whose top edge starts at the status-bar bottom; panel content applies only horizontal and bottom safe insets.
- The status bar is transparent whenever it is visible, and platform contrast scrims are disabled where the Android API supports that control. Status-bar icon brightness follows the actual foreground surface presented by the Kiyori Shell.
- The inherited transparent-status-bar and custom-status-bar-color controls and persisted preferences do not exist in Kiyori. The user may still hide or show the status bar.

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
- `.operit/config.json`, `com.operit.*` ToolPkg IDs, `remote_operit`, `operit-pc-agent` filenames and directories, and `OPERIT_*` environment variables;
- attribution, license history, source references, and upstream documentation that must continue to identify Operit accurately.

Changing one of these identifiers requires a separate compatibility design and migration plan.

Kiyori's user-visible `versionName` and the **Operit Market Compatibility Version** are independent version axes. `versionName` identifies the Kiyori product release. `BuildConfig.OPERIT_MARKET_COMPAT_VERSION` is a non-user-facing semantic version that identifies the inherited Operit script and ToolPkg runtime contract used for market `minAppVer` and `maxAppVer` checks. Its current value is `1.12.0+4`, matching the Operit source compatibility baseline; it changes only when Kiyori adopts a different market runtime contract.

## Development identity and migration

- The continuous-development branch is `main`; parent and `terminal` remotes publish Kiyori changes only to their respective `main` branches.
- The `terminal` directory is the `KiyoriTerminalCore` submodule and is pinned by a parent gitlink.
- `com.kiyori` is a new Android application identity. It cannot in-place upgrade `com.ai.assistance.operit`; users must export and import supported backups.
- Kiyori creates public runtime data under `Download/Kiyori`. An old Operit backup may be selected through an explicit import flow, but Kiyori does not automatically scan, merge, migrate, or delete `Download/Operit`.
- Current-app sandbox references use `/data/data/com.kiyori`; source namespaces and external action or provider contracts continue using their established compatibility identifiers.
- Terminal keeps `installed-rootfs/ubuntu`, `.operit_installed_ok`, its existing internal mount variables, and native filenames. Only the file-provider title and generated package-source comments use the Kiyori brand.
- Inherited backup formats, market wire types, plugin IDs, ToolPkg IDs, MCP IDs, namespaces, and protocol identifiers remain interoperability boundaries even when visible text says Kiyori.
- Root JavaScript metadata is private development tooling (`kiyori-tooling`), not a runtime package or public npm contract.
