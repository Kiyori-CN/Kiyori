---
status: active
owner: Kiyori storage and ToolPkg runtime
updated: 2026-08-11
---

# Kiyori 存储路径与 ToolPkg 数据治理

本 TODO 是 Kiyori 存储域、ToolPkg 私有数据、制品构建、安装事务和旧数据显式导入的唯一进度入口。
它不改变 `com.ai.assistance.operit`、`com.operit.*`、`operit://`、`.operit/config.json`、
`OPERIT_DOWNLOAD_DIR`、ToolPkg/MCP ID 或市场 wire type 等兼容合同。

Kiyori 新建的公共数据只进入 `Download/Kiyori` 与 `Pictures/Kiyori`。应用不得自动扫描、复制、
合并或删除 `Download/Operit`；旧数据只能由用户通过 SAF 明确选择，并交给匹配的包专属迁移器。

## 文档树

```text
kiyori_storage_and_toolpkg_data_governance/
├── index.md
├── 1_storage_domains_and_paths.md
├── 2_toolpkg_storage_and_migration.md
├── 3_artifact_pipeline_and_install_transaction.md
└── 4_validation_and_rollout.md
```

## 目标

- 由 `KiyoriPaths` 唯一声明目录名称、层级和物理根
- 由 `KiyoriStorageService` 统一创建内部目录、包命名空间和事务目录
- 由 `KiyoriPublicStore` 唯一执行 Kiyori 自有 MediaStore 写入和公开文件命名
- 为 ToolPkg 提供宿主绑定身份的 private/cache API，不返回真实内部绝对路径
- 让 ToolPkg 构建、导入和市场安装执行同一组路径、预算、敏感内容与旧路径扫描规则
- 把已安装 ToolPkg 制品写入内部内容寻址存储，通过原子 active 记录切换版本
- 为旧公共数据提供显式、包专属、可审计、可中止的 generation 导入框架

## 非目标

- 不把兼容标识机械改成 Kiyori
- 不自动发现或读取 `Download/Operit`
- 不双写新旧目录
- 不在缺少包专属迁移器时复制未知数据
- 不在本轮修改第三方“记忆系统”发布包或宣称其已完成迁移
- 不安装 APK，不操作手机、模拟器或远端市场

## 实施状态

1. [DONE] 完成旧路径、用户可见保存位置、ToolPkg parser、构建器和市场安装链的只读审计
2. [DONE] 固化四域存储、包命名空间、制品预算、安装事务和显式迁移合同
3. [DONE] 实现统一路径投影、公共保存服务和 ToolPkg private/cache API
4. [DONE] 实现 ToolPkg 制品 scanner、确定性 builder、内容寻址 store 与 active 事务
5. [DONE] 统一现有导出、Markdown、分享图片和 Browser 下载消费者
6. [DONE] 完成定向测试、ARCH046、正式开发门禁和 Debug APK 构建核验
7. [PENDING] 目标 Android 设备路径、SAF、MediaStore、市场更新和旧数据导入验收

## 当前数据边界

```text
应用私有且不备份/
└── Kiyori/toolpkg/v1/<package-key>/
    ├── data/
    ├── generations/
    ├── active-generation.json
    └── migration-audit/

应用缓存/
└── Kiyori/
    ├── toolpkg/v1/<package-key>/
    └── toolpkg-build/<transaction-id>/

应用内部文件/
└── Kiyori/toolpkg-runtime/v1/
    ├── artifacts/<sha256>.toolpkg
    ├── extracted/<sha256>/
    ├── active/<package-key>.json
    ├── audit/<sha256>.json
    └── market/<package-key>/

共享下载/
└── Kiyori/
    ├── browser/downloads/
    ├── toolpkg/<package-key>/public/
    └── exports/
        ├── browser/
        ├── userscripts/
        ├── player/
        ├── toolbox/
        ├── ai-config/
        ├── backups/
        └── toolpkg/<package-key>/

共享图片/
└── Kiyori/
    ├── Markdown/
    ├── Shared/
    └── AI/
```

详细合同见：

- [存储域与路径](1_storage_domains_and_paths.md)
- [ToolPkg 存储与显式迁移](2_toolpkg_storage_and_migration.md)
- [制品流水线与安装事务](3_artifact_pipeline_and_install_transaction.md)
- [验证、上线与完成标准](4_validation_and_rollout.md)
