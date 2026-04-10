package me.ash.reader.ui.page.settings.backuprestore

import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group

data class BackupRestorePayload(
    val version: Int = 1,
    val exportedAt: String,
    val settingsJson: String,
    val accounts: List<Account>,
    val groups: List<Group>,
    val feeds: List<Feed>,
)
