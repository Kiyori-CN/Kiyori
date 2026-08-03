package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language

/**
 * 语言工厂类，用于初始化和获取所有语言支持
 */
object LanguageFactory {
    /**
     * 初始化所有语言支持
     */
    fun init() {
        LanguageSupportRegistry.init()
    }
    
    /**
     * 根据文件名获取语言支持
     */
    fun getLanguageSupportForFile(fileName: String): LanguageSupport? {
        return LanguageSupportRegistry.getLanguageSupportForFile(fileName)
    }
    
    /**
     * 根据语言名称获取语言支持
     */
    fun getLanguageSupport(language: String): LanguageSupport? {
        return LanguageSupportRegistry.getLanguageSupport(language)
    }
    
    /**
     * 获取所有支持的语言
     */
    fun getSupportedLanguages(): Set<String> {
        return LanguageSupportRegistry.getSupportedLanguages()
    }
} 
