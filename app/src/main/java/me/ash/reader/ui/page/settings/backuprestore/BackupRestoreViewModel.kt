package me.ash.reader.ui.page.settings.backuprestore

import android.content.Context
import androidx.room.withTransaction
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.repository.AccountDao
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.infrastructure.db.AndroidDatabase
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import me.ash.reader.ui.ext.DateFormat
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.currentAccountId
import me.ash.reader.ui.ext.currentAccountType
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.fromDataStoreToJSONString
import me.ash.reader.ui.ext.fromJSONStringToDataStore
import me.ash.reader.ui.ext.put
import me.ash.reader.ui.ext.toString
import java.util.Date
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.withContext

@HiltViewModel
class BackupRestoreViewModel
@Inject
constructor(
    private val androidDatabase: AndroidDatabase,
    private val accountDao: AccountDao,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val gson = Gson()

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    fun showImportConfirmation(byteArray: ByteArray) {
        _uiState.update {
            it.copy(
                pendingImportBytes = byteArray,
                importConfirmationVisible = true,
            )
        }
    }

    fun hideImportConfirmation() {
        _uiState.update {
            it.copy(
                pendingImportBytes = null,
                importConfirmationVisible = false,
            )
        }
    }

    fun exportBackup(context: Context, callback: (Result<ByteArray>) -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            val result =
                runCatching {
                    val accounts = accountDao.queryAll()
                    val groups = accounts.flatMap { account -> groupDao.queryAll(account.id!!) }
                    val feeds = accounts.flatMap { account -> feedDao.queryAll(account.id!!) }
                    val payload =
                        BackupRestorePayload(
                            exportedAt = Date().toString(DateFormat.YYYY_MM_DD_HH_MM_SS),
                            settingsJson = context.fromDataStoreToJSONString(),
                            selectedAccountId = context.currentAccountId,
                            selectedAccountType = context.currentAccountType,
                            accounts = accounts.map { it.toBackupPayload() },
                            groups = groups.map { it.toBackupPayload() },
                            feeds = feeds.map { it.toBackupPayload() },
                        )
                    gson.toJson(payload).toByteArray()
                }
            withContext(mainDispatcher) {
                callback(result)
            }
        }
    }

    fun importBackup(context: Context, byteArray: ByteArray, callback: (Result<Unit>) -> Unit) {
        viewModelScope.launch(ioDispatcher) {
            val result =
                runCatching {
                    val payload =
                        gson.fromJson(String(byteArray), BackupRestorePayload::class.java)
                            ?: error("Invalid backup file")
                    require(payload.accounts.isNotEmpty()) { "Backup contains no accounts" }

                    var restoredCurrentAccountId: Int? = null
                    var restoredCurrentAccountType: Int? = null

                    androidDatabase.withTransaction {
                        val currentAccounts = accountDao.queryAll()
                        currentAccounts.forEach { account ->
                            val accountId = account.id ?: return@forEach
                            articleDao.deleteByAccountId(accountId)
                            feedDao.deleteByAccountId(accountId)
                            groupDao.deleteByAccountId(accountId)
                        }
                        if (currentAccounts.isNotEmpty()) {
                            accountDao.delete(*currentAccounts.toTypedArray())
                        }

                        accountDao.insertList(payload.accounts.map { it.toAccount() })
                        groupDao.insertAll(payload.groups.map { it.toGroup() })
                        feedDao.insertAll(payload.feeds.map { it.toFeed() })

                        val fallbackAccount = payload.accounts.first()
                        val selectedAccountId =
                            payload.selectedAccountId
                                ?.takeIf { selectedId ->
                                    payload.accounts.any { account -> account.id == selectedId }
                                }
                                ?: fallbackAccount.id

                        val restoredAccount =
                            selectedAccountId?.let { accountDao.queryById(it) }
                                ?: accountDao.queryAll().firstOrNull()
                                ?: error("Imported backup contains no restorable accounts")
                        restoredCurrentAccountId = restoredAccount.id
                        restoredCurrentAccountType = restoredAccount.type.id
                    }

                    payload.settingsJson.fromJSONStringToDataStore(context, clearExisting = true)
                    restoredCurrentAccountId?.let {
                        context.dataStore.put(DataStoreKey.currentAccountId, it)
                    }
                    restoredCurrentAccountType?.let {
                        context.dataStore.put(DataStoreKey.currentAccountType, it)
                    }
                    Unit
                }
            withContext(mainDispatcher) {
                callback(result)
                hideImportConfirmation()
            }
        }
    }
}

data class BackupRestoreUiState(
    val importConfirmationVisible: Boolean = false,
    val pendingImportBytes: ByteArray? = null,
)
