const STORAGE_KEY = "kiyori.pc-agent.locale";

const RESOURCES = {
  en: {
    connection: {
      mode: "Connection method", lan: "Local network", frp: "FRP / remote access", publicUrl: "Phone-facing URL",
      lanHelp: "Keep phone and PC on a reachable network. Bind the execution service to the PC LAN IP and allow its port in Windows Firewall.",
      frpHelp: "When frpc runs on this PC, bind to 127.0.0.1 and forward execution port 58321. Use an HTTPS public endpoint or a protected private tunnel. Never forward the console port. The URL may include a proxy path prefix.",
      secretHint: "Token is hidden in this preview. Copy includes the full token; share only with your own Kiyori device.",
      clipboardFailed: "Clipboard unavailable. Enable browser clipboard permission.", tokenRequired: "Access token is required.",
      invalidUrl: "Enter a valid HTTP/HTTPS URL without credentials, query or fragment.", phoneLoopback: "The phone URL must point to the PC, not localhost.",
      restartFirst: "The listener is not applied. Return to step 1, choose a current network address and save again. No process restart is needed.",
      adapter: "Current network adapters", chooseAdapter: "Select an adapter or enter an address below", virtualAdapter: "Virtual / tunnel — verify reachability",
      proxyHelp: "A proxy/TUN adapter is present. For LAN, use the physical Wi-Fi/Ethernet address, not a Clash/Mihomo fake IP. System HTTP proxy and TUN routing are different; firewall and TUN rules may still affect the phone. This console uses loopback; do not expose it or change proxy settings to hide errors.",
      notApplied: "Not applied · saved {saved} · active {active} · {error}", applied: "Listening · {address} · local console stays unchanged",
      refreshRequired: "Connection state changed or is unavailable. Refresh the page and verify settings before copying."
    },
    language: {
      english: "English",
      chinese: "中文"
    },
    ui: {
      title: "Kiyori PC Agent Console",
      subtitle: "Your Windows workspace, connected to Kiyori"
    },
    nav: {
      wizard: "Setup Wizard",
      commands: "Commands",
      manage: "Manage",
      settings: "Settings"
    },
    action: {
      refreshAll: "Refresh All",
      refreshHealth: "Refresh Health",
      runPreset: "Run Preset",
      runRaw: "Run Raw",
      refreshSessions: "Refresh Sessions",
      createSession: "New Session",
      creating: "Creating...",
      closeSession: "Close",
      closing: "Closing...",
      sendInput: "Send",
      sendCtrlC: "Send Ctrl+C",
      sending: "Sending...",
      saveConfig: "Save Config",
      runOpenSshSetup: "Run Verification",
      saveAndNext: "Apply and Continue",
      generateMobileSnippet: "Generate Mobile Snippet",
      copyJson: "Copy JSON",
      copyEnv: "Copy ENV",
      oneClickFill: "One-click Fill",
      copyPayload: "Copy",
      toggleAdvancedShow: "Advanced",
      toggleAdvancedHide: "Hide Advanced",
      toCommands: "Go to Commands",
      toSettings: "Go to Settings",
      applyRecommendedBind: "Apply Current LAN Address",
      applyingRecommendedBind: "Applying and Restarting...",
      dismiss: "Dismiss",
      working: "Working...",
      running: "Running...",
      refreshing: "Refreshing..."
    },
    status: {
      host: "Host",
      lanIp: "LAN IPv4",
      pid: "PID",
      uptime: "Uptime",
      ssh: "Mode",
      version: "Version",
      unknown: "unknown",
      ready: "Ready",
      loading: "Loading config and health data...",
      healthRefreshed: "Health refreshed",
      healthRefreshFailed: "Health refresh failed: {error}",
      dataRefreshed: "Data refreshed",
      refreshFailed: "Refresh failed: {error}",
      initializationFailed: "Initialization failed: {error}"
    },
    startup: {
      title: "Startup Recovery",
      bindUnavailableMessage:
        "Listener needs attention. Saved address: {configuredBind}; active address: {runtimeBind}; current recommended IPv4: {recommendedBind}. Apply it or select an adapter in step 1. The local console remains available.",
      bindUnavailableMeta: "Detected IPv4 candidates: {ipv4Candidates}"
    },
    card: {
      healthTitle: "Health Status",
      healthSubtitle: "Agent runtime, bind target and HTTP relay status",
      commandTitle: "Command Runner",
      commandSubtitle: "Run preset commands or raw commands",
      manageTitle: "Session Management",
      manageSubtitle: "View all remote process sessions and close running ones",
      configTitle: "Configuration",
      configSubtitle: "Manage bind address, token, timeout and allowed presets",
      openSshTitle: "Verification",
      openSshSubtitle: "Use command presets to verify HTTP relay"
    },
    panel: {
      presetMode: "Preset Mode",
      rawMode: "Raw Mode",
      allowedPresets: "Allowed Presets"
    },
    wizard: {
      title: "Connect your phone",
      subtitle: "Choose a connection method, then import the configuration into Kiyori.",
      stepNav1: "1. Base Config",
      stepNav2: "2. Mobile Fill",
      step1Title: "Step 1: Make PC reachable from phone",
      step1Desc: "Choose how your phone reaches this PC, then save the connection settings.",
      step1Hint: "Save applies the listener in this process. The local console and terminal sessions stay open. A bind failure preserves the previous configuration and reports its cause.",
      step2Title: "Step 2: Mobile Paste Config",
      step2Desc: "Use one-click fill, then copy the config text to mobile app.",
      oneClickTitle: "Config Text",
      oneClickDesc: "Auto-fills available values and generates text for mobile paste.",
      advancedTitle: "Advanced override",
      mobileJsonTitle: "Configuration Text",
      mobileEnvTitle: "Configuration Text"
    },
    field: {
      token: "Token",
      preset: "Preset",
      shell: "Shell",
      command: "Command",
      pid: "PID",
      createdAt: "Created At",
      updatedAt: "Updated At",
      exitCode: "Exit Code",
      commandPreview: "Command",
      includeExitedSessions: "Include exited sessions",
      startShell: "Shell",
      startCommand: "Startup Command",
      currentSession: "Current Session",
      status: "Status",
      bindAddress: "Bind Address",
      port: "Port",
      maxCommandTimeoutMs: "Max Command Timeout (ms)",
      apiToken: "API Token",
      windowsAgentBaseUrl: "Connection URL",
      windowsAgentToken: "Access token",
      windowsAgentDefaultShell: "Default shell",
      windowsAgentTimeoutMs: "Request timeout (ms)"
    },
    placeholder: {
      tokenRequiredWhenApiTokenEnabled: "Required",
      rawCommandExample: "Example: Get-Process | Select-Object -First 5 Name,Id",
      apiTokenEmptyDisable: "Leave empty to auto-generate a new token",
      noPresetsAvailable: "(no presets available)",
      loadingPresets: "(loading...)",
      mobileHost: "LAN IP or reachable host, not 127.0.0.1 for phone",
      mobileBaseUrl: "Example: http://192.168.1.8:58321",
      passwordOrPrivateKey: "Provide password or private key",
      startCommand: "Optional. Leave empty to open plain shell",
      terminalInput: "Type command input and submit"
    },
    preset: {
      disabledSuffix: " (disabled)"
    },
    message: {
      configSaved: "Configuration saved",
      configSavedRestartRequired: "Settings are not active yet. Check the listener status and apply a current local address.",
      configSaveFailed: "Failed to save config: {error}",
      openSshFinished: "Verification finished",
      openSshWarn: "Verification returned warnings",
      openSshFailed: "Verification failed: {error}",
      openSshRequiresAdmin: "",
      openSshElevationCancelled: "",
      openSshBlockedByPolicy: "",
      presetFinished: "Preset command finished",
      presetWarn: "Preset command returned warnings",
      presetFailed: "Preset command failed: {error}",
      rawFinished: "Raw command finished",
      rawWarn: "Raw command returned warnings",
      rawFailed: "Raw command failed: {error}",
      sessionsRefreshed: "Session list refreshed ({count})",
      sessionsRefreshFailed: "Session list refresh failed: {error}",
      sessionCreated: "Session created: {sessionId}",
      sessionCreateFailed: "Session create failed: {error}",
      sessionClosed: "Session closed: {sessionId}",
      sessionCloseWarn: "Session close finished with warning: {sessionId}",
      sessionCloseFailed: "Session close failed: {error}",
      inputSent: "Input sent",
      inputSendFailed: "Input send failed: {error}",
      ctrlCSent: "Ctrl+C sent",
      terminalInitFailed: "Terminal init failed: {error}",
      terminalInteractive: "Interactive mode: type directly in terminal window",
      terminalReadonlyExited: "Session is not running, terminal is read-only",
      selectSessionFirst: "Select a session first",
      selectSessionHint: "Select a session from the left list to start interaction",
      terminalNoOutput: "(no output yet)",
      outputTruncated: "Earlier output was truncated by server buffer",
      noSessions: "No sessions",
      wizardStep1Done: "Step 1 completed",
      mobileSnippetGenerated: "Mobile snippet generated",
      oneClickFilled: "Auto fill completed",
      copyPayloadSuccess: "Copied",
      copyJsonSuccess: "Copied",
      copyEnvSuccess: "Copied",
      copyFailed: "Copy failed: {error}",
      startupApplyRestarting: "Listener applied to {bindAddress}. Console and sessions stay open.",
      startupApplyFailed: "Failed to apply recommended IPv4: {error}"
    },
    error: {
      unknown: "Unknown error"
    }
  },
  zh: {
    connection: {
      mode: "连接方式", lan: "局域网", frp: "FRP / 远程连接", publicUrl: "手机访问地址",
      lanHelp: "手机与电脑处于可互通网络。执行服务绑定电脑局域网 IP，并在 Windows 防火墙允许对应端口。",
      frpHelp: "frpc 在本机运行时，绑定 127.0.0.1 并转发执行端口 58321。手机填写 HTTPS 外网入口或受保护的私有通道地址，可包含代理路径前缀。不要转发管理页面端口。",
      secretHint: "预览已隐藏令牌；复制时会包含完整令牌，请仅交给自己的 Kiyori 设备。",
      clipboardFailed: "无法复制，请检查浏览器剪贴板权限。", tokenRequired: "访问令牌不能为空。",
      invalidUrl: "请输入有效 HTTP/HTTPS 地址，不含用户名、密码、查询串和片段。", phoneLoopback: "手机地址必须指向电脑，不能填写 localhost。",
      restartFirst: "监听尚未生效。请返回第一步选择当前网卡地址并保存应用，无需重启进程。",
      adapter: "当前网络适配器", chooseAdapter: "选择网卡，或在下方手动填写", virtualAdapter: "虚拟网卡 / 隧道，需确认可达性",
      proxyHelp: "检测到代理/TUN 网卡。局域网请使用真实 WLAN/以太网地址，不要复制 Clash/Mihomo 的虚拟 IP。系统 HTTP 代理与 TUN 路由是两回事；防火墙和 TUN 规则仍可能影响手机连接。管理页面走本机回环，不应外网映射，也不会自动改动代理配置。",
      notApplied: "尚未生效 · 保存 {saved} · 运行 {active} · {error}", applied: "正在监听 · {address} · 本机管理页面保持不变",
      refreshRequired: "连接状态已变化或暂不可用，请刷新页面并核对设置后再复制。"
    },
    language: {
      english: "English",
      chinese: "中文"
    },
    ui: {
      title: "Kiyori PC Agent 控制台",
      subtitle: "将你的 Windows 工作环境连接到 Kiyori"
    },
    nav: {
      wizard: "配置向导",
      commands: "命令执行",
      manage: "管理",
      settings: "高级设置"
    },
    action: {
      refreshAll: "刷新全部",
      refreshHealth: "刷新健康状态",
      runPreset: "运行预设",
      runRaw: "运行 Raw",
      refreshSessions: "刷新会话",
      createSession: "新建会话",
      creating: "创建中...",
      closeSession: "关闭",
      closing: "关闭中...",
      sendInput: "发送",
      sendCtrlC: "发送 Ctrl+C",
      sending: "发送中...",
      saveConfig: "保存配置",
      runOpenSshSetup: "执行连通验证",
      saveAndNext: "保存应用并继续",
      generateMobileSnippet: "生成移动端片段",
      copyJson: "复制",
      copyEnv: "复制",
      oneClickFill: "一键填写",
      copyPayload: "复制",
      toggleAdvancedShow: "高级配置",
      toggleAdvancedHide: "收起高级",
      toCommands: "前往命令页",
      toSettings: "前往设置页",
      applyRecommendedBind: "应用当前局域网地址",
      applyingRecommendedBind: "应用并重启中...",
      dismiss: "暂不处理",
      working: "处理中...",
      running: "执行中...",
      refreshing: "刷新中..."
    },
    status: {
      host: "主机",
      lanIp: "局域网IPv4",
      pid: "PID",
      uptime: "运行时长",
      ssh: "模式",
      version: "版本",
      unknown: "未知",
      ready: "就绪",
      loading: "正在加载配置与健康状态...",
      healthRefreshed: "健康状态已刷新",
      healthRefreshFailed: "健康状态刷新失败: {error}",
      dataRefreshed: "数据刷新完成",
      refreshFailed: "刷新失败: {error}",
      initializationFailed: "初始化失败: {error}"
    },
    startup: {
      title: "启动恢复",
      bindUnavailableMessage:
        "监听需要处理。保存地址：{configuredBind}；实际运行地址：{runtimeBind}；当前推荐 IPv4：{recommendedBind}。可应用推荐地址，或在第一步选择网卡。管理页面仍可使用。",
      bindUnavailableMeta: "检测到的 IPv4 候选: {ipv4Candidates}"
    },
    card: {
      healthTitle: "健康状态",
      healthSubtitle: "查看 Agent 运行状态、绑定信息与 HTTP 中转状态",
      commandTitle: "命令执行",
      commandSubtitle: "支持预设命令与 Raw 命令",
      manageTitle: "会话管理",
      manageSubtitle: "查看所有远程进程会话，并可关闭运行中的会话",
      configTitle: "配置中心",
      configSubtitle: "管理监听地址、令牌、超时与允许预设",
      openSshTitle: "连通验证",
      openSshSubtitle: "使用预设命令验证 HTTP 中转"
    },
    panel: {
      presetMode: "预设模式",
      rawMode: "Raw 模式",
      allowedPresets: "允许的预设"
    },
    wizard: {
      title: "连接手机",
      subtitle: "选择连接方式，将配置导入 Kiyori 后即可开始协作。",
      stepNav1: "1. 基础配置",
      stepNav2: "2. 移动端填写",
      step1Title: "步骤 1：让手机能访问这台电脑",
      step1Desc: "选择手机访问电脑的方式，再保存连接设置。",
      step1Hint: "保存后在当前进程内应用监听，管理页面和终端会话保持不变。绑定失败会保留原配置并说明原因。",
      step2Title: "步骤 2：移动端粘贴配置",
      step2Desc: "先一键填写，再复制配置文本到移动端粘贴。",
      oneClickTitle: "配置文本",
      oneClickDesc: "自动填充可获取项，并生成给移动端粘贴的配置文本。",
      advancedTitle: "高级覆盖",
      mobileJsonTitle: "配置文本",
      mobileEnvTitle: "配置文本"
    },
    field: {
      token: "令牌",
      preset: "预设",
      shell: "Shell",
      command: "命令",
      pid: "PID",
      createdAt: "创建时间",
      updatedAt: "更新时间",
      exitCode: "退出码",
      commandPreview: "命令预览",
      includeExitedSessions: "包含已退出会话",
      startShell: "Shell",
      startCommand: "启动命令",
      currentSession: "当前会话",
      status: "状态",
      bindAddress: "绑定地址",
      port: "端口",
      maxCommandTimeoutMs: "最大命令超时 (ms)",
      apiToken: "API 令牌",
      windowsAgentBaseUrl: "连接地址",
      windowsAgentToken: "访问令牌",
      windowsAgentDefaultShell: "默认 Shell",
      windowsAgentTimeoutMs: "请求超时（毫秒）"
    },
    placeholder: {
      tokenRequiredWhenApiTokenEnabled: "必填",
      rawCommandExample: "示例: Get-Process | Select-Object -First 5 Name,Id",
      apiTokenEmptyDisable: "留空会自动生成新令牌",
      noPresetsAvailable: "（暂无可用预设）",
      loadingPresets: "（加载中...）",
      mobileHost: "填写局域网 IP 或可达主机，不要给手机端写 127.0.0.1",
      mobileBaseUrl: "示例: http://192.168.1.8:58321",
      passwordOrPrivateKey: "填写密码或私钥其中之一",
      startCommand: "可选，不填则仅启动空 shell",
      terminalInput: "输入命令并发送"
    },
    preset: {
      disabledSuffix: "（已禁用）"
    },
    message: {
      configSaved: "配置保存成功",
      configSavedRestartRequired: "监听尚未生效，请核对运行状态，选择当前网卡地址后重新应用。",
      configSaveFailed: "配置保存失败: {error}",
      openSshFinished: "验证完成",
      openSshWarn: "验证返回告警",
      openSshFailed: "验证失败: {error}",
      openSshRequiresAdmin: "",
      openSshElevationCancelled: "",
      openSshBlockedByPolicy: "",
      presetFinished: "预设命令执行完成",
      presetWarn: "预设命令返回告警",
      presetFailed: "预设命令执行失败: {error}",
      rawFinished: "Raw 命令执行完成",
      rawWarn: "Raw 命令返回告警",
      rawFailed: "Raw 命令执行失败: {error}",
      sessionsRefreshed: "会话列表已刷新（{count}）",
      sessionsRefreshFailed: "会话列表刷新失败: {error}",
      sessionCreated: "会话已创建: {sessionId}",
      sessionCreateFailed: "会话创建失败: {error}",
      sessionClosed: "会话已关闭: {sessionId}",
      sessionCloseWarn: "会话关闭返回告警: {sessionId}",
      sessionCloseFailed: "会话关闭失败: {error}",
      inputSent: "输入已发送",
      inputSendFailed: "输入发送失败: {error}",
      ctrlCSent: "Ctrl+C 已发送",
      terminalInitFailed: "终端初始化失败: {error}",
      terminalInteractive: "交互模式：直接在终端窗口输入即可",
      terminalReadonlyExited: "会话未运行，当前终端为只读",
      selectSessionFirst: "请先选择会话",
      selectSessionHint: "请先在左侧选择会话，再进行交互",
      terminalNoOutput: "（暂无输出）",
      outputTruncated: "更早的输出已被服务端缓冲区截断",
      noSessions: "暂无会话",
      wizardStep1Done: "步骤 1 已完成",
      mobileSnippetGenerated: "移动端片段已生成",
      oneClickFilled: "一键填写完成",
      copyPayloadSuccess: "已复制",
      copyJsonSuccess: "已复制",
      copyEnvSuccess: "已复制",
      copyFailed: "复制失败: {error}",
      startupApplyRestarting: "已应用监听地址 {bindAddress}，管理页面和会话保持不变。",
      startupApplyFailed: "应用推荐 IPv4 失败: {error}"
    },
    error: {
      unknown: "未知错误"
    }
  }
};

