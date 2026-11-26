package com.jonginzz.adblock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView

/** 앱 목록에서 체크한 앱은 모든 알림을 즉시 삭제 */
class AppsActivity : Activity() {

    private data class App(val pkg: String, val label: String) {
        override fun toString() = label
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pm = packageManager
        val blocked = Prefs.blockedApps(this).toMutableSet()

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != packageName }
            .distinctBy { it.pkg }
            .sortedWith(compareBy<App> { it.pkg !in blocked }.thenBy { it.label })

        val list = ListView(this).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            adapter = ArrayAdapter(this@AppsActivity,
                android.R.layout.simple_list_item_multiple_choice, apps)
        }
        apps.forEachIndexed { i, a -> list.setItemChecked(i, a.pkg in blocked) }
        list.setOnItemClickListener { _, _, pos, _ ->
            val pkg = apps[pos].pkg
            if (list.isItemChecked(pos)) blocked.add(pkg) else blocked.remove(pkg)
            Prefs.setBlockedApps(this, blocked)
        }
        setContentView(list)
    }
}
