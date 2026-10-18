package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test

class GreetingScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appThemeRendersContent() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Text("SVIT Smart Attendance")
            }
        }

        composeTestRule.onNodeWithText("SVIT Smart Attendance").assertExists()
    }
}
