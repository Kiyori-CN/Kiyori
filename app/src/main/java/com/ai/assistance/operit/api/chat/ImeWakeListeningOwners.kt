package com.ai.assistance.operit.api.chat

/** 主窗口与悬浮窗口独立释放自己的 IME 声明，不能恢复另一个窗口仍暂停的唤醒监听。 */
internal class ImeWakeListeningOwners {
    private val visibleOwners = mutableSetOf<Any>()

    @Synchronized
    fun setVisible(owner: Any, visible: Boolean): Boolean {
        if (visible) visibleOwners.add(owner) else visibleOwners.remove(owner)
        return visibleOwners.isNotEmpty()
    }

    @get:Synchronized
    val isVisible: Boolean get() = visibleOwners.isNotEmpty()
}
