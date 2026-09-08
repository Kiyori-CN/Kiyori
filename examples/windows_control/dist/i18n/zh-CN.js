"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.WINDOWS_SETUP_ZH_CN = void 0;
exports.WINDOWS_SETUP_ZH_CN = {
    title: "连接 Windows", subtitle: "让 AI 在你的电脑上整理文件、编辑内容和运行任务。",
    changed: "配置已修改，保存后重新验证连接。", operationFailed: "操作失败", hostUnavailable: "宿主配置接口不可用，请更新 Kiyori。",
    activationFailed: "Windows 工具未能启用，请在扩展中检查工具包状态。", notConfigured: "尚未验证", checking: "正在验证连接与认证…",
    invalidResponse: "电脑返回了无法识别的结果，请检查目标地址与代理配置。", connected: "已连接并通过认证", saved: "配置已保存。连接结果以上方状态为准。",
    invalidConfig: "请粘贴完整配置 JSON，地址和令牌必须是字符串。", failed: "连接或操作未完成", connection: "电脑连接",
    addressHelp: "局域网填写电脑 IP 与端口；FRP 填写外网地址。HTTPS 未写端口时使用 443，可包含代理路径前缀。",
    address: "连接地址", token: "访问令牌", hideToken: "隐藏令牌", showToken: "显示令牌", hideAdvanced: "收起高级设置", advanced: "高级设置",
    timeout: "请求超时（毫秒）", save: "保存并连接", recheck: "验证已保存的连接", hideImport: "收起配置导入", import: "从电脑导入配置",
    importHelp: "在电脑管理页面复制连接配置后粘贴。配置含访问令牌，导入后会清空此输入框。", config: "连接配置 JSON", applyImport: "导入并连接",
    hideSetup: "收起电脑端安装说明", setup: "准备电脑端", setupHelp: "1. 将电脑端压缩包发送到 Windows 并解压。\n2. 双击 kiyori_pc_agent.bat，按页面选择局域网或 FRP。\n3. 复制连接配置，在此导入。",
    export: "导出并分享电脑端", resourceMissing: "电脑端资源不存在，请重新安装工具包。", exported: "已打开电脑端分享。",
    networkHelp: "局域网：电脑监听局域网地址，并允许对应防火墙端口。\nFRP：只转发执行端口（默认 58321）；使用 HTTPS 入口或受保护的私有通道。管理页面仅在电脑本机打开。\n手机上的 127.0.0.1 指向手机自身。",
    usage: "交给 AI 的任务", usageHelp: "例如：先列出 D:\\Work 的文件，读取计划文档，按我的要求编辑，再移动到指定目录并核对结果。\n文件路径以电脑为准。移动与复制拒绝覆盖已有目标；传输中断后先检查结果，避免重复执行。"
};
