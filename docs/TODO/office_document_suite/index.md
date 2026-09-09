# 办公文档套件专项

状态：进行中（第一期基础闭环已实现，完整格式引擎和设备验收待完成）。

提供 `com.kiyori.office_suite` ToolPkg、可导入 Skill 和 Linux Python 运行时，采用 sentinel JSON 协议，先交付环境检查、读取、路径安全与预算控制。

SkillManager 当前仅扫描用户 `Kiyori/skills`，未发现 APK 内置 Skill 扫描入口，因此 Skill 以 `examples/office_suite/skills/` 可导入目录交付。ToolPkg 由 `tools/example_packages/packages_whitelist.txt` 驱动。

不修改 `terminal`，不新增宿主 API，不在 QuickJS 解析 OOXML/PDF；十条真机用例保持 `verification_pending`。
