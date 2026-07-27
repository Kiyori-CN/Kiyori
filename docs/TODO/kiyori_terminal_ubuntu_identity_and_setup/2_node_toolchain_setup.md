# Node 工具链首装契约

## 原因

现有命令先用 npm 安装 pnpm，再让 pnpm 在独立全局目录安装 TypeScript。报错中的 `/root/.local/share/pnpm/bin` 不在终端固定 `PATH`，因此 TypeScript 安装失败。由于后续检查只观察 pnpm，界面仍可能误报成功。

## 实施设计

- 在 `terminal` 模块建立父应用可复用的 Node 工具链契约
- 固定顺序为 npm 源配置、npm 缓存清理、由 npm 一次全局安装 pnpm 与 TypeScript
- 就绪命令要求 Node.js 主版本至少为 24，通过 `npm prefix -g` 解析安装时使用的全局 bin，并从该绝对路径实际执行 `pnpm --version` 和 `tsc --version`；三项全部成功时才输出唯一完成 marker
- 安装页和 Kiyori 父应用都使用同一命令与结果解析，删除两套易漂移的判定

## 边界

- 可见终端使用交互式 login Bash，AI 后台命令使用 `--noprofile --norc`；检测直接解析 npm 全局 bin，不依赖两种会话是否加载了相同的用户 shell 配置
- 不改变 NodeSource、npm 镜像或已选的 Node.js 24 主版本
- 不增加失败后继续执行、替代包管理器或静默忽略错误的逻辑

## 状态 [DONE]

安装页与父应用已共用 `TerminalEnvironmentContract`。npm 在同一全局 bin 中安装 pnpm 与 TypeScript；就绪检查要求 Node.js 主版本至少为 24，并从 `npm prefix -g` 对应的 bin 执行 `pnpm` 与 `tsc`。
