package com.jonginzz.adblock

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var statusCard: LinearLayout
    private lateinit var statusIcon: TextView
    private lateinit var statusTitle: TextView
    private lateinit var statusCount: TextView
    private lateinit var statusSub: TextView
    private lateinit var permBtn: TextView
    private lateinit var toggle: Switch
    private lateinit var recentBox: LinearLayout
    private lateinit var keywordValue: TextView
    private lateinit var appsValue: TextView

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        runOnUiThread { refresh() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        window.statusBarColor = ui.bg
        if (!ui.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(32))
        }

        // 제목
        root.addView(text("광고알림 차단", 26f, ui.text, bold = true).apply {
            setPadding(ui.dp(4), 0, 0, ui.dp(14))
        })

        // ── 1. 상태 카드 ──
        statusCard = vbox().apply { setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(18)) }
        statusIcon = text("", 40f, ui.text)
        statusTitle = text("", 24f, ui.text, bold = true).apply { setPadding(0, ui.dp(6), 0, 0) }
        statusCount = text("", 17f, ui.text, bold = true).apply { setPadding(0, ui.dp(8), 0, 0) }
        statusSub = text("", 14f, ui.sub).apply { setPadding(0, ui.dp(2), 0, 0) }
        permBtn = bigButton("알림 접근 권한 허용하기", Color.WHITE, ui.red) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(14) }
        toggle = Switch(this).apply {
            text = "광고 자동 삭제"
            textSize = 16f
            setTextColor(ui.text)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, ui.dp(14), 0, 0)
            isChecked = Prefs.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, on -> Prefs.setEnabled(this@MainActivity, on); refresh() }
        }
        listOf(statusIcon, statusTitle, statusCount, statusSub, permBtn, toggle).forEach(statusCard::addView)
        root.addView(statusCard, block())

        // ── 2. 지금 지우기 버튼 ──
        root.addView(bigButton("🧹  알림창 광고 지금 지우기", Color.WHITE, ui.accent) {
            val s = AdBlockService.instance
            if (s == null) toast("먼저 알림 접근 권한을 허용해 주세요")
            else {
                val before = Prefs.count(this)
                s.sweep()
                val n = Prefs.count(this) - before
                toast(if (n > 0) "광고 ${n}개를 지웠어요" else "지울 광고가 없어요")
            }
        }, block(top = 12))

        // ── 3. 최근 지운 알림 ──
        root.addView(sectionTitle("최근 지운 알림"))
        recentBox = vbox().apply {
            background = ui.round(ui.card)
            setPadding(ui.dp(18), ui.dp(6), ui.dp(18), ui.dp(6))
        }
        root.addView(recentBox, block())

        // ── 4. 설정 ──
        root.addView(sectionTitle("설정"))
        val settings = vbox().apply { background = ui.round(ui.card) }
        keywordValue = text("", 15f, ui.sub)
        appsValue = text("", 15f, ui.sub)
        settings.addView(settingRow("🔤", "차단 키워드", "이 단어가 들어간 알림을 지워요", keywordValue) { editKeywords() })
        settings.addView(View(this).apply { setBackgroundColor(ui.line) },
            LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { leftMargin = ui.dp(18); rightMargin = ui.dp(18) })
        settings.addView(settingRow("📵", "앱 통째로 차단", "광고만 보내는 앱의 알림을 모두 지워요", appsValue) {
            startActivity(Intent(this, AppsActivity::class.java))
        })
        root.addView(settings, block())

        // ── 5. 팁 ──
        root.addView(vbox().apply {
            background = ui.round(ui.tipBg)
            setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
            addView(text("💡 광고가 아예 안 오게 하려면", 16f, ui.text, bold = true))
            addView(text(
                "• 알림 맨 아래 '※ 수신거부' 안내대로 그 앱의 마케팅 수신 동의를 끄세요.\n" +
                "• 삼성: 알림을 길게 누르기 → 알림 설정 → '혜택·이벤트' 같은 항목만 끄기",
                14f, ui.sub).apply { setPadding(0, ui.dp(8), 0, 0); setLineSpacing(0f, 1.25f) })
        }, block(top = 20))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(ui.bg)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences("adblock", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("adblock", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    // ───────── 화면 갱신 ─────────

    private fun refresh() {
        val ok = hasPermission()
        val on = Prefs.isEnabled(this)
        val log = Prefs.log(this)
        val today = SimpleDateFormat("MM/dd", Locale.KOREA).format(Date())
        val todayCount = log.count { it.startsWith(today) }
        val total = Prefs.count(this)

        when {
            !ok -> {
                statusCard.background = ui.round(ui.redBg)
                statusIcon.text = "⚠️"
                statusTitle.text = "권한이 필요해요"; statusTitle.setTextColor(ui.red)
                statusCount.text = "아래 버튼 → '광고알림 차단' 켜기"
                statusSub.text = "알림을 읽을 수 있어야 광고를 지울 수 있어요"
            }
            !on -> {
                statusCard.background = ui.round(ui.greyBg)
                statusIcon.text = "⏸️"
                statusTitle.text = "꺼져 있어요"; statusTitle.setTextColor(ui.grey)
                statusCount.text = "지금은 광고를 지우지 않아요"
                statusSub.text = "아래 스위치를 켜면 다시 지워요"
            }
            else -> {
                statusCard.background = ui.round(ui.greenBg)
                statusIcon.text = "🛡️"
                statusTitle.text = "광고 차단 중"; statusTitle.setTextColor(ui.green)
                statusCount.text = "오늘 ${todayCount}개 · 전체 ${total}개 지웠어요"
                statusSub.text = "광고 알림이 오면 바로 사라져요"
            }
        }
        permBtn.visibility = if (ok) View.GONE else View.VISIBLE
        toggle.visibility = if (ok) View.VISIBLE else View.GONE
        if (toggle.isChecked != on) toggle.isChecked = on

        // 최근 기록
        recentBox.removeAllViews()
        if (log.isEmpty()) {
            recentBox.addView(text("아직 지운 광고가 없어요.\n광고가 오면 여기에 쌓여요.", 15f, ui.sub).apply {
                gravity = Gravity.CENTER
                setPadding(0, ui.dp(22), 0, ui.dp(22))
                setLineSpacing(0f, 1.25f)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        } else {
            log.take(MAX_RECENT).forEachIndexed { i, line ->
                if (i > 0) recentBox.addView(View(this).apply { setBackgroundColor(ui.line) },
                    LinearLayout.LayoutParams(MATCH_PARENT, 1))
                recentBox.addView(recentRow(line))
            }
            recentBox.addView(LinearLayout(this).apply {
                gravity = Gravity.END
                setPadding(0, ui.dp(4), 0, ui.dp(8))
                if (log.size > MAX_RECENT) addView(link("전체 ${log.size}개 보기") { showAllLog(log) })
                addView(link("기록 지우기") {
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage("지운 알림 기록을 모두 지울까요?")
                        .setPositiveButton("지우기") { _, _ -> Prefs.clearLog(this@MainActivity); refresh() }
                        .setNegativeButton("취소", null).show()
                })
            })
        }

        keywordValue.text = "${Prefs.keywords(this).size}개  ›"
        appsValue.text = "${Prefs.blockedApps(this).size}개  ›"
    }

    private fun hasPermission(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, AdBlockService::class.java)
        return flat.split(":").any { ComponentName.unflattenFromString(it) == me }
    }

    // ───────── 동작 ─────────

    private fun editKeywords() {
        val box = EditText(this).apply {
            setText(Prefs.keywords(this@MainActivity).joinToString("\n"))
            minLines = 5
            textSize = 17f
            gravity = Gravity.TOP
        }
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(22), ui.dp(6), ui.dp(22), 0)
            addView(TextView(this@MainActivity).apply {
                text = "한 줄에 하나씩 적어요. 띄어쓰기는 무시해요."
                textSize = 14f
            })
            addView(box)
        }
        AlertDialog.Builder(this)
            .setTitle("차단 키워드")
            .setView(wrap)
            .setPositiveButton("저장") { _, _ ->
                Prefs.setKeywords(this, box.text.lines().map { it.trim() }.filter { it.isNotEmpty() })
                toast("저장했어요"); refresh()
            }
            .setNeutralButton("기본값") { _, _ ->
                Prefs.setKeywords(this, Prefs.DEFAULT_KEYWORDS); toast("기본값으로 되돌렸어요"); refresh()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showAllLog(log: List<String>) {
        AlertDialog.Builder(this)
            .setTitle("지운 알림 ${log.size}개")
            .setItems(log.toTypedArray(), null)
            .setPositiveButton("닫기", null)
            .show()
    }

    // ───────── 화면 부품 ─────────

    private fun recentRow(line: String): View {
        // 형식: "MM/dd HH:mm  [앱이름] 제목"
        val m = Regex("""^(\S+ \S+)\s+\[(.*?)]\s?(.*)$""").find(line)
        val time = m?.groupValues?.get(1) ?: ""
        val app = m?.groupValues?.get(2) ?: ""
        val title = m?.groupValues?.get(3) ?: line
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ui.dp(12), 0, ui.dp(12))
            addView(vbox().apply {
                addView(text(app, 15f, ui.text, bold = true))
                addView(text(title, 14f, ui.sub).apply { maxLines = 2 })
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(text(time.substringAfter(' '), 13f, ui.sub).apply { setPadding(ui.dp(10), 0, 0, 0) })
        }
    }

    private fun settingRow(icon: String, title: String, desc: String, value: TextView, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
            ui.pressable(this, ui.card)
            setOnClickListener { onClick() }
            addView(text(icon, 24f, ui.text).apply { setPadding(0, 0, ui.dp(14), 0) })
            addView(vbox().apply {
                addView(text(title, 17f, ui.text, bold = true))
                addView(text(desc, 13f, ui.sub))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(value)
        }

    private fun bigButton(label: String, fg: Int, bg: Int, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 18f
        setTypeface(null, Typeface.BOLD)
        setTextColor(fg)
        gravity = Gravity.CENTER
        setPadding(ui.dp(16), ui.dp(17), ui.dp(16), ui.dp(17))
        ui.pressable(this, bg, 16)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    private fun link(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 14f
        setTextColor(ui.accent)
        setPadding(ui.dp(12), ui.dp(8), 0, ui.dp(4))
        setOnClickListener { onClick() }
    }

    private fun sectionTitle(t: String) = text(t, 18f, ui.text, bold = true).apply {
        setPadding(ui.dp(4), ui.dp(24), 0, ui.dp(10))
    }

    private fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }

    private fun vbox() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun block(top: Int = 0) = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
        topMargin = ui.dp(top)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object { private const val MAX_RECENT = 5 }
}
