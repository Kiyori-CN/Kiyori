package com.ai.assistance.operit.ui.floating.ui.window.models

internal const val MAX_COMPOSE_DIMENSION_PX = 262143

internal fun boundFloatingWindowDimensionPx(value: Int): Int =
    value.coerceIn(0, MAX_COMPOSE_DIMENSION_PX)
