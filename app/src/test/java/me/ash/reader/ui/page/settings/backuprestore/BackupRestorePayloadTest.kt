package me.ash.reader.ui.page.settings.backuprestore

import com.google.gson.Gson
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.account.AccountType
import me.ash.reader.infrastructure.preference.KeepArchivedPreference
import me.ash.reader.infrastructure.preference.SyncIntervalPreference
import me.ash.reader.infrastructure.preference.SyncOnStartPreference
import me.ash.reader.infrastructure.preference.SyncOnlyOnWiFiPreference
import me.ash.reader.infrastructure.preference.SyncOnlyWhenChargingPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestorePayloadTest {

    private val gson = Gson()

    @Test
    fun roundTripsConcreteBackupDtos() {
        val payload =
            BackupRestorePayload(
                exportedAt = "2026-04-10 12:00:00",
                settingsJson = "{}",
                accounts =
                    listOf(
                        Account(
                            id = 7,
                            name = "Feedly",
                            type = AccountType.Feedly,
                            lastArticleId = "article-9",
                            syncInterval = SyncIntervalPreference.Every1Hour,
                            syncOnStart = SyncOnStartPreference.On,
                            syncOnlyOnWiFi = SyncOnlyOnWiFiPreference.On,
                            syncOnlyWhenCharging = SyncOnlyWhenChargingPreference.Off,
                            keepArchived = KeepArchivedPreference.For1Week,
                            syncBlockList = listOf("ads"),
                            securityKey = "k",
                        ).toBackupPayload()
                    ),
                groups = listOf(BackupRestoreGroupPayload(id = "g", name = "All", accountId = 7)),
                feeds =
                    listOf(
                        BackupRestoreFeedPayload(
                            id = "f",
                            name = "Example",
                            url = "https://example.com/rss",
                            groupId = "g",
                            accountId = 7,
                            isNotification = true,
                        )
                    ),
            )

        val parsed = gson.fromJson(gson.toJson(payload), BackupRestorePayload::class.java)
        val account = parsed.accounts.single().toAccount()

        assertEquals(BackupRestorePayload.CURRENT_VERSION, parsed.version)
        assertEquals(AccountType.Feedly.id, account.type.id)
        assertEquals(SyncIntervalPreference.Every1Hour.value, account.syncInterval.value)
        assertEquals(SyncOnlyOnWiFiPreference.On.value, account.syncOnlyOnWiFi.value)
        assertEquals(KeepArchivedPreference.For1Week.value, account.keepArchived.value)
        assertTrue(gson.toJson(parsed).contains("\"typeId\":5"))
    }
}
