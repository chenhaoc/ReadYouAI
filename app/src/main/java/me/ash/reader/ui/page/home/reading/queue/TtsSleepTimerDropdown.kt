package me.ash.reader.ui.page.home.reading.queue

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.ash.reader.R
import me.ash.reader.infrastructure.android.ttsqueue.TtsSleepTimerOption

@Composable
internal fun TtsSleepTimerDropdown(
    selectedOption: TtsSleepTimerOption,
    enabled: Boolean,
    onSelect: (TtsSleepTimerOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(
            onClick = { expanded = true },
            enabled = enabled,
        ) {
            Text(text = triggerLabel(selectedOption))
            Icon(
                imageVector = Icons.Rounded.ArrowDropDown,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TtsSleepTimerOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun triggerLabel(option: TtsSleepTimerOption): String =
    when (option) {
        TtsSleepTimerOption.Off -> stringResource(id = R.string.sleep_timer)
        TtsSleepTimerOption.FiveMinutes -> stringResource(id = R.string.sleep_timer_minutes, 5)
        TtsSleepTimerOption.TenMinutes -> stringResource(id = R.string.sleep_timer_minutes, 10)
        TtsSleepTimerOption.FifteenMinutes -> stringResource(id = R.string.sleep_timer_minutes, 15)
        TtsSleepTimerOption.ThirtyMinutes -> stringResource(id = R.string.sleep_timer_minutes, 30)
        TtsSleepTimerOption.CurrentArticleEnd -> stringResource(id = R.string.sleep_timer_current_audio_end)
    }


@Composable
private fun optionLabel(option: TtsSleepTimerOption): String =
    when (option) {
        TtsSleepTimerOption.Off -> stringResource(id = R.string.sleep_timer_off)
        else -> triggerLabel(option)
    }
