package com.carlog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarLogFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()

    private fun exists(text: String, substring: Boolean = false) =
        app.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    @Test fun driverPickerIncomingCardAndHandoverFlow() {
        // Fresh install (or back after 20+ minutes): "이번 운전자는 누구인가요?" comes first.
        app.waitUntil(30000) { exists("이번 운전자는 누구인가요?") || exists("인계 보드") }
        if (!exists("이번 운전자는 누구인가요?")) app.onNodeWithTag("driver-button").performClick()
        app.onNodeWithTag("driver-option-mom").performClick()
        app.waitUntil(8000) { !exists("이번 운전자는 누구인가요?") }
        val controller = ViewModelProvider(app.activity)[CarLogController::class.java]
        app.runOnIdle { controller.resetDemo() }
        app.waitUntil(15_000) { controller.message == "처음 시연 상황으로 돌아왔어요" && !controller.busy }
        app.runOnIdle { controller.message = null; controller.selectTab(0) }
        app.waitUntil(30000) { exists("출발 전, 이어받을 이야기") }
        app.onNodeWithText("출발 전, 이어받을 이야기").assertIsDisplayed()

        // "받은 인계" card from the demo seed (아버지 -> everyone).
        app.waitUntil(15000) { exists("아버지님이 남긴 인계", substring = true) }
        app.onNodeWithTag("incoming-card").assertExists()
        app.onNodeWithText("확인할 메모", substring = true).assertExists()
        app.onNodeWithTag("read-aloud").assertExists()

        // Read and resolve stay separate.
        app.waitUntil(15000) {
            runCatching { app.onNodeWithTag("board").performScrollToNode(hasText("확인했어요")) }.isSuccess && exists("확인했어요")
        }
        app.onAllNodesWithText("확인했어요").onFirst().performScrollTo().performClick()
        app.waitUntil(8000) { exists("확인함 · 미처리") }
        app.onAllNodesWithText("조치 완료").onFirst().performScrollTo().performClick()
        app.onNodeWithText("지난 기록").performClick()
        app.waitUntil(8000) { exists("완료 취소") }
        app.onNodeWithText("완료 취소").performScrollTo().performClick()

        // Editor: reuse the last parking spot (with a warning), then type a new one.
        app.onAllNodesWithText("인계 남기기").onFirst().performClick()
        app.onNodeWithText("이용을 마치셨나요?").assertIsDisplayed()
        app.onNodeWithText("다음 운전자 모두").assertExists()
        app.onNodeWithTag("voice-memo").assertExists()
        app.waitUntil(8000) { exists("지난 주차 위치 사용") }
        app.onNodeWithText("지난 주차 위치 사용").performScrollTo().performClick()
        app.waitUntil(5000) { exists("이번 주차와 다를 수 있으니", substring = true) }
        app.onNodeWithText("층·구역 직접 입력").performTextClearance()
        app.onNodeWithText("층·구역 직접 입력").performTextInput("B2 C21")
        app.waitUntil(5000) { !exists("이번 주차와 다를 수 있으니", substring = true) }
        app.onNodeWithText("전달할 내용").performScrollTo().performTextInput("장비를 회수해 주세요")
        app.onNodeWithText("인계 카드 저장").performScrollTo()
        app.waitUntil(8000) { runCatching { app.onNodeWithText("인계 카드 저장").assertIsEnabled() }.isSuccess }
        app.onNodeWithText("인계 카드 저장").performClick()
        try {
            app.waitUntil(15000) { exists("인계 카드를 저장했어요", substring = true) }
        } catch (e: Throwable) {
            app.onRoot().printToLog("CarLogTest")
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "test-save-failure.png")
            file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            throw e
        }

        // The author sees the memo under "내가 남긴 메모" with a delete button.
        app.waitUntil(8000) {
            runCatching { app.onNodeWithTag("board").performScrollToNode(hasText("내가 남긴 메모")) }.isSuccess
        }

        // Switch to 아버지: the new memo arrives as an incoming memo.
        app.onNodeWithTag("driver-button").performClick()
        app.onNodeWithTag("driver-option-dad").performClick()
        app.waitUntil(8000) {
            runCatching { app.onNodeWithTag("board").performScrollToNode(hasText("장비를 회수해 주세요")) }.isSuccess
        }
        app.onNodeWithText("장비를 회수해 주세요").assertIsDisplayed()
        app.onNodeWithTag("board").performScrollToIndex(0)
        app.onNodeWithText("어머니님이 남긴 인계", substring = true).assertExists()
    }
}
