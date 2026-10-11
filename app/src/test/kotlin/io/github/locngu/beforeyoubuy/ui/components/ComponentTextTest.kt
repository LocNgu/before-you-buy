package io.github.locngu.beforeyoubuy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.locngu.beforeyoubuy.core.domain.money.Money
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the shared components say, in each language. Their look is covered by [ComponentScreenshotTest]. */
@RunWith(AndroidJUnit4::class)
class ComponentTextTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "en")
    fun `countdown in English`() {
        composeRule.setContent {
            BeforeYouBuyTheme {
                Column {
                    CountdownText(daysLeft = 23)
                    CountdownText(daysLeft = 1)
                    CountdownText(daysLeft = 0)
                    CountdownText(daysLeft = -4)
                }
            }
        }
        composeRule.onNodeWithText("23 days left").assertIsDisplayed()
        composeRule.onNodeWithText("1 day left").assertIsDisplayed()
        composeRule.onAllNodesWithText("Cooling-off over").assertCountEquals(2)
    }

    @Test
    @Config(qualifiers = "de")
    fun `countdown in German`() {
        composeRule.setContent {
            BeforeYouBuyTheme {
                Column {
                    CountdownText(daysLeft = 23)
                    CountdownText(daysLeft = 1)
                    CountdownText(daysLeft = 0)
                }
            }
        }
        composeRule.onNodeWithText("Noch 23 Tage").assertIsDisplayed()
        composeRule.onNodeWithText("Noch 1 Tag").assertIsDisplayed()
        composeRule.onNodeWithText("Wartezeit vorbei").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "de-rDE")
    fun `money follows the UI language`() {
        composeRule.setContent { BeforeYouBuyTheme { MoneyText(Money(123_456, "EUR")) } }
        composeRule.onNodeWithText("1.234,56 €").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rGB")
    fun `money in British English`() {
        composeRule.setContent { BeforeYouBuyTheme { MoneyText(Money(123_456, "EUR")) } }
        composeRule.onNodeWithText("€1,234.56").assertIsDisplayed()
    }

    @Test
    fun `section header is a heading`() {
        composeRule.setContent { BeforeYouBuyTheme { SectionHeader("Priorities") } }
        composeRule.onNode(isHeading()).assertIsDisplayed()
        composeRule.onNodeWithText("Priorities").assertIsDisplayed()
    }

    @Test
    fun `empty state shows title, body and action`() {
        var clicked = false
        composeRule.setContent {
            BeforeYouBuyTheme {
                EmptyState(title = "Title", body = "Body", action = {
                    PrimaryButton("Act", onClick = { clicked = true })
                })
            }
        }
        composeRule.onNodeWithText("Title").assertIsDisplayed()
        composeRule.onNodeWithText("Body").assertIsDisplayed()
        composeRule.onNodeWithText("Act").performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun `both buttons of a pair react and can be disabled`() {
        val clicks = mutableListOf<String>()
        composeRule.setContent {
            BeforeYouBuyTheme {
                Column {
                    ButtonPair(
                        start = { SecondaryButton("Let it go", onClick = { clicks += "start" }, modifier = it) },
                        end = { PrimaryButton("Buy", onClick = { clicks += "end" }, modifier = it) }
                    )
                    ButtonPair(
                        start = { SecondaryButton("Wait", onClick = {}, modifier = it, enabled = false) },
                        end = { PrimaryButton("Later", onClick = {}, modifier = it, enabled = false) }
                    )
                }
            }
        }
        composeRule.onNodeWithText("Let it go").performClick()
        composeRule.onNodeWithText("Buy").performClick()
        assertEquals(listOf("start", "end"), clicks)
        composeRule.onNodeWithText("Wait").assertIsNotEnabled()
        composeRule.onNodeWithText("Later").assertIsNotEnabled()
    }
}
