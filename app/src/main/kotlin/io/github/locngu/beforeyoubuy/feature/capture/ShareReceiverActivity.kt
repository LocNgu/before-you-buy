package io.github.locngu.beforeyoubuy.feature.capture

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.capture.parseSharedText
import io.github.locngu.beforeyoubuy.ui.theme.BeforeYouBuyTheme

/**
 * Share capture (#15, brief §6.2): text shared from a shop or browser opens quick add over that app, with the
 * name and link read offline from the share (ADR-0003); the price can't be, so the cursor starts there. Saving or
 * closing the sheet finishes the activity and the user is back in the app they shared from. Its own task affinity
 * (manifest) keeps it from bringing this app's task to the front.
 */
@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {
    private val quickAdd: QuickAddViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recreated after the sheet was saved or closed (e.g. rotated while it slid away): nothing left to show.
        if (!quickAdd.openOnce(sharedPrefill(intent, resources))) {
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            BeforeYouBuyTheme {
                QuickAddHost(viewModel = quickAdd, onSaved = { showSaved(it) }, onClosed = ::finish)
            }
        }
    }

    // A toast, not a snackbar: it stays on screen once this activity is gone.
    private fun showSaved(message: String) = Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
}

/**
 * Quick add's values for a share: name and link from `EXTRA_TEXT` and `EXTRA_SUBJECT` (else `EXTRA_TITLE`). A share
 * with a link but no name is named after the shop ("amazon.de item"). Anything but `ACTION_SEND`, or extras that
 * can't be read, open it empty.
 */
internal fun sharedPrefill(intent: Intent, resources: Resources): QuickAddPrefill {
    if (intent.action != Intent.ACTION_SEND) return QuickAddPrefill()
    // Any app can send to this activity; extras it can't unparcel (a class we don't have) throw on first read.
    val (text, subject) = try {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val subject = listOf(Intent.EXTRA_SUBJECT, Intent.EXTRA_TITLE)
            .firstNotNullOfOrNull { key -> intent.getCharSequenceExtra(key)?.toString()?.takeIf(String::isNotBlank) }
        text to subject
    } catch (unreadable: RuntimeException) {
        return QuickAddPrefill()
    }
    val draft = parseSharedText(text, subject)
    val name = draft.name ?: draft.shop?.let { resources.getString(R.string.share_fallback_name, it) }
    return QuickAddPrefill(name = name.orEmpty(), link = draft.url.orEmpty())
}
