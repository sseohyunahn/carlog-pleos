package com.carlog.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlog.app.ui.CarLogApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CarLogPhoneLayoutTest {
    @get:Rule val app=createAndroidComposeRule<MainActivity>()
    private lateinit var c:CarLogController
    private fun prepare() {
        c=ViewModelProvider(app.activity)[CarLogController::class.java]
        app.waitUntil(30_000){c.records.isNotEmpty()}
        app.runOnIdle { c.keepDriver();c.resetDemo() }
        app.waitUntil(15_000){c.message=="처음 시연 상황으로 돌아왔어요"&&!c.busy}
        app.runOnIdle { c.selectDriver("mom");c.message=null;c.selectTab(0) }
    }
    private fun render(width:Int,height:Int,scale:Float,night:Boolean) {
        app.activityRule.scenario.onActivity { a -> a.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)) {
                Box(Modifier.size(width.dp,height.dp)) { CarLogApp(c,night) }
            }
        } }
        app.waitForIdle()
    }
    private fun capture(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir,"phone-$name.png").outputStream().use { app.onNodeWithTag("app-surface").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun assertNavigation() {
        for(title in listOf("인계 보드","인계 남기기","지난 기록","설정")) {
            val node=app.onNodeWithText(title,useUnmergedTree=true)
            node.assertIsDisplayed()
            val results=mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            check(results.isNotEmpty() && results.none { it.hasVisualOverflow }) { "Phone navigation clips: $title; results=${results.map { "size=${it.size}, lines=${it.lineCount}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}, font=${it.layoutInput.style.fontSize}" }}" }
        }
        app.onNodeWithTag("driver-button").assertIsDisplayed()
    }
    @Test fun phoneSizesThemesAndLargeFont() {
        prepare()
        app.activityRule.scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT }
        for((width,scale) in listOf(320 to 1f,360 to 1.3f,411 to 1f)) for(night in listOf(false,true)) {
            val id="$width-${if(night)"night" else "day"}-${if(scale>1f)"large" else "normal"}"
            app.runOnIdle { c.message=null;c.selectTab(0) }
            render(width,780,scale,night)
            capture("$id-board");assertNavigation()
            app.onNodeWithText("인계 남기기").performClick()
            app.onNodeWithText("층·구역 직접 입력").performScrollTo().assertIsDisplayed()
            capture("$id-editor")
            app.onNodeWithText("인계 카드 저장").performScrollTo().assertIsDisplayed()
            app.onNodeWithText("지난 기록").performClick()
            app.onNodeWithTag("history-list").assertIsDisplayed();capture("$id-history")
            app.onNodeWithText("설정").performClick()
            app.onNodeWithTag("settings-list").assertIsDisplayed();capture("$id-settings")
            app.onNodeWithTag("settings-list").performScrollToNode(hasText("주행 중 입력 잠금 시연"))
            app.onNodeWithText("주행 중 입력 잠금 시연").performClick()
            app.onNodeWithTag("driving-lock").assertIsDisplayed()
            app.onNodeWithText("정차 상태로 돌아가기").performScrollTo().performClick()
        }
    }
    @Test fun landscapeAndRotationKeepDraft() {
        prepare()
        app.runOnIdle { c.selectTab(1);c.updateParking("B2층 C21구역");c.updateText("휴대폰 회전 후에도 보존할 메모") }
        app.activityRule.scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        app.waitUntil(15_000) { app.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE }
        render(720,300,1.3f,false)
        capture("landscape-before-check");assertNavigation()
        app.onNodeWithText("전달할 내용").performScrollTo().assertExists()
        check(c.editorParking=="B2층 C21구역" && c.editorText=="휴대폰 회전 후에도 보존할 메모")
        app.onNodeWithText("인계 카드 저장").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("landscape-large-editor")
        app.onNodeWithTag("driver-button").performClick()
        app.onNodeWithTag("driver-option-mom").performScrollTo().assertIsDisplayed().performClick()
        app.runOnIdle { c.selectTab(0) }
        capture("landscape-large-board")
    }
}
