package com.carlog.app

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlog.app.ui.CarLogApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Captures actual Compose screens, and checks that the lock hides input in both themes. */
@RunWith(AndroidJUnit4::class)
class CarLogNativeDesignTest {
    @get:Rule val app = createAndroidComposeRule<MainActivity>()
    private lateinit var controller: CarLogController
    private fun exists(text: String) = app.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    private fun capture(name: String) {
        app.waitForIdle()
        val bitmap = app.onNodeWithTag("app-surface").captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, "native-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun render(night: Boolean, half: Boolean) {
        app.activityRule.scenario.onActivity { activity ->
            controller = ViewModelProvider(activity)[CarLogController::class.java]
            activity.setContent {
                if (half) Box(Modifier.width(1060.dp).fillMaxHeight()) { CarLogApp(controller, night) }
                else CarLogApp(controller, night)
            }
        }
        app.waitForIdle()
    }
    @Test fun referenceDesignScreensAndLock() {
        app.waitUntil(30_000) { exists("이번 운전자는 누구인가요?") || exists("인계 보드") }
        render(night = true, half = false)
        if (exists("이번 운전자는 누구인가요?")) {
            capture("driver-night")
            app.onNodeWithTag("driver-option-mom").performClick()
        }
        app.runOnIdle { controller.resetDemo() }
        app.waitUntil(15_000) { controller.message == "처음 시연 상황으로 돌아왔어요" && !controller.busy }
        app.runOnIdle { controller.selectDriver("mom") }
        app.waitUntil(15_000) { controller.driver == "mom" && !controller.busy && controller.latestIncoming != null }
        app.runOnIdle { controller.message = null; controller.selectTab(0) }
        capture("board-night-full")
        render(night = true, half = true)
        capture("board-night-half")
        render(night = false, half = true)
        // A render regression used to leave the navy tag blank after switching to day.
        val dayTag = app.onNodeWithTag("incoming-card").captureToImage().asAndroidBitmap()
        var visibleLightPixels = 0
        for (y in 120 until dayTag.height) for (x in 0 until dayTag.width) {
            val pixel = dayTag.getPixel(x, y)
            if (android.graphics.Color.red(pixel) > 235 && android.graphics.Color.green(pixel) > 235 && android.graphics.Color.blue(pixel) > 235) visibleLightPixels++
        }
        check(visibleLightPixels > 500) { "Day handover tag lost its text and action rendering" }
        capture("board-day-half")
        app.onNodeWithText("인계 남기기").performClick()
        app.onNodeWithTag("voice-memo").assertExists()
        capture("editor-day-half")
        app.onNodeWithText("지난 기록").performClick()
        app.onNodeWithTag("history-list").assertExists()
        capture("history-day-half")
        app.onNodeWithText("설정").performClick()
        capture("settings-day-half")
        app.onNodeWithTag("settings-list").performScrollToNode(hasText("주행 중 입력 잠금 시연"))
        app.onNodeWithText("주행 중 입력 잠금 시연").performClick()
        app.onNodeWithTag("driving-lock").assertIsDisplayed()
        app.onNodeWithText("주행 중에는 잠시 쉬어갈게요").assertIsDisplayed()
        capture("lock-day-half")
        app.onNodeWithText("정차 상태로 돌아가기").performClick()
        render(night = true, half = true)
        app.runOnIdle { controller.simulateMoving() }
        app.onNodeWithTag("driving-lock").assertIsDisplayed()
        capture("lock-night-half")
        app.runOnIdle { controller.simulateMoving() }
    }
}
