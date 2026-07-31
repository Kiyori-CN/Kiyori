---
status: accepted_design
plan_version: 3
baseline: 62464b054f6de00b70c5596295bc216eb8edf63d
last_reviewed: 2026-07-31
---

# 工作区、基线与备份作战手册

## 唯一实施工作区

正式重构只在以下目录进行：

```text
D:\10_Project\Kiyori
```

不手工复制源码，不创建第二个长期开发目录，也不使用 Git worktree。这样能保证：

- `main` 与 `--require-main` 门禁保持真实
- Gradle、native、submodule、CI 和本机配置继续使用现有路径
- Git rename detection 不受目录副本干扰
- 不会出现两个可写工作区分别前进

仓库外只创建备份与临时恢复校验目录；它们不能用于继续开发。

## 备份目录结构

正式实施时使用：

```text
D:\10_Project\Kiyori-backups\
└── 2026-07-31-pre-refactor-<baseline-short-sha>\
    ├── manifests\
    │   ├── git-state.txt
    │   ├── backup-scope.txt
    │   ├── ignored-private-paths.txt
    │   └── sha256.txt
    ├── working-tree\
    │   ├── tracked.patch
    │   └── untracked\
    ├── private-config\
    ├── Kiyori-all.bundle
    ├── baseline-app-debug.apk
    └── restore-check\
```

目录名中的 commit 必须是开始备份时的实际 HEAD。`restore-check` 可以是同一备份根下的临时
目录，也可以使用系统临时目录；验证完成后是否保留由实施记录决定。

## 执行前冻结条件

开始 G-00 或 M-01 前必须同时满足：

- 方案 v3 已获得用户明确批准
- 当前工作区仍为 `D:\10_Project\Kiyori`
- branch、HEAD、`origin/main`、`upstream/main` 和 merge-base 已记录
- staged、unstaged、untracked 和 ignored 开发文件已分类
- 当前未提交方案文档已经保存仓库外副本和 hash
- terminal gitlink 干净且本轮排除
- tools/hotbuild 未初始化状态已记录
- 私密配置只按允许清单复制，且没有进入 Git 索引
- 原始基线安全分支已经建立
- Git bundle 已验证并通过临时克隆/`git fsck`
- 基线 APK 已复制且源/目标 hash 一致
- 没有来源不明的业务源码差异

任一条件不满足，只能停在准备阶段。

## 基线采集

以下命令在方案 v3 批准后执行：

```powershell
git status --short --branch
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git rev-parse upstream/main
git merge-base HEAD upstream/main
git rev-list --left-right --count HEAD...origin/main
git rev-list --left-right --count HEAD...upstream/main
git submodule status --recursive
git diff --name-status
git diff --cached --name-status
git ls-files --others --exclude-standard
git diff --check
```

ignored 文件不做全库枚举。只对允许清单中的本机开发配置执行 `Test-Path` 和
`git check-ignore -v`，避免扫描缓存和输出私密内容。

## 备份顺序

### 1. 保存当前未提交状态

在任何提交、分支或源码变更前：

- 使用 Git 自带 `--output` 生成 tracked binary patch
- 按 `git ls-files --others --exclude-standard` 的精确清单复制未跟踪方案文档
- 对 patch 和每个未跟踪文件计算 SHA-256
- 不复制 `build/`、`.gradle/`、`.venv/`、`work/` 或 native 缓存

### 2. 备份私有开发配置

允许清单只包含实际存在且被 Git 忽略的文件，例如：

```text
local.properties
.env
tools/github/.env
keystore.properties
signing.properties
```

不读取内容，不把它们加入公共 manifest，不复制未知私密文件。公开 manifest 只记录“已备份”
和相对路径；hash 放在备份目录内，不进入仓库。

### 3. 创建安全分支

```text
codex/kiyori-refactor-safety-<baseline-short-sha>
```

安全分支只指向原始基线 HEAD。已存在同名分支时验证指向，不覆盖；工作分支保持 `main`。

### 4. 完成 M-00 文档提交

把方案 v3、正式架构文档和相关 TODO 作为独立提交。M-00 不能包含 Application 或任何业务源码。

### 5. 创建与验证 Git bundle

```powershell
git bundle create "<backup-root>\Kiyori-all.bundle" --all
git bundle verify "<backup-root>\Kiyori-all.bundle"
```

随后从 bundle 克隆到 `restore-check`，执行：

```powershell
git fsck --full
git rev-parse main
git rev-parse codex/kiyori-refactor-safety-<baseline-short-sha>
git status --short --branch
```

临时恢复目录不初始化 terminal，不运行重构，不复制私有配置。

### 6. 保存基线 APK

运行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain
```

复制 APK 后验证：

- 文件大小和修改时间
- 源/目标 SHA-256
- package、version、ABI
- Debug V2 signature
- 16 KB zipalign
- `verifyDebugPlayerRuntimePackaging`

## 恢复顺序

发生异常时不覆盖当前目录：

1. 停止写入并记录 Git 状态。
2. 从 bundle 创建新的临时恢复目录。
3. checkout 原始安全分支或已验证里程碑。
4. 验证跟踪树和 submodule gitlink。
5. 先检查 tracked patch，再按精确相对路径恢复未跟踪文件。
6. 私有配置逐文件恢复，恢复前验证目标路径仍位于新目录内。
7. 构建并核验 APK。
8. 只有恢复副本完整后，才决定是否继续使用当前目录或切换到恢复副本。

禁止在恢复过程中使用 hard reset、clean、强制覆盖或递归复制整个项目目录。

## 备份完成标准

只有以下证据齐全才能称为“仓库备份已证明可恢复”：

- 当前工作树 patch 与未跟踪文件副本 hash 通过
- 私有配置允许清单已执行，未把内容写入日志或 Git
- 原始安全分支指向精确基线
- `git bundle verify` 通过
- 从 bundle 的临时克隆和 `git fsck --full` 通过
- 基线 APK 复制前后 hash 一致
- 备份范围、排除项和恢复步骤已记录

只创建文件但未完成临时克隆验证时，只能称为“已创建备份”，不能称为“已证明可恢复”。
