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
        if (sbn.packageName in Prefs.allowedApps(this)) return   // 예외 앱은 건드리지 않음

        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()

        val appBlocked = sbn.packageName in Prefs.blockedApps(this)
        val isAd = !appBlocked && run {
            // 진행 중 알림(음악, 통화 등)은 키워드 검사 대상에서 제외
            if (sbn.isOngoing) return@run false
            AdMatcher.isAd(listOf(
                title, text,
                ex.getCharSequence(Notification.EXTRA_BIG_TEXT),
                ex.getCharSequence(Notification.EXTRA_SUB_TEXT),
                ex.getCharSequence(Notification.EXTRA_SUMMARY_TEXT),
                ex.getCharSequence(Notification.EXTRA_TITLE_BIG),
                n.tickerText,
            ), Prefs.keywords(this))
        }

        if (appBlocked || isAd) {
            cancelNotification(sbn.key)
            // 기록: "제목 · 내용" (무엇 때문에 지웠는지 보이게)
            val body = listOf(title, text).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            Prefs.addLog(this, appLabel(sbn.packageName), body.take(90))
            Badge.update(this)
        }
    }

    private fun appLabel(pkg: String): String = try {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
    } catch (_: Exception) { pkg }

    companion object {
        @Volatile var instance: AdBlockService? = null
    }
}
