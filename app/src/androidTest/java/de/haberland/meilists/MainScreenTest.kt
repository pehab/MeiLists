package de.haberland.meilists

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppTitleIsVisible() {
        // Check if the app title is visible in the top bar or drawer
        composeTestRule.onNodeWithText("MeiLists").assertExists()
    }

    @Test
    fun testEmptyStateMessage() {
        // If no category is selected, the empty state message should be shown
        // Note: This depends on the initial state of the ViewModel
        // If the database is empty, it should show the message.
        composeTestRule.onNodeWithText("Bitte wähle eine Kategorie im Menü aus.").assertExists()
    }

    @Test
    fun testOpenDrawer() {
        // Find the menu icon by content description and click it
        composeTestRule.onNodeWithText("Menü").performClick()
        
        // After clicking, "Kategorien" header in the drawer should be visible
        composeTestRule.onNodeWithText("Kategorien").assertExists()
    }
}
