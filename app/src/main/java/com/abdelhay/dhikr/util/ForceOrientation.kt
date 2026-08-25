package com.abdelhay.dhikr.util

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * يفرض اتجاه الشاشة ما دامت هذه الشاشة معروضة، ويعيده إلى وضعه عند مغادرتها.
 *
 * القراءة عرضًا تُظهر سطر المصحف أوسع فيكبر الخطّ، وهي أنسب للقارئ ضعيف البصر
 * وللأجهزة الصغيرة. ولأننا لا نريد أن يُقلب التطبيق كلّه، يُفرض هنا فقط.
 */
@Composable
fun ForceOrientation(landscape: Boolean) {
    val context = LocalContext.current
    DisposableEffect(landscape) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