function normalizeLocale(raw) {
  const value = String(raw || "").trim().toLowerCase();
  if (value.startsWith("zh")) {
    return "zh";
  }
  return "en";
}

function getByPath(target, path) {
  return String(path)
    .split(".")
    .reduce((current, key) => (current && Object.prototype.hasOwnProperty.call(current, key) ? current[key] : undefined), target);
}

function interpolate(template, values) {
  if (!values) {
    return template;
  }

  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (match, key) => {
    if (Object.prototype.hasOwnProperty.call(values, key)) {
      return String(values[key]);
    }
    return match;
  });
}

function readStoredLocale() {
  try {
    return window.localStorage.getItem(STORAGE_KEY) || "";
  } catch {
    return "";
  }
}

function writeStoredLocale(locale) {
  try {
    window.localStorage.setItem(STORAGE_KEY, locale);
  } catch {
    // ignore storage errors
  }
}

export function createI18n() {
  const browserLocale = typeof navigator !== "undefined" ? navigator.language : "en";
  let locale = normalizeLocale(readStoredLocale() || browserLocale);

  function t(key, values) {
    const primary = getByPath(RESOURCES[locale], key);
    const fallback = getByPath(RESOURCES.en, key);
    const text = primary !== undefined ? primary : fallback;
    if (typeof text === "string") {
      return interpolate(text, values);
    }
    return key;
  }

  function setLocale(nextLocale) {
    locale = normalizeLocale(nextLocale);
    writeStoredLocale(locale);
    return locale;
  }

  function getLocale() {
    return locale;
  }

  return {
    t,
    setLocale,
    getLocale
  };
}
