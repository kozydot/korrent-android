package com.example.korrent.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Composable // for webview state
import androidx.compose.runtime.remember // for webview state
import com.example.korrent.KorrentApplication // context for bypass
import com.example.korrent.data.model.*
import com.example.korrent.data.remote.BypassState
import com.example.korrent.data.remote.CloudflareWebViewBypass
import com.example.korrent.data.repository.TorrentRepository
import com.example.korrent.data.repository.TorrentRepositoryImpl // use concrete impl
import com.example.korrent.data.remote.TorrentService // service to create impl
import com.google.accompanist.web.WebViewState // for webview state
import com.google.accompanist.web.rememberWebViewState // for webview state
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// search screen state
data class SearchUiState(
    val searchQuery: String = "",
    val category: String? = null,
    val sortBy: String? = null,
    val order: String = TorrentOrder.DESC,
    val searchResults: List<TorrentItem> = emptyList(),
    val selectedTorrentInfo: TorrentInfo? = null,
    val isLoadingSearch: Boolean = false,
    val isLoadingDetails: Boolean = false,
    val errorMessage: String? = null,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val snackbarMessage: String? = null, // quick messages like "copied"
    val bypassState: BypassState = BypassState.Idle, // track bypass
    val webViewUrl: String? = null // webview url for challenge
)

