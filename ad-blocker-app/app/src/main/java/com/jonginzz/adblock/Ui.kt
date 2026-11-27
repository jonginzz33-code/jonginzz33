package com.jonginzz.adblock

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.view.WindowInsets

/** 색·모양 공통 (라이트/다크 자동) */
class Ui(val c: Context) {
    val dark = (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    private fun pick(light: String, darkHex: String) = Color.parseColor(if (dark) darkHex else light)

    val bg = pick("#F3F5F9", "#0E1116")
    val card = pick("#FFFFFF", "#191E25")
    val text = pick("#16191D", "#EDF0F4")
    val sub = pick("#667180", "#9AA5B1")
    val line = pick("#E7EAF0", "#262C35")
    val green = pick("#14A05A", "#38C97C")
    val greenBg = pick("#E3F6EB", "#123222")
    val red = pick("#E03E3E", "#FF6B6B")
    val redBg = pick("#FDEBEB", "#3B1818")
    val grey = pick("#7A8594", "#8C97A4")
    val greyBg = pick("#ECEFF3", "#222831")
    val accent = pick("#2F6BEB", "#5C93FF")
    val tipBg = pick("#FFF6DB", "#2E2814")

    /**
     * 안드로이드 15+ 는 화면을 상태바 밑까지 그린다(edge-to-edge).
     * 상태바·내비게이션바 높이만큼 안쪽 여백을 더해 글자가 겹치지 않게 한다.
     */
    fun fitSystemBars(v: View) {
        val l = v.paddingLeft; val t = v.paddingTop; val r = v.paddingRight; val b = v.paddingBottom
        v.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int; val bottom: Int
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                top = bars.top; bottom = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(l, t + top, r, b + bottom)
            insets
        }
        v.requestApplyInsets()
    }

    fun dp(v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun round(color: Int, radiusDp: Int = 20) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radiusDp).toFloat()
    }

    /** 눌렀을 때 물결 효과가 있는 둥근 배경 */
    fun pressable(v: View, color: Int, radiusDp: Int = 20) {
        v.background = RippleDrawable(
            ColorStateList.valueOf(Color.argb(40, 0, 0, 0)), round(color, radiusDp), null)
        v.isClickable = true
    }
}
