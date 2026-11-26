package com.jonginzz.adblock

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var permBtn: Button
    private lateinit var keywords: EditText
    private lateinit var logView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun header(t: String) = TextView(this).apply {
            text = t; textSize = 18f; setTypeface(null, Typeface.BOLD)
            setPadding(0, pad, 0, pad / 3)
        }.also { root.addView(it) }
        fun note(t: String) = TextView(this).apply {
            text = t; textSize = 13f; alpha = 0.75f
        }.also { root.addView(it) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t; setOnClickListener { onClick() }
        }.also { root.addView(it) }

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        permBtn = button("① 알림 접근 권한 허용하러 가기") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        root.addView(Switch(this).apply {
            text = "광고 알림 자동 삭제"
            textSize = 16f
            isChecked = Prefs.isEnabled(this@MainActivity)
            setPadding(0, pad / 2, 0, pad / 2)
            setOnCheckedChangeListener { _, on -> Prefs.setEnabled(this@MainActivity, on); refresh() }
        })

        header("차단 키워드 (한 줄에 하나)")
        note("제목·내용에 이 단어가 있으면 바로 지웁니다. 띄어쓰기는 무시해요.")
        keywords = EditText(this).apply {
            setText(Prefs.keywords(this@MainActivity).joinToString("\n"))
            minLines = 4
        }
        root.addView(keywords)
        button("키워드 저장") {
            Prefs.setKeywords(this, keywords.text.lines().map { it.trim() }.filter { it.isNotEmpty() })
            Toast.makeText(this, "저장했어요", Toast.LENGTH_SHORT).show()
        }
        button("기본값으로 되돌리기") {
            Prefs.setKeywords(this, Prefs.DEFAULT_KEYWORDS)
            keywords.setText(Prefs.DEFAULT_KEYWORDS.joinToString("\n"))
        }

        header("앱 통째로 차단")
        note("광고만 보내는 앱은 알림 전체를 지울 수 있어요.")
        button("앱 선택하기") { startActivity(Intent(this, AppsActivity::class.java)) }

        header("지금 정리")
        button("알림창에 남은 광고 지금 지우기") {
            val s = AdBlockService.instance
            if (s == null) Toast.makeText(this, "먼저 알림 접근 권한을 허용하세요", Toast.LENGTH_SHORT).show()
            else { s.sweep(); refresh() }
        }

        header("💡 아예 안 오게 하려면")
        note(
            "• 각 앱 설정에서 '마케팅/광고 알림 수신 동의'를 끄세요. 알림 하단의 '※ 수신거부: …' 경로가 그 위치예요.\n" +
            "• 삼성: 알림을 길게 누르기 → 알림 설정 → '광고/혜택/이벤트' 같은 카테고리만 끄기\n" +
            "• 이 앱은 그래도 오는 광고를 도착 즉시 지워줍니다."
        )

        header("최근 지운 알림")
        logView = TextView(this).apply { textSize = 13f; setTextIsSelectable(true) }
        root.addView(logView)
        button("기록 지우기") { Prefs.clearLog(this); refresh() }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun hasPermission(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, AdBlockService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    private fun refresh() {
        val ok = hasPermission()
        val on = Prefs.isEnabled(this)
        status.text = when {
            !ok -> "⚠️ 알림 접근 권한이 필요해요"
            !on -> "⏸ 꺼져 있음"
            else -> "✅ 작동 중 · 지금까지 ${Prefs.count(this)}개 지움"
        }
        permBtn.visibility = if (ok) View.GONE else View.VISIBLE
        val log = Prefs.log(this)
        logView.text = if (log.isEmpty()) "아직 없음" else log.joinToString("\n")
    }
}
