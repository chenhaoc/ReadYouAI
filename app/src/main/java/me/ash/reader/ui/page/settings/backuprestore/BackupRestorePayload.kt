package me.ash.reader.ui.page.settings.backuprestore

import java.util.Date
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.infrastructure.preference.KeepArchivedPreference
import me.ash.reader.infrastructure.preference.SyncBlockList
import me.ash.reader.infrastructure.preference.SyncBlockListPreference
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
    val syncIntervalMinutes: Long? = null,
    val syncOnStart: Boolean? = null,
    val syncOnlyOnWiFi: Boolean? = null,
    val syncOnlyWhenCharging: Boolean? = null,
    val keepArchivedMillis: Long? = null,
    val syncBlockList: SyncBlockList? = null,
    val securityKey: String? = null,
) {
    fun toAccount(): Account =
        Account(
            id = id,
            name = name,
            type = AccountType(typeId),
            updateAt = updateAtMillis?.let(::Date),
            lastArticleId = lastArticleId,
            syncInterval = syncIntervalMinutes.toSyncIntervalPreference(),
            syncOnStart =
                if (syncOnStart == true) SyncOnStartPreference.On else SyncOnStartPreference.Off,
            syncOnlyOnWiFi =
                if (syncOnlyOnWiFi == true) SyncOnlyOnWiFiPreference.On else SyncOnlyOnWiFiPreference.Off,
            syncOnlyWhenCharging =
                if (syncOnlyWhenCharging == true) {
                    SyncOnlyWhenChargingPreference.On
                } else {
                    SyncOnlyWhenChargingPreference.Off
                },
            keepArchived = keepArchivedMillis.toKeepArchivedPreference(),
            syncBlockList = syncBlockList ?: SyncBlockListPreference.default,
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
    val isAutoSummary: Boolean = false,
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
            isAutoSummary = isAutoSummary,
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
        isAutoSummary = isAutoSummary,
    )

private fun Long?.toSyncIntervalPreference(): SyncIntervalPreference =
    when (this) {
        SyncIntervalPreference.Manually.value -> SyncIntervalPreference.Manually
        SyncIntervalPreference.Every15Minutes.value -> SyncIntervalPreference.Every15Minutes
        SyncIntervalPreference.Every30Minutes.value -> SyncIntervalPreference.Every30Minutes
        SyncIntervalPreference.Every1Hour.value -> SyncIntervalPreference.Every1Hour
        SyncIntervalPreference.Every2Hours.value -> SyncIntervalPreference.Every2Hours
        SyncIntervalPreference.Every3Hours.value -> SyncIntervalPreference.Every3Hours
        SyncIntervalPreference.Every6Hours.value -> SyncIntervalPreference.Every6Hours
        SyncIntervalPreference.Every12Hours.value -> SyncIntervalPreference.Every12Hours
        SyncIntervalPreference.Every1Day.value -> SyncIntervalPreference.Every1Day
        else -> SyncIntervalPreference.default
    }

private fun Long?.toKeepArchivedPreference(): KeepArchivedPreference =
    when (this) {
        KeepArchivedPreference.Always.value -> KeepArchivedPreference.Always
        KeepArchivedPreference.For1Day.value -> KeepArchivedPreference.For1Day
        KeepArchivedPreference.For2Days.value -> KeepArchivedPreference.For2Days
        KeepArchivedPreference.For3Days.value -> KeepArchivedPreference.For3Days
        KeepArchivedPreference.For1Week.value -> KeepArchivedPreference.For1Week
        KeepArchivedPreference.For2Weeks.value -> KeepArchivedPreference.For2Weeks
        KeepArchivedPreference.For1Month.value -> KeepArchivedPreference.For1Month
        else -> KeepArchivedPreference.default
    }
