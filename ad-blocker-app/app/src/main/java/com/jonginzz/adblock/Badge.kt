package com.jonginzz.adblock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 앱 아이콘 배지(숫자) = 마지막으로 앱을 연 뒤 지운 광고 수.
 * 안드로이드 배지는 알림이 있어야 표시되므로, 소리·진동 없는 조용한 알림 하나를 띄워 둔다.
 * 앱을 열면 숫자를 0으로 하고 알림을 지운다.
 */
object Badge {
    private const val CHANNEL = "badge"
    private const val ID = 1

    fun update(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val n = Prefs.unseen(c)
        if (n <= 0 || !Prefs.badgeEnabled(c)) { nm.cancel(ID); return }
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "지운 광고 개수", NotificationManager.IMPORTANCE_MIN).apply {
            description = "앱 아이콘에 지운 광고 개수를 숫자로 표시해요"
            setShowBadge(true)
            setSound(null, null)
            enableVibration(false)
        })
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notif = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("광고 알림 ${n}개를 지웠어요")
            .setContentText("눌러서 무엇을 지웠는지 확인하세요")
            .setNumber(n)
            .setBadgeIconType(Notification.BADGE_ICON_SMALL)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID, notif) }   // 알림 권한이 없으면 조용히 무시
    }

    fun clear(c: Context) {
        Prefs.resetUnseen(c)
        c.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
