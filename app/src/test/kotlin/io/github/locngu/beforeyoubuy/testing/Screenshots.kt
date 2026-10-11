package io.github.locngu.beforeyoubuy.testing

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
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
    val bitmap = runOnIdle { activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0).drawToBitmap() }
    bitmap.captureRoboImage()
}
