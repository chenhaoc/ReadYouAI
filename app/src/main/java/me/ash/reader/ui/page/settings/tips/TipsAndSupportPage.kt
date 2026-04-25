package me.ash.reader.ui.page.settings.tips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import me.ash.reader.BuildConfig
import me.ash.reader.R
import me.ash.reader.infrastructure.preference.OpenLinkPreference
import me.ash.reader.ui.component.base.FeedbackIconButton
import me.ash.reader.ui.component.base.RYScaffold
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.openURL
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.ext.showToast
import me.ash.reader.ui.theme.palette.onLight

private data class SupportFeature(
    val icon: ImageVector,
    val text: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsAndSupportPage(
    updateViewModel: UpdateViewModel = hiltViewModel(),
    onBack: () -> Unit,
    navigateToLicenseList: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isGithubAi = BuildConfig.FLAVOR == "githubAi"
    var currentVersion by remember { mutableStateOf("") }
    var showSponsorDialog by remember { mutableStateOf(false) }
    var showCustomSupportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        currentVersion =
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    val checkUpdates = {
        updateViewModel.checkUpdate(
            preProcessor = {
                context.showToast(context.getString(R.string.checking_updates))
                context.dataStore.put(DataStoreKey.skipVersionNumber, "")
            },
            postProcessor = {
                if (!it) {
                    context.showToast(context.getString(R.string.is_latest_version))
                }
            },
        )
    }

    val openLink: (String) -> Unit = { url ->
        context.openURL(url, OpenLinkPreference.AutoPreferCustomTabs)
    }

    val featureItems = remember(isGithubAi, context) {
        buildList {
            if (isGithubAi) {
                add(
                    SupportFeature(
                        icon = Icons.Rounded.AutoAwesome,
                        text = context.getString(R.string.tips_support_feature_ai_summary),
                    ),
                )
                add(
                    SupportFeature(
                        icon = Icons.Rounded.Psychology,
                        text = context.getString(R.string.tips_support_feature_ai_chat),
                    ),
                )
                add(
                    SupportFeature(
                        icon = Icons.Rounded.Translate,
                        text = context.getString(R.string.tips_support_feature_translation),
                    ),
                )
                add(
                    SupportFeature(
                        icon = Icons.Rounded.Book,
                        text = context.getString(R.string.tips_support_feature_reading),
                    ),
                )
                add(
                    SupportFeature(
                        icon = Icons.AutoMirrored.Rounded.VolumeUp,
                        text = context.getString(R.string.tips_support_feature_tts),
                    ),
                )
                add(
                    SupportFeature(
                        icon = Icons.AutoMirrored.Rounded.Article,
                        text = context.getString(R.string.tips_support_feature_backup),
                    ),
                )
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
        actions = {
            FeedbackIconButton(
                modifier = Modifier.size(20.dp),
                imageVector = Icons.Rounded.Balance,
                contentDescription = stringResource(R.string.open_source_licenses),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = navigateToLicenseList,
            )
        },
        content = {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                item {
                    VersionCard(
                        appName = stringResource(R.string.read_you),
                        currentVersion = currentVersion,
                        versionDesc =
                            stringResource(
                                if (isGithubAi) R.string.tips_support_build_desc_ai
                                else R.string.tips_support_build_desc_default,
                            ),
                        onCheckUpdates = checkUpdates,
                    )
                }

                item { SectionTitle(text = stringResource(R.string.tips_support_section_updates)) }
                item {
                    SupportListItem(
                        title = stringResource(R.string.tips_support_github_repo),
                        desc =
                            stringResource(
                                if (isGithubAi) R.string.tips_support_github_repo_desc_ai
                                else R.string.tips_support_github_repo_desc_default,
                            ),
                        iconPainter = painterResource(R.drawable.ic_github),
                        onClick = { openLink(context.getString(R.string.github_link)) },
                    )
                }
                item {
                    SupportListItem(
                        title = stringResource(R.string.tips_support_docs),
                        desc =
                            stringResource(
                                if (isGithubAi) R.string.tips_support_docs_desc_ai
                                else R.string.tips_support_docs_desc_default,
                            ),
                        icon = Icons.Rounded.Book,
                        onClick = { openLink(context.getString(R.string.wiki_link)) },
                    )
                }
                item {
                    SupportListItem(
                        title = stringResource(R.string.tips_support_release_notes),
                        desc = stringResource(R.string.tips_support_release_notes_desc),
                        icon = Icons.Rounded.Update,
                        onClick = { openLink(context.getString(R.string.tips_support_release_notes_link)) },
                    )
                }
                item {
                    SupportListItem(
                        title = stringResource(R.string.open_source_licenses),
                        desc = stringResource(R.string.tips_support_licenses_desc),
                        icon = Icons.Rounded.Balance,
                        onClick = navigateToLicenseList,
                    )
                }

                item { SectionTitle(text = stringResource(R.string.tips_support_section_support)) }
                item {
                    SupportListItem(
                        title = "Telegram",
                        desc = stringResource(R.string.tips_support_telegram_desc),
                        iconPainter = painterResource(R.drawable.ic_telegram),
                        onClick = { openLink(context.getString(R.string.telegram_link)) },
                    )
                }
                item {
                    SupportListItem(
                        title = stringResource(R.string.tips_support_support_upstream),
                        desc = stringResource(R.string.tips_support_support_upstream_desc),
                        icon = Icons.Rounded.VolunteerActivism,
                        onClick = { showSponsorDialog = true },
                    )
                }
                if (isGithubAi) {
                    item {
                        SupportListItem(
                            title = stringResource(R.string.tips_support_support_custom),
                            desc = stringResource(R.string.tips_support_support_custom_desc),
                            icon = Icons.Rounded.VolunteerActivism,
                            onClick = { showCustomSupportDialog = true },
                        )
                    }
                }

                if (featureItems.isNotEmpty()) {
                    item {
                        SectionTitle(text = stringResource(R.string.tips_support_section_features))
                    }
                    item { FeatureCard(items = featureItems) }
                }
            }
        },
    )

    UpdateDialog()
    if (showSponsorDialog) {
        SponsorDialog(onDismissRequest = { showSponsorDialog = false })
    }
    if (showCustomSupportDialog) {
        CustomSupportDialog(onDismissRequest = { showCustomSupportDialog = false })
    }
}

@Composable
private fun VersionCard(
    appName: String,
    currentVersion: String,
    versionDesc: String,
    onCheckUpdates: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = currentVersion,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = versionDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = onCheckUpdates) {
                    Text(text = stringResource(R.string.tips_support_check_updates))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            AppIconImage(
                size = 88.dp,
                contentDescription = appName,
                modifier = Modifier.size(88.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SupportListItem(
    title: String,
    desc: String,
    icon: ImageVector? = null,
    iconPainter: androidx.compose.ui.graphics.painter.Painter? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Unspecified,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                iconPainter != null -> {
                    Icon(
                        painter = iconPainter,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 20.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(items: List<SupportFeature>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            items.forEachIndexed { index, item ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
                if (index != items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
