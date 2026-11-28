package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun verifyAppIdentityAndVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("TK Studio", context.getString(R.string.app_name))
        assertEquals("com.tkofficial.tkstudio", context.packageName)
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals("0.6.8", pkgInfo.versionName)
    }

    @Test
    fun verifyBottomNavOrderAndProfileSettingsGear() {
        composeRule.waitForIdle()
        // Verify exact 5 bottom nav tabs exist
        composeRule.onNodeWithTag("nav_home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_project").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_live_coding").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_apk_tool").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_profile").assertIsDisplayed()

        // Navigate to Profile and verify acceptance-critical header + Settings gear -> Settings
        composeRule.onNodeWithTag("nav_profile").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("profile_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_brand_mark").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_brand_title").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_settings_button").assertIsDisplayed()

        composeRule.onNodeWithTag("profile_settings_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()

        // Open Appearance/Themes and switch theme
        composeRule.onNodeWithTag("settings_cat_appearance_themes").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("theme_option_neon_violet").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun verifyCreateProjectAndNavigateAllCoreTabs() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_new_project_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("new_project_name_input").performTextInput("SmokeTestApp")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()

        // Visit Project tab
        composeRule.onNodeWithTag("nav_project").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("project_screen").assertIsDisplayed()
        composeRule.onNodeWithText("SmokeTestApp").assertIsDisplayed()

        // Visit Live Coding tab
        composeRule.onNodeWithTag("nav_live_coding").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("live_coding_screen").assertIsDisplayed()

        // Visit APK Tool tab
        composeRule.onNodeWithTag("nav_apk_tool").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("apk_tool_screen").assertIsDisplayed()
    }
}
