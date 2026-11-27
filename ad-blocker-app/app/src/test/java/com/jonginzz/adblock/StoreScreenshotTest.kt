package com.jonginzz.adblock

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowPowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 스토어용 실제 화면 캡처. SCREENSHOT_DIR 환경변수가 있을 때만 파일로 저장. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class StoreScreenshotTest {

    private val out = System.getenv("SCREENSHOT_DIR")?.let { File(it).apply { mkdirs() } }

    private fun seed(ctx: Context) {
        Settings.Secure.putString(ctx.contentResolver, "enabled_notification_listeners",
            "${ctx.packageName}/${AdBlockService::class.java.name}")
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        org.robolectric.Shadows.shadowOf(pm).setIgnoringBatteryOptimizations(ctx.packageName, true)
        Prefs.clearLog(ctx)
        val today = SimpleDateFormat("MM/dd", Locale.KOREA).format(Date())
        val sp = ctx.getSharedPreferences("adblock", Context.MODE_PRIVATE)
        val lines = listOf(
            "$today 19:42  [monimo] (광고) 겨울 산장 콘셉트 카페 3곳",
            "$today 18:05  [쿠팡] (광고) 오늘만 최대 70% 타임딜",
            "$today 12:31  [신한카드] (광고) 12월 혜택 미리보기",
            "$today 09:10  [배달앱] [광고] 첫 주문 5,000원 할인 쿠폰",
            "11/26 21:17  [토스] (광고) 이번 주 행운 퀴즈 정답은?",
        )
        sp.edit().putString("log", lines.joinToString("\n")).putInt("count", 127).commit()
    }

    private fun snap(a: Activity, name: String) {
        val v: View = a.window.decorView
        v.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY))
        v.layout(0, 0, 1080, 2340)
        val bmp = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        out?.let { File(it, "$name.png").outputStream().use { o -> bmp.compress(Bitmap.CompressFormat.PNG, 100, o) } }
    }

    @Test fun mainScreen() {
        val ctx = RuntimeEnvironment.getApplication()
        seed(ctx)
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        snap(a, "main")
    }

    @Test fun appsScreen() {
        val ctx = RuntimeEnvironment.getApplication()
        seed(ctx)
        val a = Robolectric.buildActivity(AppsActivity::class.java).setup().get()
        snap(a, "apps")
    }
}
