package com.carlog.app.domain

import org.junit.Assert.*
import org.junit.Test

class HandoverRulesTest {
    private val snapshot = VehicleSnapshot(DataMode.DEMO, 1000)
    private fun handover(recipient: String? = null) = Handover(author="dad",recipient=recipient,createdAt=1000,parking="B3",snapshot=snapshot)
    @Test fun authorDoesNotReceiveOwnHandover() { assertFalse(HandoverRules.visibleTo(handover(),"dad")) }
    @Test fun commonHandoverReachesOtherDrivers() { assertTrue(HandoverRules.visibleTo(handover(),"mom")); assertTrue(HandoverRules.visibleTo(handover(),"child")) }
    @Test fun directedHandoverExcludesOtherDrivers() { assertTrue(HandoverRules.visibleTo(handover("mom"),"mom")); assertFalse(HandoverRules.visibleTo(handover("mom"),"child")) }
    @Test fun authorCannotResolveOwnMemo() { assertFalse(HandoverRules.canResolve(handover(),"dad")) }
    @Test fun demoCannotOverrideRealDrivingRestriction() { assertFalse(HandoverRules.canInteract(DataMode.DEMO,false,true)) }
    @Test fun demoDrivingLocksInput() { assertFalse(HandoverRules.canInteract(DataMode.DEMO,true,null)) }
    @Test fun ordinaryTabletCanRunDemo() { assertTrue(HandoverRules.canInteract(DataMode.DEMO,false,null)) }
    @Test fun liveModeFailsClosedOnUnknownRestriction() { assertFalse(HandoverRules.canInteract(DataMode.PLEOS,false,null)) }
    @Test fun liveRequiresExplicitUnrestrictedState() { assertTrue(HandoverRules.canInteract(DataMode.PLEOS,false,false)); assertFalse(HandoverRules.canInteract(DataMode.PLEOS,false,true)) }
    @Test fun emptyMemoIsInvalid() { assertFalse(HandoverRules.validDraft("B3", listOf(MemoDraft("  ",Category.OTHER,Priority.NORMAL)))) }
    @Test fun longMemoIsInvalid() { assertFalse(HandoverRules.validDraft("", listOf(MemoDraft("가".repeat(301),Category.OTHER,Priority.NORMAL)))) }
    @Test fun tooManyMemosAreInvalid() { assertFalse(HandoverRules.validDraft("", List(11){MemoDraft("물품",Category.ITEM,Priority.NORMAL)})) }
    @Test fun recentLocationMustBeValidAndFresh() {
        val recent = snapshot.copy(latitude=37.5,longitude=127.0,locationTime=10_000)
        assertTrue(recent.hasFreshLocation(100_000))
        // v2 contract: LocationRules.MAX_AGE_MS (10 min) and MAX_FUTURE_SKEW_MS (60 s).
        assertTrue(recent.hasFreshLocation(10_000 + LocationRules.MAX_AGE_MS))
        assertFalse(recent.hasFreshLocation(10_000 + LocationRules.MAX_AGE_MS + 1))
        assertTrue(recent.hasFreshLocation(9000))
        assertFalse(recent.hasFreshLocation(10_000 - LocationRules.MAX_FUTURE_SKEW_MS - 1))
        assertFalse(recent.copy(latitude=Double.NaN).hasFreshLocation(100_000))
        assertFalse(recent.copy(longitude=200.0).hasFreshLocation(100_000))
        assertFalse(recent.copy(locationTime=null).hasFreshLocation(100_000))
    }
    @Test fun readingDoesNotCompleteMemo() {
        val memo = Memo(handoverId=1,text="충전 확인",category=Category.CHARGE,priority=Priority.IMPORTANT)
        assertNull(memo.copy(readBy=setOf("mom")).resolvedAt)
    }

    private fun record(id: Long, author: String, createdAt: Long, recipient: String? = null, parking: String = "B3") =
        HandoverWithMemos(Handover(id = id, author = author, recipient = recipient, createdAt = createdAt, parking = parking, snapshot = snapshot), emptyList())

    @Test fun incomingCardIsTheLatestHandoverWhenAddressedToDriver() {
        val mom = record(1, "mom", 1_000, parking = "B3 · C17")
        assertEquals(mom, HandoverRules.latestIncoming(listOf(mom), "dad"))
        // Dad saved a newer handover himself: mom's older parking spot must not come back as his 받은 인계.
        val dad = record(2, "dad", 2_000, parking = "1층 A2")
        assertNull(HandoverRules.latestIncoming(listOf(dad, mom), "dad"))
        assertEquals(dad, HandoverRules.latestIncoming(listOf(mom, dad), "mom"))
    }

    @Test fun incomingCardIsHiddenWhenTheNewestHandoverIsForSomeoneElse() {
        val toDad = record(1, "child", 1_000, recipient = "dad")
        val momToChild = record(2, "mom", 2_000, recipient = "child")
        assertNull(HandoverRules.latestIncoming(listOf(toDad, momToChild), "dad"))
        assertEquals(momToChild, HandoverRules.latestIncoming(listOf(toDad, momToChild), "child"))
        assertNull(HandoverRules.latestIncoming(emptyList(), "dad"))
    }
}
