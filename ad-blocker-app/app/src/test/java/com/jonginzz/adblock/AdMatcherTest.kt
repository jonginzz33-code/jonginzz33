package com.jonginzz.adblock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdMatcherTest {
    private val kw = Prefs.DEFAULT_KEYWORDS

    @Test fun monimoAd_isBlocked() = assertTrue(AdMatcher.isAd(
        listOf("(광고) monimo", "모닥불과 군고구마 있는 겨울 산장 콘셉트 카페 3곳⛄ ※ 수신거부: 전체메뉴 > 설정"), kw))

    @Test fun spacedAndBracketVariants_areBlocked() {
        assertTrue(AdMatcher.isAd(listOf("( 광고 ) 쿠폰 도착"), kw))
        assertTrue(AdMatcher.isAd(listOf("[광고]오늘만 특가"), kw))
        assertTrue(AdMatcher.isAd(listOf("이벤트", null, "무료 수신 거부 080-000-0000"), kw))
    }

    @Test fun normalNotifications_areKept() {
        assertFalse(AdMatcher.isAd(listOf("알콩달콩♡♡", "응? 바다 한글 골든벨해서 문제의 답이었나봄"), kw))
        assertFalse(AdMatcher.isAd(listOf("네이버경제", "프랑스 CAC 40 2% 이상 상승 (현재가 8,140.22 ▲159.65 +2.00%)"), kw))
        assertFalse(AdMatcher.isAd(listOf("스크린샷 저장됨", "스크린샷을 보려면 여기를 누르세요."), kw))
        assertFalse(AdMatcher.isAd(listOf("의심스러운 악성 앱 1개를 찾았어요", "해킹 위험성이 있는 앱이에요."), kw))
        assertFalse(AdMatcher.isAd(listOf("♡알콩우리♡", "★ 매너콜 ★ [통화중] 상황에서 고객님께..."), kw))
    }

    @Test fun blankKeyword_matchesNothing() =
        assertFalse(AdMatcher.isAd(listOf("아무 알림"), listOf("  ", "")))
}