@Suppress("DEPRECATION") // suppress accompanist webview warnings
class SearchViewModel(
    // todo: use di (hilt)
    private val repository: TorrentRepository = TorrentRepositoryImpl(TorrentService()),
    private val cloudflareBypass: CloudflareWebViewBypass = CloudflareWebViewBypass(KorrentApplication.appContext) // bypass helper
) : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
    }

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        // watch bypass state
        viewModelScope.launch {
            cloudflareBypass.bypassState.collect { state ->
                _uiState.update { it.copy(bypassState = state) }
                if (state is BypassState.Success) {
                    // if bypass worked, retry last failed action (simple)
                    // todo: store exact action to retry
                    if (_uiState.value.isLoadingSearch) {
                        performSearch(_uiState.value.currentPage)
                    } else if (_uiState.value.isLoadingDetails) {
                         // need to remember item being fetched
                         // just stop loading spinner for now
                         _uiState.update { it.copy(isLoadingDetails = false) }
                    }
                } else if (state is BypassState.Error) {
                     // show bypass error
                     _uiState.update { it.copy(isLoadingSearch = false, isLoadingDetails = false, errorMessage = "cloudflare bypass failed: ${state.message}") }
                } else if (state is BypassState.ChallengeRequired) {
                    // update webview url
                     _uiState.update { it.copy(webViewUrl = state.url) }
                }
            }
        }
    }



    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onCategoryChanged(category: String?) {
         _uiState.update { it.copy(category = category?.ifBlank { null }) } // blank is null
    }

     fun onSortByChanged(sortBy: String?) {
         _uiState.update { it.copy(sortBy = sortBy?.ifBlank { null }) }
    }

     fun onOrderChanged(order: String) {
         _uiState.update { it.copy(order = order) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun performSearch(page: Int = 1) {
        val currentState = _uiState.value
        if (currentState.searchQuery.isBlank()) {
            _uiState.update { it.copy(errorMessage = "please enter a search query.") }
            return
        }

        _uiState.update { it.copy(isLoadingSearch = true, errorMessage = null, currentPage = page, searchResults = if (page == 1) emptyList() else it.searchResults) } // clear results only on page 1

        viewModelScope.launch {
            val result = repository.searchTorrents(
                query = currentState.searchQuery,
                page = page,
                category = currentState.category,
                sortBy = currentState.sortBy,
                order = currentState.order
            )
            result.onSuccess { torrentResult ->
                _uiState.update {
                    it.copy(
                        // reset bypass state
                        bypassState = BypassState.Idle,
                        webViewUrl = null,
                        isLoadingSearch = false,
                        // add results if loading next page, else replace
                        searchResults = if (page > 1) it.searchResults + torrentResult.items else torrentResult.items,
                        totalPages = torrentResult.pageCount
                    )
                }
            }.onFailure { error ->
                 // check if cloudflare error (403, timeout, etc.)
                 // basic check, might need tweaks
                 if (error.message?.contains("403") == true || error.message?.contains("timeout", ignoreCase = true) == true || error.message?.contains("cloudflare", ignoreCase = true) == true) {
                     // start bypass
                     viewModelScope.launch {
                         // Use the actual repository method to get the URL
                         val searchUrl = repository.buildSearchUrl(currentState.searchQuery, page, currentState.category, currentState.sortBy, currentState.order)
                         if (!cloudflareBypass.prepareClearance(searchUrl)) {
                             // prepareClearance returned false, challenge needed
                             // stateflow will update ui via the init collector
                             _uiState.update { it.copy(isLoadingSearch = true) } // keep loading spinner
                         } else {
                              // cached clearance worked, retry now
                              performSearch(page)
                         }
                     }
                 } else {
                     // handle other errors
                    _uiState.update {
                        it.copy(
                            isLoadingSearch = false,
                            errorMessage = "search failed: ${error.message}"
                        )
                    }
                 }
            }
        }
    }

    fun fetchTorrentDetails(torrentItem: TorrentItem?) {
        if (torrentItem == null) {
             _uiState.update { it.copy(selectedTorrentInfo = null) }
             return
        }

        _uiState.update { it.copy(isLoadingDetails = true, errorMessage = null, selectedTorrentInfo = null) } // clear old details

        viewModelScope.launch {
            // use torrentid mainly, fallback to link if needed (id is better)
            // Use the actual repository method to get the URL
            val infoUrl = repository.buildInfoUrl(torrentId = torrentItem.torrentId, link = null)
            val result = repository.getTorrentInfo(torrentId = torrentItem.torrentId, link = null)

            result.onSuccess { info ->
                _uiState.update {
                    it.copy(
                        // reset bypass state on success
                        bypassState = BypassState.Idle,
                        webViewUrl = null,
                        isLoadingDetails = false,
                        selectedTorrentInfo = info
                    )
                }
            }.onFailure { error ->
                 // check if it failed 'cause of cloudflare
                 if (error.message?.contains("403") == true || error.message?.contains("timeout", ignoreCase = true) == true || error.message?.contains("cloudflare", ignoreCase = true) == true) {
                     // start bypass process
                     viewModelScope.launch {
                         if (!cloudflareBypass.prepareClearance(infoUrl)) {
                             // stateflow will update ui via the init collector
                             _uiState.update { it.copy(isLoadingDetails = true) } // keep loading spinner
                         } else {
                             // cached clearance worked, retry right away
                             fetchTorrentDetails(torrentItem)
                         }
                     }
                 } else {
                    _uiState.update {
                        it.copy(
                            isLoadingDetails = false,
                            errorMessage = "failed to load details: ${error.message}"
                        )
                    }
                 }
            }
        }
    }

    fun clearSelectedTorrent() {
        _uiState.update { it.copy(selectedTorrentInfo = null, isLoadingDetails = false) } // Also clear loading state
    }

     fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // called by ui on bypass webview interaction
    fun notifyWebViewChallengeSolved() {
        cloudflareBypass.notifyChallengeSolved()
    }

    fun notifyWebViewChallengeFailed() {
        cloudflareBypass.notifyChallengeFailed()
        // clear loading states
        _uiState.update { it.copy(isLoadingSearch = false, isLoadingDetails = false) }
    }

    // expose webview state for ui
    @Composable
    fun rememberWebViewStateForBypass(url: String): WebViewState {
        return rememberWebViewState(url = url)
    }

    // Removed placeholder extension functions as repository now provides these.
}