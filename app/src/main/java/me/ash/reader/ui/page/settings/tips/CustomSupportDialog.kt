package me.ash.reader.ui.page.settings.tips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.ui.ext.showToast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSupportDialog(modifier: Modifier = Modifier, onDismissRequest: () -> Unit) {
    ModalBottomSheet(modifier = modifier, onDismissRequest = onDismissRequest) {
        CustomSupportDialogContent()
    }
}

@Composable
private fun CustomSupportDialogContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.tips_support_custom_dialog_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.tips_support_custom_dialog_desc),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tips_support_custom_account_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SupportAccountItem(
            name = stringResource(R.string.tips_support_custom_account_value),
            description = stringResource(R.string.tips_support_custom_account_label),
        ) {
            context.copySupportAccount()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SupportAccountItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(
                    enabled = true,
                    indication = null,
                    interactionSource = interactionSource,
                    onClick = onClick,
                )
                .padding(vertical = 12.dp)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconImage(
            size = 64.dp,
            contentDescription = description,
            modifier = Modifier.size(64.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onClick, interactionSource = interactionSource) {
            Text(stringResource(R.string.tips_support_copy_account))
        }
    }
}

private fun Context.copySupportAccount() {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val value = getString(R.string.tips_support_custom_account_value)
    clipboard.setPrimaryClip(ClipData.newPlainText("support_account", value))
    showToast(getString(R.string.tips_support_account_copied))
}
