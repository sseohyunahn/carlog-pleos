package com.carlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechScriptTest {
    private val minute = 60_000L
    private val hour = 60 * minute
    private val now = 100 * 24 * hour

    private fun snapshot(
        battery: Int? = null, fuel: Int? = null, range: Float? = null,
        doors: Int? = null, windows: Int? = null
    ) = VehicleSnapshot(
        DataMode.DEMO, now - 2 * hour, battery = battery, fuel = fuel, rangeKm = range,
        unlockedDoors = doors, openWindows = windows
    )

    private fun handover(
        author: String = "dad", recipient: String? = null, parking: String = "B3층 C17구역",
        parkingBy: String? = null, parkingAt: Long? = null, snapshot: VehicleSnapshot = snapshot()
    ) = Handover(1, author, recipient, now - 2 * hour, parking, snapshot, parkingBy, parkingAt)

    private fun memo(id: Long, text: String, category: Category = Category.OTHER, priority: Priority = Priority.NORMAL, resolvedBy: String? = null) =
        Memo(id, 1, text, category, priority, resolvedBy = resolvedBy)

    @Test fun readsAuthorTimeParkingVehicleAndMemosInOrder() {
        val record = HandoverWithMemos(
            handover(snapshot = snapshot(battery = 78, range = 312.4f, windows = 1)),
            listOf(memo(1, "뒷좌석에 우산 있어요", Category.ITEM))
        )
        val script = SpeechScript.build(record, now)
        assertTrue(script, script.startsWith("아버지께서 2시간 전에 남긴 인계예요."))
        assertTrue(script, script.contains("주차 위치를 알려드릴게요. B3층 C17구역."))
        assertTrue(script, script.contains("배터리 78퍼센트, 주행 가능 거리 312킬로미터예요."))
        assertTrue(script, script.contains("열린 창문이 있었어요."))
        assertFalse(script, script.contains("잠기지 않은 문"))
        assertTrue(script, script.contains("확인할 메모가 1건 있어요. 1번, 물품. 뒷좌석에 우산 있어요."))
        assertTrue(script, script.endsWith(SpeechScript.CLOSING))
        val order = listOf("남긴 인계", "주차 위치", "배터리", "열린 창문", "확인할 메모", SpeechScript.CLOSING).map { script.indexOf(it) }
        assertEquals(order.sorted(), order)
    }

    @Test fun namesTheRecipientWhenDirected() {
        val script = SpeechScript.build(HandoverWithMemos(handover(recipient = "mom"), emptyList()), now)
        assertTrue(script, script.startsWith("아버지께서 2시간 전에 어머니께 남긴 인계예요."))
    }

    @Test fun warnsWhenParkingWasRecordedBySomeoneElse() {
        val record = HandoverWithMemos(handover(author = "mom", parkingBy = "dad", parkingAt = now - 3 * hour), emptyList())
        val script = SpeechScript.build(record, now)
        assertTrue(script, script.contains("주차 위치는 아버지께서 3시간 전에 기록했어요. 이번 주차와 다를 수 있어요."))
    }

    @Test fun noWarningWhenTheAuthorRecordedParking() {
        val same = SpeechScript.build(HandoverWithMemos(handover(parkingBy = "dad", parkingAt = now), emptyList()), now)
        val unknown = SpeechScript.build(HandoverWithMemos(handover(parkingBy = null), emptyList()), now)
        assertFalse(same, same.contains("다를 수 있어요"))
        assertFalse(unknown, unknown.contains("다를 수 있어요"))
    }

    @Test fun fuelIsUsedWhenThereIsNoBatteryAndDoorsAreWarned() {
        val script = SpeechScript.build(HandoverWithMemos(handover(snapshot = snapshot(fuel = 40, doors = 2)), emptyList()), now)
        assertTrue(script, script.contains("연료 40퍼센트예요."))
        assertTrue(script, script.contains("잠기지 않은 문이 있었어요."))
    }

    @Test fun missingVehicleValuesAreSkipped() {
        val script = SpeechScript.build(HandoverWithMemos(handover(), emptyList()), now)
        assertFalse(script, script.contains("퍼센트"))
        assertFalse(script, script.contains("킬로미터"))
    }

    @Test fun importantMemosComeFirstAndOnlyThreeAreRead() {
        val memos = (1L..6L).map { memo(it, "메모 $it") } + memo(7, "트렁크 확인", Category.ITEM, Priority.IMPORTANT)
        val script = SpeechScript.build(HandoverWithMemos(handover(), memos), now)
        assertTrue(script, script.contains("확인할 메모가 7건 있어요."))
        assertTrue(script, script.contains("1번, 중요, 물품. 트렁크 확인."))
        assertTrue(script, script.contains("2번, 기타. 메모 1."))
        assertTrue(script, script.contains("3번, 기타. 메모 2."))
        assertFalse(script, script.contains("메모 3"))
        assertTrue(script, script.contains("외 4건은 정차 후 화면에서 확인해 주세요."))
    }

    @Test fun careMemosAreReadBeforeOtherNormalMemosButAfterImportantOnes() {
        val memos = listOf(
            memo(1, "우산 있어요", Category.ITEM), memo(2, "충전 부탁해요", Category.CHARGE), memo(3, "세차 부탁", Category.OTHER),
            memo(4, "지갑 두고 감", Category.ITEM), memo(5, "브레이크 경고등 켜졌어", Category.CARE),
            memo(6, "열쇠 확인", Category.ITEM, Priority.IMPORTANT)
        )
        val script = SpeechScript.build(HandoverWithMemos(handover(), memos), now)
        assertTrue(script, script.contains("1번, 중요, 물품. 열쇠 확인."))
        assertTrue(script, script.contains("2번, 차량 관리. 브레이크 경고등 켜졌어."))
        assertTrue(script, script.contains("3번, 물품. 우산 있어요."))
        assertTrue(script, script.contains("외 3건은"))
    }

    @Test fun careMemoBeyondTheSpokenLimitIsStillRead() {
        val memos = (1L..4L).map { memo(it, "메모 $it") } + memo(5, "타이어 펑크 났어요", Category.CARE)
        val script = SpeechScript.build(HandoverWithMemos(handover(), memos), now)
        assertTrue(script, script.contains("1번, 차량 관리. 타이어 펑크 났어요."))
    }

    @Test fun longMemosAreCutAndTheScriptStaysShort() {
        val long = (1..60).joinToString(" ") { "가나다라" }.take(300)
        val memos = (1L..10L).map { memo(it, long, Category.CARE, if (it % 2 == 0L) Priority.IMPORTANT else Priority.NORMAL) }
        // 10 x 300-char memos and an 80-char parking spot: well under a minute of speech.
        val plain = SpeechScript.build(HandoverWithMemos(handover(parking = "가".repeat(80)), memos), now)
        assertTrue("${plain.length}: $plain", plain.length <= 500)
        // Worst case: every vehicle warning and a parking spot recorded by someone else on top.
        val record = HandoverWithMemos(
            handover(recipient = "child", parking = "가".repeat(80), parkingBy = "mom", parkingAt = now - hour,
                snapshot = snapshot(battery = 100, range = 512f, doors = 1, windows = 2)),
            memos
        )
        val script = SpeechScript.build(record, now)
        assertTrue("${script.length}: $script", script.length <= 600)
        assertTrue(script, script.contains("긴 메모는 정차 후 화면에서 끝까지 확인해 주세요."))
        assertTrue(script, script.contains("외 7건은 정차 후 화면에서 확인해 주세요."))
        assertFalse(script, script.contains(long))
        assertTrue(script, script.endsWith(SpeechScript.CLOSING))
    }

    @Test fun longMemoIsCutAtAWordBoundary() {
        val (text, cut) = SpeechScript.spokenMemo((1..40).joinToString(" ") { "단어$it" })
        assertTrue(cut)
        assertTrue(text, text.endsWith("…"))
        assertTrue(text, text.length <= SpeechScript.MAX_SPOKEN_MEMO_CHARS + 1)
        val words = text.removeSuffix("…").split(' ')
        assertTrue(text, words.all { it.matches(Regex("단어\\d+")) })
        assertEquals(Pair("짧은 메모.", false), SpeechScript.spokenMemo("짧은 메모"))
    }

    @Test fun lineBreaksInAMemoBecomeSentenceBreaks() {
        val script = SpeechScript.build(HandoverWithMemos(handover(), listOf(memo(1, "트렁크에 우산\n내일 충전 부탁해요.\n\n고마워요"))), now)
        assertTrue(script, script.contains("트렁크에 우산. 내일 충전 부탁해요. 고마워요."))
    }

    @Test fun recipientAndAuthorUseNaturalHonorifics() {
        val child = SpeechScript.build(HandoverWithMemos(handover(author = "child", recipient = "dad"), emptyList()), now)
        assertTrue(child, child.startsWith("자녀가 2시간 전에 아버지께 남긴 인계예요."))
        val toChild = SpeechScript.build(HandoverWithMemos(handover(author = "mom", recipient = "child"), emptyList()), now)
        assertTrue(toChild, toChild.startsWith("어머니께서 2시간 전에 자녀에게 남긴 인계예요."))
        val unknown = SpeechScript.build(HandoverWithMemos(handover(author = "ghost", recipient = "nobody"), emptyList()), now)
        assertTrue(unknown, unknown.startsWith("가족 중 한 명이 2시간 전에 남긴 인계예요."))
        assertFalse(unknown, unknown.contains("님"))
    }

    @Test fun resolvedMemosAreSkippedUnlessAllAreRequested() {
        val memos = listOf(memo(1, "충전 필요", Category.CHARGE, resolvedBy = "mom"), memo(2, "세차했어요", Category.CARE))
        val open = SpeechScript.build(HandoverWithMemos(handover(), memos), now)
        assertTrue(open, open.contains("확인할 메모가 1건 있어요. 1번, 차량 관리. 세차했어요."))
        assertFalse(open, open.contains("충전 필요"))
        val all = SpeechScript.build(HandoverWithMemos(handover(), memos), now, unresolvedOnly = false)
        assertTrue(all, all.contains("남긴 메모가 2건 있어요."))
        assertTrue(all, all.contains("충전 주유. 충전 필요."))
    }

    @Test fun saysWhenThereIsNothingToCheck() {
        val script = SpeechScript.build(HandoverWithMemos(handover(parking = "  "), emptyList()), now)
        assertTrue(script, script.contains("이번 인계에는 주차 위치 기록이 없어요."))
        assertTrue(script, script.contains("확인할 메모는 없어요."))
    }

    @Test fun relativeTimesAreDeterministic() {
        assertEquals("방금", SpeechScript.relativeWhen(now - 30_000, now))
        assertEquals("방금", SpeechScript.relativeWhen(now + 5 * minute, now))
        assertEquals("5분 전에", SpeechScript.relativeWhen(now - 5 * minute, now))
        assertEquals("2시간 전에", SpeechScript.relativeWhen(now - 2 * hour, now))
        assertEquals("하루 전에", SpeechScript.relativeWhen(now - 30 * hour, now))
        assertEquals("3일 전에", SpeechScript.relativeWhen(now - 3 * 24 * hour, now))
    }

    @Test fun sameInputGivesTheSameScript() {
        val record = HandoverWithMemos(handover(snapshot = snapshot(battery = 50)), listOf(memo(1, "고마워요")))
        assertEquals(SpeechScript.build(record, now), SpeechScript.build(record, now))
    }
}
