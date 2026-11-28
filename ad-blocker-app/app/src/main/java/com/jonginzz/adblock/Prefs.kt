package com.jonginzz.adblock

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 설정/기록 저장소 (SharedPreferences) */
object Prefs {
    private const val NAME = "adblock"
    private const val K_ENABLED = "enabled"
    private const val K_KEYWORDS = "keywords"
    private const val K_APPS = "blocked_apps"
    private const val K_ALLOW = "allowed_apps"
    private const val K_LOG = "log"
    private const val K_COUNT = "count"
    private const val K_UNSEEN = "unseen"
    private const val K_BADGE = "badge"
    private const val MAX_LOG = 100

    /**
     * 기본 키워드. 한국 정보통신망법상 광고성 푸시는 "(광고)" 표기와 수신거부 안내가 의무라서
     * 이 두 가지만으로도 대부분의 광고 알림이 걸러진다. (공백은 무시하고 비교)
     */
    val DEFAULT_KEYWORDS = listOf("(광고)", "[광고]", "<광고>", "수신거부", "수신 거부")

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isEnabled(c: Context) = sp(c).getBoolean(K_ENABLED, true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_ENABLED, v).apply()

    fun keywords(c: Context): List<String> =
        sp(c).getString(K_KEYWORDS, null)?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: DEFAULT_KEYWORDS

    fun setKeywords(c: Context, list: List<String>) =
        sp(c).edit().putString(K_KEYWORDS, list.joinToString("\n")).apply()

    fun blockedApps(c: Context): Set<String> = sp(c).getStringSet(K_APPS, emptySet())!!.toSet()
    fun setBlockedApps(c: Context, s: Set<String>) = sp(c).edit().putStringSet(K_APPS, s).apply()

    /** 예외 앱: 광고 키워드가 있어도 절대 지우지 않음 */
    fun allowedApps(c: Context): Set<String> = sp(c).getStringSet(K_ALLOW, emptySet())!!.toSet()
    fun setAllowedApps(c: Context, s: Set<String>) = sp(c).edit().putStringSet(K_ALLOW, s).apply()

    fun count(c: Context) = sp(c).getInt(K_COUNT, 0)

    fun log(c: Context): List<String> =
        sp(c).getString(K_LOG, "")!!.lines().filter { it.isNotEmpty() }

    @Synchronized
    fun addLog(c: Context, appName: String, title: String) {
        val time = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA).format(Date())
        val line = "$time  [$appName] $title".replace("\n", " ")
        val list = (listOf(line) + log(c)).take(MAX_LOG)
        sp(c).edit()
            .putString(K_LOG, list.joinToString("\n"))
            .putInt(K_COUNT, count(c) + 1)
            .putInt(K_UNSEEN, unseen(c) + 1)
            .apply()
    }

    fun unseen(c: Context) = sp(c).getInt(K_UNSEEN, 0)
    fun resetUnseen(c: Context) = sp(c).edit().putInt(K_UNSEEN, 0).apply()
    fun badgeEnabled(c: Context) = sp(c).getBoolean(K_BADGE, true)
    fun setBadgeEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean(K_BADGE, v).apply()

    fun clearLog(c: Context) = sp(c).edit().remove(K_LOG).putInt(K_COUNT, 0).apply()
}
