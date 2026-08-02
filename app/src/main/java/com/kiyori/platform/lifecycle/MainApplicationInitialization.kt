package com.kiyori.platform.lifecycle

/**
 * 默认主进程请求 Application 初始化的稳定边界。
 *
 * 实现仍由唯一 KiyoriApplication 持有；接口不保存 delegate，也不建立第二初始化 owner。
 */
interface MainApplicationInitialization {
    fun initializeMainUiPrerequisites()

    fun initializeMainApplication()
}
