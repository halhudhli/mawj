package com.nuvio.app.features.luxo

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w960dp-h540dp-land")
class LuxoTvNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteMovesAndRestoresInEnglish() = checkNavigation(LayoutDirection.Ltr, Key.DirectionRight)
    @Test fun remoteMovesAndRestoresInArabic() = checkNavigation(LayoutDirection.Rtl, Key.DirectionLeft)

    private fun checkNavigation(direction: LayoutDirection, forward: Key) {
        val first = FocusRequester()
        var clicks = 0
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Column {
                    repeat(2) { row ->
                        LazyRow(Modifier.luxoTvRowFocus(enabled = true)) {
                            items(8, key = { it }) { index ->
                                Box(
                                    Modifier.size(180.dp, 120.dp)
                                        .testTag("$row:$index")
                                        .then(if (row == 0 && index == 0) Modifier.focusRequester(first) else Modifier)
                                        .clickable { clicks++ },
                                )
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle { first.requestFocus() }
        compose.onNodeWithTag("0:0").performKeyInput { pressKey(forward) }
        compose.onNodeWithTag("0:1").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        // Move to a different column before going back up; the first shelf remembers 0:1.
        compose.onNodeWithTag("1:1").assertIsFocused().performKeyInput { pressKey(forward) }
        compose.onNodeWithTag("1:2").assertIsFocused().performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithTag("0:1").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, clicks) }
        // Keep moving beyond the viewport: the row must scroll, without dropping into row 1.
        for (index in 1 until 7) {
            compose.onNodeWithTag("0:$index").performKeyInput { pressKey(forward) }
        }
        compose.onNodeWithTag("0:7").assertIsFocused().assertIsDisplayed()
    }
}
