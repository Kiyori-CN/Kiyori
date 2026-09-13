# 身份与兼容边界

## 调查结果

- 用户看到的 Operit 图形由 `terminal/.../OutputProcessor.kt` 在会话进入 READY 后绘制，不来自 Ubuntu rootfs
- rootfs 共检查 12684 个成员；文件名没有 Kiyori 或 Operit 项目品牌，命中二进制本地化词条的子串不属于产品标识
- `/etc/os-release` 与 `/etc/issue.net` 正确声明 Ubuntu 24.04.1 LTS，`root/.bashrc` 和 `root/.profile` 是标准 Ubuntu 配置
- `root@localhost` 是中性的容器主机提示，不需要改造成产品品牌

## 修改意图

- 使用紧凑 Kiyori ASCII 横幅替换旧 Operit 图形
- 将说明行改为 `Kiyori Ubuntu environment on Android`
- 保持 Ubuntu 发行版身份、rootfs 内容、系统目录和 hostname 不变

## 不修改的实现标识

- namespace、AIDL、源码目录和 JNI 符号
- `.operit_installed_ok` 与 `OPERIT_*`
- `liboperit_proot.so`、`liboperit_loader.so` 与现有 chroot/隐藏命令 marker
- `installed-rootfs/ubuntu` 和现有挂载路径

这些标识没有用户可见品牌收益，改名会扩大兼容风险、破坏既有安装识别或增加无意义的上游合并冲突。

## 状态 [DONE]

欢迎横幅已改为最大行宽不超过 48 个字符的 Kiyori ASCII 标识，Ubuntu rootfs 与兼容路径保持不变。
