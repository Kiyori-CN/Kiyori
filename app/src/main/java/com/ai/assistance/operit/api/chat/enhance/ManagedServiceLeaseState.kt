package com.ai.assistance.operit.api.chat.enhance

/**
 * 单个模型服务实例的租约生命周期。
 *
 * 配置刷新只会把实例标记为 retired；只要仍有活跃租约，就必须允许当前请求继续使用该实例。
 * 最后一个租约归还后，调用方才可以释放实例。把状态转换集中在这里可以防止全量刷新和单功能
 * 刷新形成两套不一致的资源所有权规则。
 */
internal class ManagedServiceLeaseState {
    var activeLeases: Int = 0
        private set

    var retired: Boolean = false
        private set

    var released: Boolean = false
        private set

    fun acquire() {
        check(!retired) { "Cannot acquire a retired service" }
        check(!released) { "Cannot acquire a released service" }
        activeLeases += 1
    }

    /**
     * @return 当前是否已经没有租约，调用方应立即释放实例。
     */
    fun retire(): Boolean {
        if (released) {
            return false
        }
        retired = true
        return activeLeases == 0
    }

    /**
     * @return 归还后是否满足“已退休且无租约”，调用方应释放实例。
     */
    fun releaseLease(): Boolean {
        check(activeLeases > 0) { "Service lease released more than once" }
        activeLeases -= 1
        return retired && activeLeases == 0 && !released
    }

    /**
     * @return 本次调用是否取得唯一释放权。
     */
    fun markReleased(): Boolean {
        if (released) {
            return false
        }
        check(retired) { "Active service cannot be released" }
        check(activeLeases == 0) { "Service with active leases cannot be released" }
        released = true
        return true
    }
}
