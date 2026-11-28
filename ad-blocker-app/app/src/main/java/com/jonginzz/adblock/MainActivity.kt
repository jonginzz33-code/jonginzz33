package com.jonginzz.adblock

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
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
    private lateinit var batteryCard: LinearLayout
    private lateinit var keywordValue: TextView
    private lateinit var appsValue: TextView
    private lateinit var allowValue: TextView

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

        // 제목 줄: [⟳ 새로고침] 광고알림 차단
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, ui.dp(14))
            addView(TextView(this@MainActivity).apply {
                text = "⟳"
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ui.accent)
                gravity = Gravity.CENTER
                contentDescription = "새로고침 및 업데이트 확인"
                ui.pressable(this, ui.card, 24)
                setOnClickListener { refreshAndUpdate() }
            }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            addView(text("광고알림 차단", 26f, ui.text, bold = true).apply {
                setPadding(ui.dp(12), 0, 0, 0)
            })
        })

        // ── 1. 상태 카드 ──
        statusCard = vbox().apply { setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(16)) }
        statusIcon = text("", 30f, ui.text)
        statusTitle = text("", 24f, ui.text, bold = true).apply { setPadding(ui.dp(10), 0, 0, 0) }
        val statusHead = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(statusIcon); addView(statusTitle)
        }
        statusCount = text("", 18f, ui.text, bold = true).apply { setPadding(0, ui.dp(12), 0, 0) }
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
        listOf(statusHead, statusCount, statusSub, permBtn, toggle).forEach(statusCard::addView)
        root.addView(statusCard, block())

        // ── 1-2. 꺼지지 않게: 배터리 '제한 없음' 안내 (설정 안 됐을 때만 보임) ──
        batteryCard = vbox().apply {
            background = ui.round(ui.tipBg)
            setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18))
            addView(text("🔋 꺼지지 않게 한 번만 설정해 주세요", 17f, ui.text, bold = true))
            addView(text(
                "삼성폰은 배터리 절약 때문에 이 앱을 가끔 잠재워요.\n" +
                "아래 버튼 → '배터리' → '제한 없음'을 고르고 돌아오세요.\n" +
                "알림이 올 때만 잠깐 일해서 배터리는 거의 안 써요.",
                14f, ui.sub).apply { setPadding(0, ui.dp(8), 0, ui.dp(12)); setLineSpacing(0f, 1.25f) })
            addView(bigButton("배터리 설정 열기", Color.WHITE, ui.accent) { openBatterySettings() })
        }
        root.addView(batteryCard, block(top = 12))

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
        allowValue = text("", 15f, ui.sub)
        settings.addView(View(this).apply { setBackgroundColor(ui.line) },
            LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { leftMargin = ui.dp(18); rightMargin = ui.dp(18) })
        settings.addView(settingRow("✅", "예외 앱", "이 앱의 알림은 절대 지우지 않아요", allowValue) {
            startActivity(Intent(this, AppsActivity::class.java)
                .putExtra(AppsActivity.EXTRA_MODE, AppsActivity.MODE_ALLOW))
        })
        settings.addView(View(this).apply { setBackgroundColor(ui.line) },
            LinearLayout.LayoutParams(MATCH_PARENT, 1).apply { leftMargin = ui.dp(18); rightMargin = ui.dp(18) })
        settings.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16))
            addView(text("🔴", 22f, ui.text).apply { setPadding(0, 0, ui.dp(14), 0) })
            addView(vbox().apply {
                addView(text("아이콘에 지운 개수 표시", 17f, ui.text, bold = true))
                addView(text("켜면 알림창에 조용한 알림이 생기고 아이콘에 숫자가 떠요", 13f, ui.sub))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(Switch(this@MainActivity).apply {
                isChecked = Prefs.badgeEnabled(this@MainActivity)
                setOnCheckedChangeListener { _, on ->
                    Prefs.setBadgeEnabled(this@MainActivity, on)
                    if (!on) Badge.clear(this@MainActivity)
                    else if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                        android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
                    }
                }
            })
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

        // 상태바 여백은 스크롤 바깥(ScrollView)에 줘야 내려도 글자가 시계 밑으로 안 들어감
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ui.bg)
            clipToPadding = true
            addView(root)
        }
        ui.fitSystemBars(scroll)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        getSharedPreferences("adblock", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListener)
        Badge.clear(this)   // 앱을 열었으니 아이콘 숫자 지우기
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
        val needBattery = ok && !isBatteryUnrestricted()
        batteryCard.visibility = if (needBattery) View.VISIBLE else View.GONE
        if (needBattery && !askedBattery) {
            askedBattery = true
            AlertDialog.Builder(this)
                .setTitle("🔋 한 가지만 더 설정해 주세요")
                .setMessage("삼성폰은 배터리 절약 때문에 이 앱을 가끔 잠재워서 광고가 안 지워질 수 있어요.\n\n" +
                    "다음 화면에서 '배터리' → '제한 없음'을 고르고 뒤로 돌아오면 끝이에요.")
                .setPositiveButton("설정하러 가기") { _, _ -> openBatterySettings() }
                .setNegativeButton("나중에", null)
                .show()
        }
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
        allowValue.text = "${Prefs.allowedApps(this).size}개  ›"
    }

    /** 화면 새로고침 + Play 스토어에 새 버전이 있으면 바로 업데이트 */
    private fun refreshAndUpdate() {
        refresh()
        AdBlockService.instance?.sweep()
        val current = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        val mgr = AppUpdateManagerFactory.create(this)
        toast("새 버전 확인 중…")
        mgr.appUpdateInfo
            .addOnSuccessListener { info ->
                val avail = info.updateAvailability()
                if ((avail == UpdateAvailability.UPDATE_AVAILABLE ||
                        avail == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                    @Suppress("DEPRECATION")
                    mgr.startUpdateFlowForResult(info, AppUpdateType.IMMEDIATE, this, REQ_UPDATE)
                } else {
                    toast("최신 버전이에요 ($current)")
                }
            }
            .addOnFailureListener {
                // Play 스토어로 설치하지 않은 경우 등
                toast("업데이트 확인 실패 — Play 스토어에서 직접 확인해 주세요")
            }
    }

    /** 배터리 최적화 예외(삼성 '제한 없음')인지 */
    private fun isBatteryUnrestricted(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    /** 이 앱의 '애플리케이션 정보' 화면을 연다. 거기서 배터리 → 제한 없음 */
    private fun openBatterySettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        toast("'배터리' → '제한 없음'을 골라 주세요")
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

    /** 이번 실행에서 배터리 안내 창을 이미 띄웠는지 (화면 돌아올 때마다 뜨지 않게) */
    private var askedBattery = false

    companion object {
        private const val MAX_RECENT = 5
        private const val REQ_UPDATE = 7001
    }
}
