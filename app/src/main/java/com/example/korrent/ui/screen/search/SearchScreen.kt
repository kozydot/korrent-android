package com.example.korrent.ui.screen.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.ActivityNotFoundException
import android.util.Log // for logging
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.korrent.data.model.*
import com.example.korrent.data.remote.BypassState
import com.google.accompanist.web.WebView
import com.google.accompanist.web.WebViewState
import com.google.accompanist.web.rememberWebViewState

// log tag
private const val TAG = "SearchScreen"

// dropdown options
private val categoryOptions = mapOf(
    "Any" to null,
    "Movies" to TorrentCategory.MOVIES,
    "TV" to TorrentCategory.TV,
    "Games" to TorrentCategory.GAMES,
    "Music" to TorrentCategory.MUSIC,
    "Apps" to TorrentCategory.APPS,
    "Anime" to TorrentCategory.ANIME,
    "Docs" to TorrentCategory.DOCUMENTARIES,
    "Other" to TorrentCategory.OTHER,
    "XXX" to TorrentCategory.XXX
)

private val sortOptions = mapOf(
    "Default" to null,
    "Time" to TorrentSort.TIME,
    "Size" to TorrentSort.SIZE,
    "Seeders" to TorrentSort.SEEDERS,
    "Leechers" to TorrentSort.LEECHERS
)

private val orderOptions = mapOf(
    "Desc" to TorrentOrder.DESC,
    "Asc" to TorrentOrder.ASC
)


@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION") // suppress accompanist webview warnings
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // webview state
    val webViewState = uiState.webViewUrl?.let { url ->
        viewModel.rememberWebViewStateForBypass(url = url)
    }

    // show snackbars
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSnackbarMessage()
        }
    }
    // show errors
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // main layout
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        // cloudflare challenge dialog
        if (uiState.bypassState is BypassState.ChallengeRequired && webViewState != null) {
            CloudflareChallengeDialog(
                webViewUrl = uiState.webViewUrl ?: "about:blank",
                onChallengeSolved = viewModel::notifyWebViewChallengeSolved,
                onChallengeFailed = viewModel::notifyWebViewChallengeFailed
            )
        }

        // main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // scaffold padding
                .padding(horizontal = 16.dp, vertical = 8.dp) // content padding
        ) {
            // search bar
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                SearchInputSection(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onSearchClick = {
                        keyboardController?.hide()
                        viewModel.performSearch(page = 1)
                    }
                )
            }

            // filter/sort row
            Box(modifier = Modifier.padding(bottom = 16.dp)) {
                OptionsRow(
                    selectedCategory = uiState.category,
                    selectedSortBy = uiState.sortBy,
                    selectedOrder = uiState.order,
                    onCategoryChange = viewModel::onCategoryChanged,
                    onSortByChange = viewModel::onSortByChanged,
                    onOrderChange = viewModel::onOrderChanged
                )
            }

            // results list & details view
            Row(modifier = Modifier.weight(1f).padding(bottom = 16.dp)) {
                // results list (left)
                Surface(modifier = Modifier.weight(1f), shadowElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    SearchResultsList(
                        results = uiState.searchResults,
                        isLoading = uiState.isLoadingSearch && uiState.currentPage == 1,
                        onItemSelected = viewModel::fetchTorrentDetails,
                        onLoadMore = {
                            if (!uiState.isLoadingSearch && uiState.currentPage < uiState.totalPages) {
                                viewModel.performSearch(page = uiState.currentPage + 1)
                            }
                        },
                        currentPage = uiState.currentPage,
                        totalPages = uiState.totalPages
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // details view (right)
                Surface(modifier = Modifier.weight(2f), shadowElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                    TorrentDetailsView(
                        torrentInfo = uiState.selectedTorrentInfo,
                        isLoading = uiState.isLoadingDetails
                    )
                }
            }

            // action buttons
            Box(modifier = Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally)) { // center buttons
                ActionButtons(
                    torrentInfo = uiState.selectedTorrentInfo,
                    onCopyMagnet = { magnet ->
                        copyToClipboard(context, magnet)
                        viewModel.showSnackbar("Magnet link copied!")
                    },
                    onDownloadMagnet = { magnet ->
                        openMagnetLink(context, magnet)
                    }
                )
            }
        }
    }
}

