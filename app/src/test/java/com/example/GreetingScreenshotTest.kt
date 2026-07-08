package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun settings_screen_screenshot() {
        val botTokenFlow = MutableStateFlow("123456789:ABCDefGhIJKlmNoPQRst")
        val chatIdFlow = MutableStateFlow("-1001234567890")
        val autoBackupPhotosFlow = MutableStateFlow(true)
        val autoBackupVideosFlow = MutableStateFlow(false)
        val originalQualityFlow = MutableStateFlow(true)

        composeTestRule.setContent {
            MyApplicationTheme {
                SettingsScreen(
                    botTokenFlow = botTokenFlow,
                    chatIdFlow = chatIdFlow,
                    autoBackupPhotosFlow = autoBackupPhotosFlow,
                    autoBackupVideosFlow = autoBackupVideosFlow,
                    originalQualityFlow = originalQualityFlow,
                    onSaveSettings = { _, _, _, _, _ -> },
                    onViewGuide = {}
                )
            }
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
    }
}
