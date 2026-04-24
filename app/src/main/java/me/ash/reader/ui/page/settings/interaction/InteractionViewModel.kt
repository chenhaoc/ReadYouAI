package me.ash.reader.ui.page.settings.interaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.group.Group
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.repository.GroupDao
import me.ash.reader.domain.service.AccountService

@HiltViewModel
class InteractionViewModel @Inject constructor(
    private val accountService: AccountService,
    private val groupDao: GroupDao,
    private val feedDao: FeedDao,
) : ViewModel() {
    private val _commuteSources = MutableStateFlow(CommuteSourcesUiState())
    val commuteSources: StateFlow<CommuteSourcesUiState> = _commuteSources.asStateFlow()

    init {
        loadCommuteSources()
    }

    fun loadCommuteSources() {
        viewModelScope.launch {
            val accountId = accountService.getCurrentAccountId()
            _commuteSources.update {
                it.copy(
                    groups = groupDao.queryAll(accountId),
                    feeds = feedDao.queryAll(accountId),
                )
            }
        }
    }
}

data class CommuteSourcesUiState(
    val groups: List<Group> = emptyList(),
    val feeds: List<Feed> = emptyList(),
)
