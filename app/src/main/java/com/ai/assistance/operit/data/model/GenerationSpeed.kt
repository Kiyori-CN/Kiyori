package com.ai.assistance.operit.data.model

/** 当前或最近一次模型请求的输出速度；只在内存中保留，不推算历史请求。 */
data class GenerationSpeed(val tokensPerSecond: Double, val isEstimated: Boolean)
