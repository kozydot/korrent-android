package com.example.korrent.ui.screen.search

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.ActivityNotFoundException
import android.util.Log // for logging
import androidx.activity.compose.BackHandler // Import BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.rememberModalBottomSheetState // Import for Bottom Sheet
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


@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class) // Added ExperimentalMaterial3Api
@Suppress("DEPRECATION") // suppress accompanist webview warnings
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) // Bottom sheet state
    var showFilterSheet by remember { mutableStateOf(false) } // State to control sheet visibility

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

        // Filter Bottom Sheet
        if (showFilterSheet) {
            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = sheetState
            ) {
                // Content of the bottom sheet
                FilterBottomSheetContent(
                    initialCategory = uiState.category,
                    initialSortBy = uiState.sortBy,
                    initialOrder = uiState.order,
                    onApplyFilters = { category, sortBy, order ->
                        viewModel.onCategoryChanged(category)
                        viewModel.onSortByChanged(sortBy)
                        viewModel.onOrderChanged(order)
                        viewModel.performSearch(page = 1) // Trigger search with new filters
                        showFilterSheet = false
                    },
                    onDismiss = { showFilterSheet = false }
                )
            }
        }

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
            // Search Bar and Filter Button Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), // Combined padding
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Re-integrate SearchInputSection logic here for clarity
                var textFieldValue by remember { mutableStateOf(TextFieldValue(uiState.searchQuery)) }
                LaunchedEffect(uiState.searchQuery) { // update text field if query changes externally
                    if (textFieldValue.text != uiState.searchQuery) {
                        textFieldValue = textFieldValue.copy(text = uiState.searchQuery)
                    }
                }

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        viewModel.onSearchQueryChanged(it.text) // Use ViewModel directly
                    },
                    label = { Text("Search Query") },
                    modifier = Modifier.weight(1f), // Takes available space
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    keyboardController?.hide()
                    viewModel.performSearch(page = 1)
                }) {
                    Text("Search")
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Re-integrate OptionsRow logic here
                Button(onClick = { showFilterSheet = true }) {
                    Text("Filters")
                }
            }

            // Conditionally display List or Details (Single Pane)
            Box(modifier = Modifier.weight(1f).padding(bottom = 8.dp)) { // Reduced bottom padding
// TODO: Implement screen size detection (e.g., using BoxWithConstraints or WindowSizeClass)
                //       to conditionally apply this single-pane logic only on phones/portrait.
                //       The current implementation replaces the two-pane layout entirely.
                if (uiState.selectedTorrentInfo == null) {
                    // Show Search Results List
                    Surface(modifier = Modifier.fillMaxSize(), shadowElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                        SearchResultsList(
                            results = uiState.searchResults,
                            isLoading = uiState.isLoadingSearch && uiState.currentPage == 1,
                            onItemSelected = viewModel::fetchTorrentDetails, // Selecting item will trigger detail view
                            onLoadMore = {
                                if (!uiState.isLoadingSearch && uiState.currentPage < uiState.totalPages) {
                                    viewModel.performSearch(page = uiState.currentPage + 1)
                                }
                            },
                            currentPage = uiState.currentPage,
                            totalPages = uiState.totalPages
                        )
                    }
                } else {
                    // Show Torrent Details View
                    // Add BackHandler to intercept back press when details are shown
                    BackHandler(enabled = uiState.selectedTorrentInfo != null) {
                        viewModel.clearSelectedTorrent() // Clear selection to go back to list
                    }
                    Surface(modifier = Modifier.fillMaxSize(), shadowElevation = 2.dp, shape = MaterialTheme.shapes.medium) {
                        TorrentDetailsView(
                            torrentInfo = uiState.selectedTorrentInfo,
                            isLoading = uiState.isLoadingDetails,
                            onCopyMagnet = { magnet ->
                                copyToClipboard(context, magnet)
                                viewModel.showSnackbar("Magnet link copied!")
                            },
                            onDownloadMagnet = { magnet ->
                                openMagnetLink(context, magnet)
                            }
                            // Back navigation is now handled by the BackHandler above
                        )
                    }
                }
            }
            // Action buttons are now moved inside TorrentDetailsView
        }
    }
}

// helper composables

// SearchInputSection and OptionsRow removed as their logic is integrated above

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
fun TorrentDetailsView(
    torrentInfo: TorrentInfo?,
    isLoading: Boolean,
    onCopyMagnet: (String) -> Unit, // Added callbacks
    onDownloadMagnet: (String) -> Unit // Added callbacks
) {
    // Use Column to place content and buttons
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Box(modifier = Modifier.weight(1f)) { // Box to contain the scrollable details or loading/empty state
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
                    // Add extra space at the bottom of the list inside the weighted Box
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
            else -> {
                 // This case should ideally not be reached if TorrentDetailsView is only shown when torrentInfo is not null
                 // But keep it for robustness
                Text("Loading details...", modifier = Modifier.align(Alignment.Center))
            }
            } // <<<<< ADDED: Closing brace for the 'when' statement
        } // End of weighted Box

        // Action buttons at the bottom of the Column
        Spacer(modifier = Modifier.height(8.dp))
        ActionButtons(
            torrentInfo = torrentInfo,
            onCopyMagnet = onCopyMagnet,
            onDownloadMagnet = onDownloadMagnet
        ) // Added missing closing parenthesis
        Spacer(modifier = Modifier.height(8.dp)) // Padding at the very bottom
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
// Composable for the content inside the Filter Bottom Sheet
@Composable
fun FilterBottomSheetContent(
    initialCategory: String?,
    initialSortBy: String?,
    initialOrder: String,
    onApplyFilters: (category: String?, sortBy: String?, order: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var selectedSortBy by remember { mutableStateOf(initialSortBy) }
    var selectedOrder by remember { mutableStateOf(initialOrder) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally // Center children horizontally
    ) {
        Text("Filters & Sorting", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

        DropdownSelector(
            label = "Category",
            options = categoryOptions,
            selectedValue = selectedCategory,
            onValueSelected = { selectedCategory = it },
            modifier = Modifier.padding(bottom = 8.dp) // Removed fillMaxWidth
        )

        DropdownSelector(
            label = "Sort By",
            options = sortOptions,
            selectedValue = selectedSortBy,
            onValueSelected = { selectedSortBy = it },
            modifier = Modifier.padding(bottom = 8.dp) // Removed fillMaxWidth
        )

        DropdownSelector(
            label = "Order",
            options = orderOptions,
            selectedValue = selectedOrder,
            onValueSelected = { selectedOrder = it ?: TorrentOrder.DESC }, // Default to DESC if null somehow
            modifier = Modifier.padding(bottom = 16.dp) // Removed fillMaxWidth
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                onApplyFilters(selectedCategory, selectedSortBy, selectedOrder)
            }) {
                Text("Apply")
            }
        }
        Spacer(modifier = Modifier.height(8.dp)) // Padding at the bottom
    }
}