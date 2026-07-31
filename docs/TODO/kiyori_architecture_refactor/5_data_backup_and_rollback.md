---
status: accepted_design
plan_version: 3
last_reviewed: 2026-07-31
---

# 开发数据、备份与回滚

## 保护目标

本轮“开发数据”只指 `D:\10_Project\Kiyori` 当前项目文件夹的本机开发状态：

1. Git 跟踪源码、提交历史、refs 和父仓库记录的 gitlink
2. 当前已暂存、未暂存和未跟踪文件
3. Git 忽略但开发所需的本机私有配置
4. 可安装、可核验的基线 Debug APK

手机、模拟器、ADB 和已安装应用的运行数据明确不在本轮范围内。应用数据格式仍属于兼容合同，
由 [兼容合同与稳定标识清单](8_compatibility_contract_inventory.md) 管理，但不作为开始源码
重构的备份门禁。

## 当前仓库事实

- 当前分支：`main`
- 基线 HEAD：`62464b054f6de00b70c5596295bc216eb8edf63d`
- `origin/main` 与 HEAD 一致
- 当前工作树只有 `docs/TODO/README.md` 和本方案目录的文档差异
- `terminal` gitlink 为 `8d5c2c224317c0176c14520facbb202a8be5993f`，本轮排除
- `tools/hotbuild/OperitNightlyRelease` 保持未初始化
- 当前没有安全分支、Git bundle 或仓库外备份目录

正式实施前必须重新采集这些值，不能把本文快照当作永久事实。

## 六层本地保护

### B-00：只读状态清单

记录：

- branch、HEAD、`origin/main`、`upstream/main`、merge-base
- staged、unstaged、untracked 和 ignored 开发文件
- submodule/gitlink 状态
- 现有 APK 路径、大小、时间和 SHA-256
- 不纳入备份的缓存、生成物和大型目录

只记录文件路径和状态，不把私密配置内容写入日志或文档。

### B-01：未提交工作树快照

在创建任何提交前，把当前文档差异保存到仓库外：

- 跟踪文件使用 Git 二进制 patch
- 未跟踪方案文档按相对路径复制
- 每个文件记录 SHA-256
- 快照清单记录来源 HEAD 与生成时间

这样即使 M-00 提交尚未完成，当前方案也可独立恢复。

### B-02：基线安全分支

创建：

```text
codex/kiyori-refactor-safety-62464b05
```

规则：

- 只指向原始基线 `62464b05`
- 已存在同名分支时只验证指向，不覆盖
- 不切换工作分支
- 不推送

### B-03：M-00 文档提交

方案重新获批后，先把方案 v3、正式架构文档和相关 TODO 作为独立 M-00 提交。
它不包含应用源码变化。

M-00 提交让原本未跟踪的方案文档进入 Git 对象库；随后创建的 bundle 同时包含原始安全分支
和已接受方案。

### B-04：Git bundle

创建包含全部本地 refs 的 bundle，并执行：

```powershell
git bundle verify "<bundle>"
```

随后在仓库外的临时验证目录从 bundle 克隆，确认：

- 原始安全分支可解析
- `main` 与 M-00 文档提交可解析
- `git fsck` 通过
- checkout 后跟踪树与源仓库对应 commit 一致

临时验证目录只用于恢复证明，不作为重构工作区。

### B-05：本机私有开发配置

只复制明确允许且实际存在的 Git ignored 文件，例如：

```text
local.properties
.env
tools/github/.env
keystore.properties
signing.properties
```

规则：

- 先用 `git check-ignore` 证明文件确实被忽略
- 不递归复制未知目录
- 不读取或输出配置内容
- 不放入 Git bundle、提交或项目文档
- 备份目录保持在仓库外，不上传
- 恢复时逐文件复制，不整体覆盖项目

`app/src/main/assets/jks.jks` 与 `app/src/main/assets/pkcs12.keystore` 当前是已跟踪运行时资产，
由 Git bundle 保护，不能误当成本机私有签名材料重复处理。

### B-06：基线 APK

保存：

- `app-debug.apk`
- 文件大小与修改时间
- SHA-256
- package、version、ABI
- Debug 签名结果
- 16 KB zipalign 结果
- player/native packaging gate 结果

复制后的 APK 必须重新计算 SHA-256，并与源 APK 一致。

## 不复制的内容

不做整个项目目录的无差别压缩。默认排除：

```text
.git/
.gradle/
.gradle-local/
.venv/
node_modules/
**/build/
**/.cxx/
work/
terminal/ 工作树内容
未初始化的 tools/hotbuild/OperitNightlyRelease 内容
```

Git 历史由 bundle 保护，生成物可重建；仅保留经过核验的基线 APK。terminal gitlink 由父仓库
记录，但 terminal 内容本轮不复制、不初始化、不修改。

## 重构期间的数据冻结清单

以下内容在普通文件移动里程碑中不得改变：

- application ID
- 数据库名、表名、schema version 和 migration
- ObjectBox model 与 UID
- DataStore 文件名和 key
- SharedPreferences 文件名和 key
- WorkManager unique work name
- worker FQCN
- raw snapshot 结构和文件前缀
- `Download/Kiyori` 子目录名
- `operit://`
- Intent action、extra 和 provider authority
- ToolPkg、MCP、Skill 和 workflow wire value
- 序列化 `__type`

代码符号可以改名，但这些字符串必须通过静态清单验证保持不变。

## 回滚层级

### 单文件或单提交问题

使用新的反向提交恢复该里程碑，不改写已有提交历史。

### 多提交结构问题

从安全分支建立新的恢复分支，按已验证里程碑重新应用；不执行 hard reset、强制清理或覆盖用户文件。

### 仓库损坏

使用已验证 Git bundle 恢复 refs，并核对基线 commit。

### APK 或运行行为问题

重新安装保存的基线 Debug APK。是否能覆盖安装取决于 application ID、versionCode 和签名保持一致，
所以重构阶段禁止改变这些值。

### 当前工作树或配置问题

停止后续重构，在新的恢复目录从 bundle 重建跟踪树，再按清单恢复未提交文件和私有配置。
不得直接覆盖当前工作目录；先比较 hash 和路径，再决定是否切换到恢复副本。

## 停止条件

出现以下任一情况立即停止当前里程碑：

- 未提交工作树快照无法验证
- 私有配置路径来源不明或会被纳入 Git
- 安全分支未指向原始基线
- bundle verify、临时克隆或 `git fsck` 失败
- 基线 APK 复制前后 hash 不一致
- 数据文件或偏好名称发生非计划变化
- 旧 worker 无法实例化
- Android 系统入口丢失
- Browser 或 Player 出现第二状态 owner
- baseline APK 无法重新安装
- 用户工作树或私有配置被意外纳入差异

恢复和根因分析完成前，不继续下一批文件移动。
