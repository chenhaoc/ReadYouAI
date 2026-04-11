package me.ash.reader.ui.page.settings.backuprestore

import java.util.Date
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.infrastructure.preference.KeepArchivedPreference
import me.ash.reader.infrastructure.preference.SyncBlockList
import me.ash.reader.infrastructure.preference.SyncIntervalPreference
import me.ash.reader.infrastructure.preference.SyncOnStartPreference
import me.ash.reader.infrastructure.preference.SyncOnlyOnWiFiPreference
import me.ash.reader.infrastructure.preference.SyncOnlyWhenChargingPreference

data class BackupRestorePayload(
    val version: Int = CURRENT_VERSION,
    val exportedAt: String,
    val settingsJson: String,
    val selectedAccountId: Int? = null,
    val selectedAccountType: Int? = null,
    val accounts: List<BackupRestoreAccountPayload>,
    val groups: List<BackupRestoreGroupPayload>,
    val feeds: List<BackupRestoreFeedPayload>,
) {
    companion object {
        const val CURRENT_VERSION = 3
    }
}

data class BackupRestoreAccountPayload(
    val id: Int? = null,
    val name: String,
    val typeId: Int,
    val updateAtMillis: Long? = null,
    val lastArticleId: String? = null,
    val syncIntervalMinutes: Long = SyncIntervalPreference.default.value,
    val syncOnStart: Boolean = SyncOnStartPreference.default.value,
    val syncOnlyOnWiFi: Boolean = SyncOnlyOnWiFiPreference.default.value,
    val syncOnlyWhenCharging: Boolean = SyncOnlyWhenChargingPreference.default.value,
    val keepArchivedMillis: Long = KeepArchivedPreference.default.value,
    val syncBlockList: SyncBlockList = emptyList(),
    val securityKey: String? = null,
) {
    fun toAccount(): Account =
        Account(
            id = id,
            name = name,
            type = AccountType(typeId),
            updateAt = updateAtMillis?.let(::Date),
            lastArticleId = lastArticleId,
            syncInterval =
                SyncIntervalPreference.values.firstOrNull { it.value == syncIntervalMinutes }
                    ?: SyncIntervalPreference.default,
            syncOnStart =
                if (syncOnStart) SyncOnStartPreference.On else SyncOnStartPreference.Off,
            syncOnlyOnWiFi =
                if (syncOnlyOnWiFi) SyncOnlyOnWiFiPreference.On else SyncOnlyOnWiFiPreference.Off,
            syncOnlyWhenCharging =
                if (syncOnlyWhenCharging) {
                    SyncOnlyWhenChargingPreference.On
                } else {
                    SyncOnlyWhenChargingPreference.Off
                },
            keepArchived =
                KeepArchivedPreference.values.firstOrNull { it.value == keepArchivedMillis }
                    ?: KeepArchivedPreference.default,
            syncBlockList = syncBlockList,
            securityKey = securityKey,
        )
}

data class BackupRestoreGroupPayload(
    val id: String,
    val name: String,
    val accountId: Int,
) {
    fun toGroup(): Group = Group(id = id, name = name, accountId = accountId)
}

data class BackupRestoreFeedPayload(
    val id: String,
    val name: String,
    val icon: String? = null,
    val url: String,
    val groupId: String,
    val accountId: Int,
    val isNotification: Boolean = false,
    val isFullContent: Boolean = false,
    val isBrowser: Boolean = false,
    val isTranslationEnabled: Boolean = false,
    val isAutoTranslate: Boolean = false,
) {
    fun toFeed(): Feed =
        Feed(
            id = id,
            name = name,
            icon = icon,
            url = url,
            groupId = groupId,
            accountId = accountId,
            isNotification = isNotification,
            isFullContent = isFullContent,
            isBrowser = isBrowser,
            isTranslationEnabled = isTranslationEnabled,
            isAutoTranslate = isAutoTranslate,
        )
}

fun Account.toBackupPayload(): BackupRestoreAccountPayload =
    BackupRestoreAccountPayload(
        id = id,
        name = name,
        typeId = type.id,
        updateAtMillis = updateAt?.time,
        lastArticleId = lastArticleId,
        syncIntervalMinutes = syncInterval.value,
        syncOnStart = syncOnStart.value,
        syncOnlyOnWiFi = syncOnlyOnWiFi.value,
        syncOnlyWhenCharging = syncOnlyWhenCharging.value,
        keepArchivedMillis = keepArchived.value,
        syncBlockList = syncBlockList,
        securityKey = securityKey,
    )

fun Group.toBackupPayload(): BackupRestoreGroupPayload =
    BackupRestoreGroupPayload(
        id = id,
        name = name,
        accountId = accountId,
    )

fun Feed.toBackupPayload(): BackupRestoreFeedPayload =
    BackupRestoreFeedPayload(
        id = id,
        name = name,
        icon = icon,
        url = url,
        groupId = groupId,
        accountId = accountId,
        isNotification = isNotification,
        isFullContent = isFullContent,
        isBrowser = isBrowser,
        isTranslationEnabled = isTranslationEnabled,
        isAutoTranslate = isAutoTranslate,
    )
