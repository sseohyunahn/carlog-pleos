package com.carlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteClassifierTest {
    @Test fun planningDocExamplesGetTheExpectedCategories() {
        assertEquals(Category.LOCATION, NoteClassifier.classify("지하 3층 C구역"))
        assertEquals(Category.LOCATION, NoteClassifier.classify("B3 C17"))
        assertEquals(Category.ITEM, NoteClassifier.classify("트렁크에 우산 있음"))
        assertEquals(Category.CHARGE, NoteClassifier.classify("내일 충전 필요"))
    }

    @Test fun chargerCableIsAnItemNotCharging() {
        assertEquals(Category.ITEM, NoteClassifier.classify("충전기 뒷좌석에 두었어요"))
    }

    @Test fun fuelAndChargingWordsAreCharge() {
        assertEquals(Category.CHARGE, NoteClassifier.classify("주유 부탁해요"))
        assertEquals(Category.CHARGE, NoteClassifier.classify("배터리 20퍼센트 남았어요"))
    }

    @Test fun maintenanceWordsAreCare() {
        assertEquals(Category.CARE, NoteClassifier.classify("타이어 공기압 경고등이 켜졌어요"))
        assertEquals(Category.CARE, NoteClassifier.classify("세차 했어요"))
        assertEquals(Category.CARE, NoteClassifier.classify("워셔액 부족해요"))
        assertEquals(Category.CARE, NoteClassifier.classify("정비소 예약했어요"))
    }

    @Test fun careWinsTiesBecauseItNeedsActionBeforeDriving() {
        assertEquals(Category.CARE, NoteClassifier.classify("트렁크 쪽 타이어 공기압 경고등"))
        assertEquals(Category.CARE, NoteClassifier.classify("배터리 경고등"))
    }

    @Test fun mixedMessageFollowsTheStrongerSignal() {
        assertEquals(Category.ITEM, NoteClassifier.classify("뒷좌석에 우산 두었고, 다음 주행 전에 충전 상태 확인해 줘"))
    }

    @Test fun parkingWordsAreLocation() {
        assertEquals(Category.LOCATION, NoteClassifier.classify("주차 위치는 기둥 옆이에요"))
    }

    @Test fun unknownOrEmptyTextIsOther() {
        assertEquals(Category.OTHER, NoteClassifier.classify("고마워요"))
        assertEquals(Category.OTHER, NoteClassifier.classify(""))
        assertEquals(Category.OTHER, NoteClassifier.classify("   "))
        assertEquals(Category.OTHER, NoteClassifier.classify(null))
    }

    /** Long 10 s voice memos mix a place with a problem; repeated recognizer tokens must not decide. */
    @Test fun safetyWinsOverDenseLocationAndRepeatedWords() {
        mapOf(
            "지하 2층 주차장 입구 쪽 기둥 옆에 주차했는데 타이어 경고등 켜졌어" to Category.CARE,
            "기름이 거의 없어 주차장 지하 2층" to Category.CHARGE,
            "주유소 들러서 기름 넣어 줘 브레이크 밀려" to Category.CARE,
            "우산 우산 우산 브레이크 경고등" to Category.CARE,
            "지하 2층에 주차했어요" to Category.LOCATION
        ).forEach { (text, expected) -> assertEquals(text, expected, NoteClassifier.classify(text)) }
    }

    @Test fun ambiguousWordsAndMissingVocabulary() {
        mapOf(
            // 사고 as the verb "buy" is not an accident; as a noun it is.
            "편의점에서 물 사고 와 줘" to Category.OTHER,
            "사고가 났어요" to Category.CARE,
            "접촉 사고 났어" to Category.CARE,
            // A power bank is an item, not the car battery.
            "보조배터리 두고 내렸어" to Category.ITEM,
            "보조 배터리 뒷좌석" to Category.ITEM,
            "배터리 10퍼센트예요" to Category.CHARGE,
            // Body, door and noise problems.
            "트렁크 안 닫혀" to Category.CARE,
            "트렁크에서 덜컹 소리 나" to Category.CARE,
            "문콕 당했어" to Category.CARE,
            "앞 범퍼 찌그러졌어" to Category.CARE,
            "에어컨 냄새 나" to Category.CARE,
            "연기가 나요" to Category.CARE,
            // Energy words; 경유 alone means "via".
            "경유 넣어 줘" to Category.CHARGE,
            "방전됐어" to Category.CHARGE,
            "휘발유 가득 넣었어요" to Category.CHARGE,
            "전기부족" to Category.CHARGE,
            "서울 경유해서 왔어" to Category.OTHER,
            // Items with optional spacing and particles.
            "차 키를 조수석에" to Category.ITEM,
            "키" to Category.ITEM,
            "운전석밑에 지갑" to Category.ITEM,
            "휴대폰 충전 케이블" to Category.ITEM
        ).forEach { (text, expected) -> assertEquals(text, expected, NoteClassifier.classify(text)) }
    }

    @Test fun keyIsOnlyAWholeWord() {
        assertEquals(Category.OTHER, NoteClassifier.classify("하키 연습 다녀왔어"))
        assertEquals(Category.ITEM, NoteClassifier.classify("스마트키 놓고 내렸어"))
        assertEquals(Category.ITEM, NoteClassifier.classify("내 차키는 가방에"))
    }

    @Test fun recognisesParkingSpotWording() {
        assertTrue(NoteClassifier.looksLikeParkingSpot("지하 2층 기둥 옆"))
        assertTrue(NoteClassifier.looksLikeParkingSpot("B2 C17"))
        assertFalse(NoteClassifier.looksLikeParkingSpot("우산 있어요"))
        assertFalse(NoteClassifier.looksLikeParkingSpot(null))
    }
}