// helper composables

@Composable
fun SearchInputSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(query)) }
    LaunchedEffect(query) { // update text field if query changes externally
        if (textFieldValue.text != query) {
            textFieldValue = textFieldValue.copy(text = query)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = {
                textFieldValue = it
                onQueryChange(it.text)
            },
            label = { Text("Search Query") },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = onSearchClick) {
            Text("Search")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class) // for flowrow
@Composable
fun OptionsRow(
    selectedCategory: String?,
    selectedSortBy: String?,
    selectedOrder: String,
    onCategoryChange: (String?) -> Unit,
    onSortByChange: (String?) -> Unit,
    onOrderChange: (String) -> Unit
) {
    FlowRow(
        verticalArrangement = Arrangement.Center,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        DropdownSelector(
            label = "Category",
            options = categoryOptions,
            selectedValue = selectedCategory,
            onValueSelected = onCategoryChange,
            modifier = Modifier.weight(1f)
        )

        DropdownSelector(
            label = "Sort By",
            options = sortOptions,
            selectedValue = selectedSortBy,
            onValueSelected = onSortByChange,
            modifier = Modifier.weight(1f)
        )

        DropdownSelector(
            label = "Order",
            options = orderOptions,
            selectedValue = selectedOrder,
            onValueSelected = { value -> onOrderChange(value!!) }, // must be non-null
            modifier = Modifier.weight(1f, fill = false).widthIn(min = 120.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(
    label: String,
    options: Map<String, String?>,
    selectedValue: String?,
    onValueSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDisplayName = options.entries.find { it.value == selectedValue }?.key ?: options.entries.first().key

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedDisplayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor() // for dropdown box
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (displayName, value) ->
                DropdownMenuItem(
                    text = { Text(displayName) },
                    onClick = {
                        onValueSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}


@Composable
fun SearchResultsList(
    results: List<TorrentItem>,
    isLoading: Boolean,
    onItemSelected: (TorrentItem) -> Unit,
    onLoadMore: () -> Unit,
    currentPage: Int,
    totalPages: Int
) {
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && results.isEmpty()) { // show loading only if list empty initially
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (results.isEmpty()) {
            Text("No results found.", modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.torrentId }) { item ->
                    TorrentResultItem(item = item, onClick = { onItemSelected(item) })
                    HorizontalDivider()
                }

                // load more indicator
                if (currentPage < totalPages && !isLoading) {
                    item {
                        LaunchedEffect(listState) {
                             snapshotFlow { listState.layoutInfo.visibleItemsInfo }
                                .collect { visibleItems ->
                                    val lastVisibleItemIndex = visibleItems.lastOrNull()?.index ?: -1
                                    // load more when near end
                                    if (visibleItems.isNotEmpty() && lastVisibleItemIndex >= results.size - 5) {
                                        onLoadMore()
                                    }
                                }
                        }
                    }
                } else if (isLoading && results.isNotEmpty()) {
                     // spinner when loading more
                     item {
                         Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                             CircularProgressIndicator()
                         }
                     }
                }
            }
        }
    }
}

@Composable
fun TorrentResultItem(item: TorrentItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp) // padding
    ) {
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp)) // spacer
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp), // padding above row
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "S: ${item.seeders} L: ${item.leechers}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.size,
                style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.time,
                style = MaterialTheme.typography.bodySmall,
                 color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
         Text(
            text = "Up: ${item.uploader}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp)) // spacer after uploader
    }
}

