package io.github.locngu.beforeyoubuy.feature.priorities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.locngu.beforeyoubuy.R
import io.github.locngu.beforeyoubuy.core.domain.item.Item
import io.github.locngu.beforeyoubuy.core.domain.priorities.MAX_PRIORITY_SLOTS
import io.github.locngu.beforeyoubuy.core.domain.priorities.MIN_PRIORITY_SLOTS
import io.github.locngu.beforeyoubuy.core.domain.priorities.demotedBySlots

/**
 * Choose 1–5 priority slots (brief §4.7). Fewer slots than [active]'s priorities lists which ones go back to the
 * wishlist before Save, so saving is the confirmation (ADR-0047). #26's Settings uses the same dialog.
 */
@Composable
fun PrioritySlotsDialog(current: Int, active: List<Item>, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var slots by rememberSaveable { mutableIntStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.priority_slots_title)) },
        text = { PrioritySlotsChoice(slots, active, onSelect = { slots = it }) },
        confirmButton = { TextButton(onClick = { onSave(slots) }) { Text(stringResource(R.string.quick_add_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } }
    )
}

/** The dialog's content (a dialog is its own window, so screenshots draw this). */
@Composable
fun PrioritySlotsChoice(slots: Int, active: List<Item>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.priority_slots_body), style = MaterialTheme.typography.bodyMedium)
        Column(Modifier.selectableGroup()) {
            (MIN_PRIORITY_SLOTS..MAX_PRIORITY_SLOTS).forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == slots, role = Role.RadioButton, onClick = { onSelect(option) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = option == slots, onClick = null)
                    Text(
                        pluralStringResource(R.plurals.priority_slots_option, option, option),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        val demoted = demotedBySlots(active, slots)
        if (demoted.isNotEmpty()) {
            Text(
                pluralStringResource(R.plurals.priority_slots_demoted, demoted.size, demoted.size),
                style = MaterialTheme.typography.bodyMedium
            )
            demoted.forEach { Text("• ${it.name}", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
