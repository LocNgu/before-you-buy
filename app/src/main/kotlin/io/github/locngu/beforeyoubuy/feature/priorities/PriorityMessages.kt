package io.github.locngu.beforeyoubuy.feature.priorities

import android.content.res.Resources
import io.github.locngu.beforeyoubuy.R

/**
 * The snackbar text for [event], or null for [PriorityEvent.Compare], which opens the head-to-head instead.
 * Resources, not composables: it's called from the collector of one-shot events.
 */
fun priorityMessage(event: PriorityEvent, resources: Resources): String? = when (event) {
    is PriorityEvent.Promoted -> resources.getString(R.string.priority_promoted, event.name, event.rank)
    is PriorityEvent.Removed -> resources.getString(R.string.priority_removed, event.name)
    PriorityEvent.ChangedMeanwhile -> resources.getString(R.string.priority_changed_meanwhile)
    is PriorityEvent.Compare -> null
}