@Composable
fun TorrentDetailsView(torrentInfo: TorrentInfo?, isLoading: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) { // padding
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            torrentInfo != null -> {
                LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                    item { Text("Details:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp)) }
                    item { DetailItem("Name", torrentInfo.name) }
                    item { DetailItem("Category", torrentInfo.category) }
                    item { DetailItem("Type", torrentInfo.type) }
                    item { DetailItem("Language", torrentInfo.language) }
                    item { DetailItem("Size", torrentInfo.size) }
                    item { DetailItem("Seeders", torrentInfo.seeders) }
                    item { DetailItem("Leechers", torrentInfo.leechers) }
                    item { DetailItem("Downloads", torrentInfo.downloads) }
                    item { DetailItem("Uploaded", torrentInfo.dateUploaded) }
                    item { DetailItem("Last Checked", torrentInfo.lastChecked) }
                    item { DetailItem("Uploader", torrentInfo.uploader) }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { DetailItem("Magnet", torrentInfo.magnetLink, isCode = true) }
                    item { DetailItem("InfoHash", torrentInfo.infoHash, isCode = true) }
                }
            }
            else -> {
                Text("Select a result to view details.", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun DetailItem(label: String, value: String?, isCode: Boolean = false) {
    value?.let {
        Row(modifier = Modifier.padding(vertical = 4.dp)) { // vertical padding
            Text(
                "$label: ",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                it,
                style = if (isCode) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                softWrap = true // wrap long stuff like magnet links
            )
        }
    }
}


@Composable
fun ActionButtons(
    torrentInfo: TorrentInfo?,
    onCopyMagnet: (String) -> Unit,
    onDownloadMagnet: (String) -> Unit
) {
    val hasMagnet = !torrentInfo?.magnetLink.isNullOrBlank()

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { torrentInfo?.magnetLink?.let { onCopyMagnet(it) } },
            enabled = hasMagnet
        ) {
            Text("Copy Magnet")
        }
        Button(
            onClick = { torrentInfo?.magnetLink?.let { onDownloadMagnet(it) } },
            enabled = hasMagnet
        ) {
            Text("Download")
        }
    }
}

@Suppress("DEPRECATION") // suppress accompanist webview warnings
@Composable
fun CloudflareChallengeDialog(
    webViewUrl: String,
    onChallengeSolved: () -> Unit,
    onChallengeFailed: () -> Unit
) {
    val webViewState = rememberWebViewState(url = webViewUrl)
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        Dialog(
            onDismissRequest = {
                onChallengeFailed()
                showDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.8f),
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 8.dp // lift dialog
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cloudflare Challenge", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Please solve the challenge presented below to continue.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    WebView(
                        state = webViewState,
                        modifier = Modifier.weight(1f),
                        onCreated = { webView ->
                            webView.settings.javaScriptEnabled = true
                            webView.settings.domStorageEnabled = true
                        },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            onChallengeFailed()
                            showDialog = false
                        }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            onChallengeSolved()
                            showDialog = false
                        }) {
                            Text("Done / Retry Request")
                        }
                    }
                }
            }
        }
    }
}


// utility functions

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Magnet Link", text)
    clipboard.setPrimaryClip(clip)
}

// use chooser for magnet links
fun openMagnetLink(context: Context, magnetLink: String) {
    Log.d(TAG, "attempting to open magnet link: $magnetLink")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetLink))

    // check if any app can handle it
    val resolvedActivity = intent.resolveActivity(context.packageManager)
    if (resolvedActivity == null) {
        Log.w(TAG, "no activity found by resolveactivity for magnet link.")
        Toast.makeText(context, "no app installed can handle magnet links.", Toast.LENGTH_LONG).show()
        return
    } else {
         Log.i(TAG, "resolveactivity found handler: ${resolvedActivity.flattenToString()}")
    }

    try {
        // create chooser dialog
        Log.d(TAG, "creating intent chooser.")
        val chooser = Intent.createChooser(intent, "open magnet link with...")

        // check if chooser works (should always)
        val chooserResolvedActivity = chooser.resolveActivity(context.packageManager)
        if (chooserResolvedActivity != null) {
            Log.i(TAG, "intent chooser resolved to: ${chooserResolvedActivity.flattenToString()}")
            Log.d(TAG, "starting intent chooser activity.")
            context.startActivity(chooser)
        } else {
            // fallback if chooser fails (weird)
            Log.e(TAG, "intent chooser could not be resolved.")
            Toast.makeText(context, "could not show app chooser.", Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        // catch chooser errors
        Log.e(TAG, "error starting intent chooser for magnet link", e)
        Toast.makeText(context, "could not open magnet link: ${e.message}", Toast.LENGTH_LONG).show()
    }
}