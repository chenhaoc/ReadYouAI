package me.ash.reader.ui.page.settings.backuprestore

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.R
import me.ash.reader.ui.component.base.DisplayText
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYDialog
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.component.base.Subtitle
import me.ash.reader.ui.ext.MimeType
import me.ash.reader.ui.ext.collectAsStateValue
import me.ash.reader.ui.ext.getCurrentVersion
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.ext.toString
import me.ash.reader.ui.page.settings.SettingItem
import me.ash.reader.ui.theme.palette.onLight

@Composable
fun BackupRestorePage(
    onBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState = viewModel.uiState.collectAsStateValue()

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MimeType.JSON)) {
            result ->
            if (result == null) return@rememberLauncherForActivityResult
            viewModel.exportBackup(context) { exportResult ->
                exportResult
                    .onSuccess { byteArray ->
                        context.contentResolver.openOutputStream(result)?.use { outputStream ->
                            outputStream.write(byteArray)
                        }
                        context.showToast(context.getString(R.string.backup_export_success))
                    }
                    .onFailure {
                        context.showToast(it.message ?: context.getString(R.string.backup_export_failed))
                    }
            }
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                context.contentResolver.openInputStream(it)?.use { inputStream ->
                    viewModel.showImportConfirmation(inputStream.readBytes())
                }
            }
        }

    RYScaffold(
        containerColor = MaterialTheme.colorScheme.surface onLight MaterialTheme.colorScheme.inverseOnSurface,
        navigationIcon = {
            FeedbackIconButton(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onBack,
            )
        },
        content = {
            LazyColumn {
                item {
                    DisplayText(
                        text = stringResource(R.string.backup_restore),
                        desc = stringResource(R.string.backup_restore_desc),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.configuration_backup),
                    )
                    SettingItem(
                        title = stringResource(R.string.export_backup),
                        desc = stringResource(R.string.export_backup_desc),
                        icon = Icons.Outlined.Save,
                        onClick = { backupFileLauncher(context, exportLauncher) },
                    ) {}
                    SettingItem(
                        title = stringResource(R.string.import_backup),
                        desc = stringResource(R.string.import_backup_desc),
                        icon = Icons.Outlined.Upload,
                        onClick = { importLauncher.launch(arrayOf(MimeType.JSON, MimeType.ANY)) },
                    ) {}
                    Spacer(modifier = Modifier.height(24.dp))
                }
                item {
                    Subtitle(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.backup_scope),
                    )
                    Text(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        text = stringResource(R.string.backup_scope_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        },
    )

    RYDialog(
        visible = uiState.importConfirmationVisible,
        onDismissRequest = { viewModel.hideImportConfirmation() },
        icon = {
            Icon(
                imageVector = Icons.Outlined.ImportExport,
                contentDescription = stringResource(R.string.import_backup),
            )
        },
        title = { Text(text = stringResource(R.string.import_backup)) },
        text = { Text(text = stringResource(R.string.backup_import_warning)) },
        confirmButton = {
            TextButton(
                onClick = {
                    val bytes = uiState.pendingImportBytes ?: return@TextButton
                    viewModel.importBackup(context, bytes) { importResult ->
                        importResult
                            .onSuccess {
                                context.showToast(context.getString(R.string.backup_import_success))
                            }
                            .onFailure {
                                context.showToast(
                                    it.message ?: context.getString(R.string.backup_import_failed)
                                )
                            }
                    }
                }
            ) {
                Text(text = stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.hideImportConfirmation() }) {
                Text(text = stringResource(R.string.cancel))
            }
        },
    )
}

private fun backupFileLauncher(
    context: android.content.Context,
    launcher: ManagedActivityResultLauncher<String, Uri?>,
) {
    launcher.launch(
        "${context.getString(R.string.read_you)}-" +
            "${context.getCurrentVersion()}-backup-" +
            "${java.util.Date().toString(me.ash.reader.ui.ext.DateFormat.YYYY_MM_DD_DASH_HH_MM_SS_DASH)}.json"
    )
}
