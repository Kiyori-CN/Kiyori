# 3. AI 快捷动作

状态：[DONE]

## 动作合同

一次性动作类型固定为：

- `OpenAttachments`：进入 AI Home 后展开现有完整附件面板。
- `StartVoiceSession`：复用 AI 输入区录音与悬浮窗权限流程，打开 `FloatingMode.FULLSCREEN`。
- `CapturePhoto`：复用现有相机权限、临时 URI 和 `TakePicture` 流程，成功后保留附件预览且不自动发送。

## 状态合同

- 每次点击创建唯一请求 ID，再将主导航重置为 AI Chat 根并移动共享 Pager。
- AI Home 到达 settled 且真实输入区可交互后消费动作。
- 重组、旋转和 Pager 动画不得重复消费同一请求。
- 用户取消或拒绝权限后，本次请求仍视为已消费并停留 AI Home。
- 普通 AI 按钮只导航到 AI Home，不登记快捷动作。

## 验收

- 三个入口均只启动一次原有能力。
- 不复制 launcher、权限合同、附件 ViewModel 状态或语音 service 生命周期。
