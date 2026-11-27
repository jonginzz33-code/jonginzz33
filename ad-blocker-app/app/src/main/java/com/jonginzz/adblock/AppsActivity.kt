package com.jonginzz.adblock

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView

/** 앱 목록에서 켠 앱은 모든 알림을 즉시 삭제 */
class AppsActivity : Activity() {

    private class App(val pkg: String, val label: String) { var icon: Drawable? = null }

    private lateinit var ui: Ui
    private lateinit var all: List<App>
    private var shown: List<App> = emptyList()
    private val blocked = mutableSetOf<String>()
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        window.statusBarColor = ui.bg
        if (!ui.dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        val pm = packageManager
        blocked += Prefs.blockedApps(this)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        all = pm.queryIntentActivities(launcher, 0)
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<App> { it.pkg !in blocked }.thenBy { it.label.lowercase() })
        shown = all

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.bg)
            setPadding(ui.dp(16), ui.dp(20), ui.dp(16), 0)
        }
        root.addView(TextView(this).apply {
            text = "앱 통째로 차단"; textSize = 24f; setTypeface(null, Typeface.BOLD); setTextColor(ui.text)
        })
        root.addView(TextView(this).apply {
            text = "켠 앱의 알림은 광고가 아니어도 모두 지워요."
            textSize = 14f; setTextColor(ui.sub); setPadding(0, ui.dp(4), 0, ui.dp(12))
        })
        root.addView(EditText(this).apply {
            hint = "🔍  앱 이름 검색"
            textSize = 16f
            setTextColor(ui.text); setHintTextColor(ui.sub)
            background = ui.round(ui.card, 14)
            setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val q = s.toString().trim().lowercase()
                    shown = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
                    adapter.notifyDataSetChanged()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
                val row = (convert as? LinearLayout) ?: makeRow()
                val app = shown[p]
                if (app.icon == null) app.icon = runCatching { pm.getApplicationIcon(app.pkg) }.getOrNull()
                (row.getChildAt(0) as ImageView).setImageDrawable(app.icon)
                (row.getChildAt(1) as TextView).text = app.label
                (row.getChildAt(2) as Switch).isChecked = app.pkg in blocked
                return row
            }
        }
        root.addView(ListView(this).apply {
            this.adapter = this@AppsActivity.adapter
            divider = null
            setPadding(0, ui.dp(10), 0, ui.dp(20))
            clipToPadding = false
            setOnItemClickListener { _, view, pos, _ ->
                val pkg = shown[pos].pkg
                if (!blocked.add(pkg)) blocked.remove(pkg)
                Prefs.setBlockedApps(this@AppsActivity, blocked)
                ((view as LinearLayout).getChildAt(2) as Switch).isChecked = pkg in blocked
            }
        }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        ui.fitSystemBars(root)
        setContentView(root)
    }

    private fun makeRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10))
        addView(ImageView(this@AppsActivity), LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)))
        addView(TextView(this@AppsActivity).apply {
            textSize = 17f; setTextColor(ui.text); setPadding(ui.dp(14), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(Switch(this@AppsActivity).apply { isClickable = false; isFocusable = false })
    }
}
