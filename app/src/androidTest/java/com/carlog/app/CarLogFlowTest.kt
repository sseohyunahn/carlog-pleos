package com.carlog.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarLogFlowTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    @Test fun initialHandoverIsVisibleAndNavigationWorks() {
        app.waitUntil(30000){app.onAllNodesWithText("확인할 전달사항").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithText("출발 전, 이어받을 이야기").assertIsDisplayed()
        app.waitUntil(15000){app.onAllNodesWithText("확인했어요").fetchSemanticsNodes().isNotEmpty()}
        app.onAllNodesWithText("확인했어요").onFirst().performClick()
        app.waitUntil(8000){app.onAllNodesWithText("확인함 · 미처리").fetchSemanticsNodes().isNotEmpty()}
        app.onAllNodesWithText("조치 완료").onFirst().performClick()
        app.onNodeWithText("지난 기록").performClick()
        app.waitUntil(8000){app.onAllNodesWithText("완료 취소").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithText("완료 취소").performScrollTo().performClick()
        app.onAllNodesWithText("인계 남기기").onFirst().performClick()
        app.onNodeWithText("이용을 마치셨나요?").assertIsDisplayed()
        app.onNodeWithText("다음 운전자 모두").assertExists()
        app.onNodeWithText("층·구역 직접 입력").performTextInput("B2 C21")
        app.onNodeWithText("전달할 내용").performScrollTo().performTextInput("장비를 회수해 주세요")
        app.onNodeWithTag("handover-editor").performScrollToNode(hasText("인계 카드 저장"))
        app.waitUntil(8000){runCatching { app.onNodeWithText("인계 카드 저장").assertIsEnabled() }.isSuccess}
        app.onNodeWithText("인계 카드 저장").performClick()
        try {
            app.waitUntil(10000){app.onAllNodesWithText("인계 카드를 저장했어요").fetchSemanticsNodes().isNotEmpty()}
        } catch(e: Throwable) {
            app.onRoot().printToLog("CarLogTest")
            val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val file=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"test-save-failure.png")
            file.outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            throw e
        }
        app.onNodeWithText("어머니  ").performClick()
        app.onNodeWithText("아버지").performClick()
        app.waitUntil(8000){app.onAllNodesWithText("장비를 회수해 주세요").fetchSemanticsNodes().isNotEmpty()}
        app.onNodeWithText("장비를 회수해 주세요").assertIsDisplayed()
    }
}
