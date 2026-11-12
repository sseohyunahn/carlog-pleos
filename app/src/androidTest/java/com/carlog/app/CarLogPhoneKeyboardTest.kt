package com.carlog.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CarLogPhoneKeyboardTest {
    @get:Rule val app=createAndroidComposeRule<MainActivity>()
    private fun screenshot(name:String) {
        app.waitForIdle()
        val inst=InstrumentationRegistry.getInstrumentation()
        File(inst.targetContext.filesDir,"phone-$name.png").outputStream().use { inst.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun softwareKeyboardRotationAndSave() {
        app.activityRule.scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        app.waitUntil(15_000){app.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
        val c=ViewModelProvider(app.activity)[CarLogController::class.java]
        app.waitUntil(30_000){c.records.isNotEmpty()}
        app.runOnIdle { c.keepDriver();c.selectDriver("mom");c.resetDemo() }
        app.waitUntil(15_000){c.message=="처음 시연 상황으로 돌아왔어요"&&!c.busy}
        app.runOnIdle { c.message=null;c.selectTab(1) }
        app.onNodeWithText("층·구역 직접 입력").performScrollTo().performClick()
        app.waitUntil(10_000){app.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true}
        app.onNodeWithText("층·구역 직접 입력").performTextInput("B5층 D12구역")
        app.onNodeWithText("전달할 내용").performScrollTo().performClick().performTextInput("회전해도 보존되는 휴대폰 검증 메모")
        screenshot("keyboard-portrait")
        app.activityRule.scenario.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        app.waitUntil(15_000){app.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
        check(c.editorParking=="B5층 D12구역"&&c.editorText=="회전해도 보존되는 휴대폰 검증 메모")
        app.activityRule.scenario.onActivity { (it.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.window.decorView.windowToken,0) }
        app.waitForIdle()
        app.onNodeWithText("인계 카드 저장").performScrollTo().assertIsDisplayed().performClick()
        app.waitUntil(15_000){c.message?.contains("인계 카드를 저장했어요")==true}
        screenshot("landscape-saved")
    }
}
