package de.haberland.meilists

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun drawerOpensFromMenuButton() {
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase("meilists_database")

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.onNodeWithContentDescription("Menü").performClick()
            composeTestRule.onNodeWithText("Kategorien").assertExists()
        }
    }
}
