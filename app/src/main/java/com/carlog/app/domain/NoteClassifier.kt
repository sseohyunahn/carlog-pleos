package com.carlog.app.domain

/**
 * Keyword tagging for handover memos (ported from v1 NoteClassifier, mapped to the v2 [Category] enum).
 * "지하 3층 C구역" -> LOCATION, "트렁크에 우산" -> ITEM, "내일 충전 필요" -> CHARGE, "타이어 공기압 경고등" -> CARE.
 * Deliberately simple and explainable; the driver can always change the category before saving.
 *
 * Scoring:
 *  - Each category scores the number of *distinct* keywords found, so a recognizer stutter ("우산 우산") counts once.
 *  - Location wording counts at most 1: a 10 s memo often says where the car is *and* what is wrong, and the
 *    location words ("지하 2층 주차장 입구") come in bunches. Location therefore decides only when nothing else matched.
 *  - A safety word (warning light, brakes, tyres, ...) makes the memo CARE unless another category is clearly
 *    stronger (by 2 or more distinct keywords).
 *  - Ties: care needs action before driving, then charging, then forgotten items, then location hints.
 */
object NoteClassifier {
    private val LOCATION_WORDS = Regex(
        "(지하|지상|층|구역|기둥|주차장|주차|출구|입구|라인|동 앞|건물|위치|[Bb]\\s?\\d|(?<![A-Za-z])[A-Z]\\d{1,3}(?!\\d))"
    )
    /**
     * "키" counts only as a word of its own ("차 키를", "차키", "스마트키", "키가", "키" at the end), not as the end of
     * another word ("하키"): the character before it must not be Hangul other than 차/트.
     */
    private val ITEM_WORDS = Regex(
        "(우산|트렁크|뒷좌석|조수석|운전석\\s?(밑|아래)|글로브|가방|지갑|열쇠|(차\\s?)?(?<![가-힣&&[^차트]])키(?=\\s|$|[를가는도])|짐|물건|충전기|케이블|" +
            "카시트|유모차|선글라스|보조\\s?배터리|휴대폰|핸드폰|안경|이어폰|에어팟|텀블러|두고|두었|뒀어|놓고|놓았|놔뒀|챙겨)"
    )
    /**
     * "충전기" is an item, so "충전" only counts when it is not followed by 기; a power bank ("보조배터리") is an item too.
     * "경유" alone also means "via" (경유해서), so it counts only with refuelling words.
     */
    private val CHARGE_WORDS = Regex(
        "(충전(?!기)|주유|기름|연료|(?<!보조\\s?)배터리|전기\\s?부족|방전|휘발유|경유\\s?(넣|주유))"
    )
    /** "사고" counts as an accident, not as the verb 사다 ("물 사고 와 줘"). */
    private val CARE_WORDS = Regex(
        "(정비|타이어|공기압|경고등|이상한 소리|소음|고장|점검|세차|오일|와이퍼|워셔|긁|찍힘|사고(?!\\s+(와|올|오|가|갈|싶|사)|와|올|오|싶)|" +
            "수리|브레이크|엔진|펑크|범퍼|문콕|찌그러|덜컹|냄새|에어컨|안\\s?(닫혀|잠겨)|연기\\s?(가\\s?)?(나|났))"
    )
    /** Words that mean the car may be unsafe to drive. */
    private val SAFETY_WORDS = Regex("(경고등|브레이크|타이어|펑크|고장|엔진|연기\\s?(가\\s?)?(나|났))")

    fun classify(text: String?): Category {
        if (text == null || text.isBlank()) return Category.OTHER
        val care = distinct(CARE_WORDS, text)
        val charge = distinct(CHARGE_WORDS, text)
        val item = distinct(ITEM_WORDS, text)
        val location = minOf(distinct(LOCATION_WORDS, text), 1)
        if (care == 0 && charge == 0 && item == 0 && location == 0) return Category.OTHER
        if (SAFETY_WORDS.containsMatchIn(text) && charge < care + 2 && item < care + 2) return Category.CARE
        return when {
            care >= charge && care >= item && care >= location -> Category.CARE
            charge >= item && charge >= location -> Category.CHARGE
            item >= location -> Category.ITEM
            else -> Category.LOCATION
        }
    }

    /** True when the text reads like a parking spot ("B3 C17", "지하 2층 기둥 옆"). */
    fun looksLikeParkingSpot(text: String?): Boolean = text != null && LOCATION_WORDS.containsMatchIn(text)

    private fun distinct(pattern: Regex, text: String): Int = pattern.findAll(text).map { it.value }.toSet().size
}
