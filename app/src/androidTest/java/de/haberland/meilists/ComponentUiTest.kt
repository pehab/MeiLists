package de.haberland.meilists

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.haberland.meilists.model.CatalogArea
import de.haberland.meilists.model.CatalogProduct
import de.haberland.meilists.model.Category
import de.haberland.meilists.model.ListItem
import de.haberland.meilists.model.ShoppingList
import de.haberland.meilists.ui.components.ListItemRow
import de.haberland.meilists.ui.dialogs.AddEntryDialog
import de.haberland.meilists.ui.dialogs.AddType
import de.haberland.meilists.ui.dialogs.JoinCategoryDialog
import de.haberland.meilists.ui.dialogs.MoveItemDialog
import de.haberland.meilists.ui.dialogs.SettingsDialog
import de.haberland.meilists.ui.theme.MeiListsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ComponentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun listItemRowDisplaysAreaAndInvokesCallbacks() {
        var checkedValue: Boolean? = null
        var editClicked = false
        var moveClicked = false
        var deleteClicked = false

        composeTestRule.setContent {
            MeiListsTheme {
                ListItemRow(
                    item = ListItem(
                        id = "item1",
                        listId = "list1",
                        text = "Milk",
                        isChecked = false,
                        timestamp = 1L,
                        area = "Dairy"
                    ),
                    onCheckedChange = { checkedValue = it },
                    onEditClick = { editClicked = true },
                    onMoveClick = { moveClicked = true },
                    onDeleteClick = { deleteClicked = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Milk").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dairy").assertIsDisplayed()

        composeTestRule.onAllNodes(isToggleable())[0].performClick()
        composeTestRule.onNodeWithContentDescription("Bearbeiten").performClick()
        composeTestRule.onNodeWithContentDescription("Verschieben").performClick()
        composeTestRule.onNodeWithContentDescription("Löschen").performClick()

        composeTestRule.runOnIdle {
            assertEquals(true, checkedValue)
            assertTrue(editClicked)
            assertTrue(moveClicked)
            assertTrue(deleteClicked)
        }
    }

    @Test
    fun addEntryDialogUsesProductSuggestionDefaultArea() {
        var confirmed: ConfirmedEntry? = null

        composeTestRule.setContent {
            MeiListsTheme {
                AddEntryDialog(
                    type = AddType.ITEM,
                    onDismiss = {},
                    onConfirm = { name, color, area, importId, importAreas, importProducts ->
                        confirmed = ConfirmedEntry(name, color, area, importId, importAreas, importProducts)
                    },
                    catalogProducts = listOf(
                        CatalogProduct(id = "product1", categoryId = "cat1", name = "Milk", defaultArea = "Dairy")
                    ),
                    catalogAreas = listOf(
                        CatalogArea(id = "area1", categoryId = "cat1", name = "Dairy")
                    ),
                    allCategories = emptyList()
                )
            }
        }

        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("Mi")
        composeTestRule.onNodeWithText("Milk (Dairy)").performClick()
        composeTestRule.onNodeWithText("Hinzufügen").performClick()

        composeTestRule.runOnIdle {
            assertEquals("Milk", confirmed?.name)
            assertEquals("Dairy", confirmed?.area)
            assertEquals(null, confirmed?.color)
            assertEquals(null, confirmed?.importId)
        }
    }

    @Test
    fun settingsDialogSavesAutoLearningToggle() {
        var savedHideChecked: Boolean? = null
        var savedAutoLearning: Boolean? = null

        composeTestRule.setContent {
            MeiListsTheme {
                SettingsDialog(
                    category = Category(id = "cat1", name = "Groceries"),
                    onDismiss = {},
                    onSave = { hideChecked, _, autoLearningEnabled ->
                        savedHideChecked = hideChecked
                        savedAutoLearning = autoLearningEnabled
                    },
                    onDelete = {},
                    allCategories = emptyList()
                )
            }
        }

        composeTestRule.onNodeWithText("Autolearning aktivieren").assertIsDisplayed()
        composeTestRule.onAllNodes(isToggleable())[1].assertIsOn()
        composeTestRule.onAllNodes(isToggleable())[1].performClick()
        composeTestRule.onAllNodes(isToggleable())[1].assertIsOff()
        composeTestRule.onNodeWithText("Speichern").performClick()

        composeTestRule.runOnIdle {
            assertEquals(false, savedHideChecked)
            assertEquals(false, savedAutoLearning)
        }
    }

    @Test
    fun moveItemDialogSubmitsSelectedTargetList() {
        var targetListId: String? = null

        composeTestRule.setContent {
            MeiListsTheme {
                MoveItemDialog(
                    listItem = ListItem(id = "item1", listId = "weekly", text = "Milk"),
                    categories = listOf(
                        Category(id = "groceries", name = "Groceries"),
                        Category(id = "hardware", name = "Hardware")
                    ),
                    lists = listOf(
                        ShoppingList(id = "weekly", categoryId = "groceries", name = "Weekly"),
                        ShoppingList(id = "party", categoryId = "groceries", name = "Party"),
                        ShoppingList(id = "tools", categoryId = "hardware", name = "Tools")
                    ),
                    onDismiss = {},
                    onConfirm = { targetListId = it }
                )
            }
        }

        composeTestRule.onNodeWithText("Aktuelle Liste").assertIsDisplayed()
        composeTestRule.onNodeWithText("Party").performClick()
        composeTestRule.onNodeWithText("Verschieben").performClick()

        composeTestRule.runOnIdle {
            assertEquals("party", targetListId)
        }
    }

    @Test
    fun joinCategoryDialogSubmitsEnteredCode() {
        var submittedCode: String? = null

        composeTestRule.setContent {
            MeiListsTheme {
                JoinCategoryDialog(
                    onDismiss = {},
                    onConfirm = { submittedCode = it }
                )
            }
        }

        composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("cat-code")
        composeTestRule.onNodeWithText("Beitreten").performClick()

        composeTestRule.runOnIdle {
            assertEquals("cat-code", submittedCode)
        }
    }

    private data class ConfirmedEntry(
        val name: String,
        val color: Color?,
        val area: String?,
        val importId: String?,
        val importAreas: Boolean,
        val importProducts: Boolean
    )
}
