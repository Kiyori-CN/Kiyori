package com.ai.assistance.operit.util

import android.graphics.Bitmap

/**
 * API 26+ still exposes WEBP only through the legacy enum constant.
 * The app keeps this format for existing image/document output contracts.
 */
@Suppress("DEPRECATION")
internal fun legacyWebpCompressFormat(): Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP
