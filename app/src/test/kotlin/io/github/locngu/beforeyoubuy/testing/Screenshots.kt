package io.github.locngu.beforeyoubuy.testing

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Screenshots the composed content with Roborazzi (default name `<package>.<Class>.<test>.png`).
 *
 * Draws the content on the main thread once Compose is idle, instead of Roborazzi's default window
 * PixelCopy: under Robolectric that copy races the render thread and sometimes returned the blank
 * frame from before `setContent`, mostly on CI.
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.captureContent() {
    checkAccessibility()
    val bitmap = runOnIdle { activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0).drawToBitmap() }
    bitmap.captureRoboImage()
}

/**
 * Fails when an actionable element on screen has a touch target under 48dp or nothing a screen reader could say
 * (no text, no content description) (#30). The Accessibility Test Framework can't see Compose content under
 * Robolectric ("Composable content is not evaluated in this environment"), so this walks the semantics tree itself.
 * Every screenshot test goes through [captureContent], so every screenshot-tested screen and state in en/de ×
 * light/dark is covered. Bars that still expose a range (a bare percentage for TalkBack) need a description or state text; bars next to text that says the same are made decorative with `clearAndSetSemantics {}` and aren't matched. Only elements with a click action are checked.
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.checkAccessibility() {
    waitForIdle()
    val minPx = with(density) { 48.dp.toPx() }
    val problems = mutableListOf<String>()
    onAllNodes(hasClickAction()).fetchSemanticsNodes(atLeastOneRootRequired = false).forEach { node ->
        val config = node.config
        val label =
            listOfNotNull(
                config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text },
                config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
            ).joinToString(" / ")
        val box = node.touchBoundsInRoot
        // A node scrolled out of the window has empty touch bounds: it is not a touch target on screen.
        if (!box.isEmpty && (box.width < minPx - 1 || box.height < minPx - 1)) {
            problems +=
                "touch target ${box.width / density.density}x${box.height / density.density}dp too small: '$label'"
        }
        if (label.isBlank()) problems += "clickable without text or content description at ${node.boundsInRoot}"
    }
    onAllNodes(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    ).fetchSemanticsNodes(atLeastOneRootRequired = false).forEach { node ->
        val config = node.config
        val described =
            config.getOrNull(SemanticsProperties.ContentDescription)?.isNotEmpty() == true ||
                config.getOrNull(SemanticsProperties.StateDescription)?.isNotBlank() == true
        if (!described) problems += "progress bar without description at ${node.boundsInRoot}"
    }
    check(problems.isEmpty()) { "Accessibility problems (#30):\n" + problems.joinToString("\n") }
}
