package com.jonginzz.adblock

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * 알림이 올라오는 순간 검사해서 광고면 바로 지운다.
 * (안드로이드는 다른 앱 알림을 '도착 전에' 막는 API가 없으므로, 도착 즉시 취소하는 방식)
 */
class AdBlockService : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        sweep() // 이미 쌓여 있는 광고 알림도 정리
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        check(sbn)
    }

    /** 현재 알림창에 있는 모든 알림 검사 */
    fun sweep() {
        try {
            activeNotifications?.forEach { check(it) }
        } catch (_: Exception) { }
    }

    private fun check(sbn: StatusBarNotification) {
        if (!Prefs.isEnabled(this)) return
        if (sbn.packageName == packageName) return

        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val appBlocked = sbn.packageName in Prefs.blockedApps(this)
        val isAd = !appBlocked && run {
            // 진행 중 알림(음악, 통화 등)은 키워드 검사 대상에서 제외
            if (sbn.isOngoing) return@run false
            val all = listOf(
                title, text,
                ex.getCharSequence(Notification.EXTRA_BIG_TEXT),
                ex.getCharSequence(Notification.EXTRA_SUB_TEXT),
                ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
                ex.getCharSequence(Notification.EXTRA_TITLE_BIG),
                n.tickerText,
            ).joinToString(" ") { it?.toString().orEmpty() }.noSpace()
            Prefs.keywords(this).any { kw -> all.contains(kw.noSpace(), ignoreCase = true) }
        }

        if (appBlocked || isAd) {
            cancelNotification(sbn.key)
            Prefs.addLog(this, appLabel(sbn.packageName), title.ifEmpty { text }.take(60))
        }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
    } catch (_: Exception) { pkg }

    private fun String.noSpace() = replace(Regex("\\s+"), "")

    companion object {
        @Volatile var instance: AdBlockService? = null
    }
}
