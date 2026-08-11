# 制品流水线与安装事务

## Manifest schema v2

```json
{
  "schema_version": 2,
  "toolpkg_id": "com.example.demo",
  "version": "1.0.0",
  "main": "main.js",
  "distribution": {
    "include": [
      "manifest.json",
      "main.js",
      "packages/**/*.js",
      "ui/**/*.js",
      "resources/**",
      "modules/**/*.wasm",
      "i18n/**/*.js"
    ]
  }
}
```

schema v2 制品只能包含 `distribution.include` 命中的条目。manifest、main、subpackage entry、
resource、WASM、UI screen 和模板声明引用的文件必须在 include 内。

schema v1 继续作为生态兼容格式接受扫描；Kiyori 自有 builder 只选择运行所需目录与声明文件，不把
整个工作区递归放入制品。

## 永久拒绝项

即使 include 命中，以下内容也不能进入制品：

```text
.git/
.backup/
.history/
__pycache__/
node_modules/
.gradle/
.idea/
.env
.env.*
*.pem
*.key
*.p12
*.pfx
*.jks
*.keystore
*.swp
*.tmp
```

活动 JavaScript、TypeScript、JSON、HJSON 和 UI 模块中不得出现：

- `/sdcard/Download/Operit`
- `/storage/emulated/<user>/Download/Operit`
- 固定 `/data/user/<id>/<applicationId>`
- 固定 `/sdcard/Android/data/<applicationId>`

旧路径只允许出现在专用迁移元数据、迁移说明文档和 scanner 测试 fixture。

## 初始预算

| 项目 | 上限 |
| --- | ---: |
| `.toolpkg` 压缩文件 | 256 MiB |
| 文件条目 | 4096 |
| 总解压大小 | 512 MiB |
| manifest | 1 MiB |
| 单个 JS/TS/JSON/HJSON/HTML/CSS | 4 MiB |
| 单个普通资源 | 128 MiB |
| 最大目录深度 | 32 |
| 规范化路径长度 | 240 字符 |
| 单条目压缩比 | 200:1 |

超过预算的资源使用专门的外部资产机制，不扩大所有 ToolPkg 的通用上限。

## Scanner 报告

每次扫描生成稳定报告，至少包含：

- scanner 版本
- artifact SHA-256
- ToolPkg ID 与版本
- archive bytes、entry count、unpacked bytes
- 规范化 tree digest
- finding code、级别、条目和消息

发现 error 时禁止构建提交、导入或激活。报告写入内部
`Kiyori/toolpkg-runtime/v1/audit/<sha256>.json`。

## 确定性 builder

builder：

1. 读取并验证 manifest
2. 按 schema 选择文件
3. 拒绝符号链接和越界 canonical path
4. 运行目录扫描
5. 按规范化路径排序
6. 固定 ZIP 条目时间戳和压缩参数
7. 写入 cache transaction
8. 重新打开制品并运行 archive scanner
9. 返回已验证制品路径与 SHA-256

`operit_editor` 不再调用通用 `Tools.Files.zip(source.folderPath, ...)`。

## 内容寻址与 active 记录

已验证制品写入：

```text
filesDir/Kiyori/toolpkg-runtime/v1/
├── artifacts/<sha256>.toolpkg
├── active/<package-key>.json
└── audit/<sha256>.json
```

active 记录包含 package ID、artifact SHA-256、版本和激活时间。ToolPkg cache signature 使用完整
artifact SHA-256，不依赖路径、长度和 mtime。

## 安装与更新事务

```text
下载到 cache staging
→ 校验市场 SHA-256
→ Scanner
→ manifest ID/version 校验
→ 写入内容寻址 artifacts
→ 使用隔离 registration engine 解析并注册验证
→ 写入 audit
→ 原子切换 active
→ 重建当前 package snapshot
→ 销毁旧 package engine
→ 清理无引用旧制品和 staging
```

提交 active 前的任何错误都不能改变当前激活版本。更新只切换制品，不删除 private data。

旧 external package 文件只在新 active 已提交且当前 snapshot 已验证后进入清理；事务失败时恢复原
文件位置和原 active 记录。市场 metadata 写入内部
`Kiyori/toolpkg-runtime/v1/market/<package-key>`，不再写入公开插件目录。
