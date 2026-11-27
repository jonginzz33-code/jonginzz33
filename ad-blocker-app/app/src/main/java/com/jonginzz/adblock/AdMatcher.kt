package com.jonginzz.adblock

/** 알림 텍스트가 광고 키워드를 포함하는지 판단 (공백·대소문자 무시) */
object AdMatcher {
    private val SPACE = Regex("\\s+")
    private fun String.noSpace() = replace(SPACE, "")

    fun isAd(texts: List<CharSequence?>, keywords: List<String>): Boolean {
        val all = texts.joinToString(" ") { it?.toString().orEmpty() }.noSpace()
        return keywords.map { it.noSpace() }.filter { it.isNotEmpty() }
            .any { all.contains(it, ignoreCase = true) }
    }
}
