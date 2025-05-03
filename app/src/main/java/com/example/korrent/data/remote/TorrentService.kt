package com.example.korrent.data.remote

import android.net.Uri
import android.util.Log
import com.example.korrent.data.model.* // data models
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException // network/parsing errors
import kotlinx.coroutines.Dispatchers // for switching context
import kotlinx.coroutines.withContext // for switching context

import kotlinx.collections.immutable.toImmutableList // Add this import
class TorrentService {

    companion object {
        private const val TAG = "TorrentService"

        // Jsoup Selectors for Search Results Page
        private const val SELECTOR_SEARCH_TABLE_ROWS = "table.table-list tbody tr"
        private const val SELECTOR_SEARCH_NAME_LINK = "td.name a:nth-child(2)"
        private const val SELECTOR_SEARCH_SEEDERS = "td.seeds"
        private const val SELECTOR_SEARCH_LEECHERS = "td.leeches"
        private const val SELECTOR_SEARCH_SIZE = "td.size"
        private const val SELECTOR_SEARCH_TIME = "td.time"
        private const val SELECTOR_SEARCH_UPLOADER_LINK = "td.coll-5 a"
        private const val SELECTOR_SEARCH_PAGINATION_LAST = ".pagination li.last a"

        // Jsoup Selectors for Torrent Info Page
        private const val SELECTOR_INFO_HEADING = "div.box-info-heading h1"
        private const val SELECTOR_INFO_DETAIL_LIST = "div.box-info-detail ul"
        private const val SELECTOR_INFO_CATEGORY = "li:has(strong:contains(category)) > span"
        private const val SELECTOR_INFO_TYPE = "li:has(strong:contains(type)) > span"
        private const val SELECTOR_INFO_LANGUAGE = "li:has(strong:contains(language)) > span"
        private const val SELECTOR_INFO_SIZE = "li:has(strong:contains(total size)) > span"
        private const val SELECTOR_INFO_UPLOADER = "li:has(strong:contains(uploaded by)) > span" // Contains link or text
        private const val SELECTOR_INFO_DOWNLOADS = "li:has(strong:contains(downloads)) > span"
        private const val SELECTOR_INFO_LAST_CHECKED = "li:has(strong:contains(last checked)) > span"
        private const val SELECTOR_INFO_DATE_UPLOADED = "li:has(strong:contains(date uploaded)) > span"
        private const val SELECTOR_INFO_SEEDERS = "li span.seeds"
        private const val SELECTOR_INFO_LEECHERS = "li span.leeches"
        private const val SELECTOR_INFO_MAGNET_LINK = "div.torrent-detail-page a[href^=magnet:]"
        private const val SELECTOR_INFO_DESCRIPTION = "div#description"
    }

    private val client = NetworkModule.client
    private val baseUrl = NetworkModule.BASE_URL

    // url building (simplified from py1337x.utils)

    private fun sanitizeQuery(query: String): String {
        return query.replace(" ", "+") // basic space replace
    }

    internal fun buildSearchUrl( // Make internal
        query: String,
        page: Int = 1,
        category: String? = null,
        sortBy: String? = null,
        order: String = TorrentOrder.DESC
    ): String {
        val sanitizedQuery = sanitizeQuery(query)
        val path = if (category != null && sortBy != null) {
            // category & sort
            "/sort-category-search/${sanitizedQuery}/${category}/${sortBy}/${order}/${page}/"
        } else if (category != null) {
            // only category
            "/category-search/${sanitizedQuery}/${category}/${page}/"
        } else if (sortBy != null) {
            // only sort
            "/sort-search/${sanitizedQuery}/${sortBy}/${order}/${page}/"
        } else {
            // basic
            "/search/${sanitizedQuery}/${page}/"
        }
        return baseUrl + path
    }

     internal fun buildInfoUrl(torrentId: String?, link: String?): String { // Make internal
        if (torrentId != null) {
            // construct url from id (assuming format)
            // todo: verify exact format from py1337x/website
            // placeholder: /torrent/id/name/
            return "$baseUrl/torrent/$torrentId/placeholder/" // placeholder
        } else if (link != null) {
            // use provided link (make absolute if needed)
            return if (link.startsWith("http")) link else baseUrl + link
        } else {
            throw IllegalArgumentException("need torrentid or link")
        }
    }


    // parsing logic (simplified from py1337x.parser)

    private fun parseTorrentList(html: String, currentPage: Int): TorrentResult {
        val document: Document = Jsoup.parse(html)
        val items = mutableListOf<TorrentItem>()
        var pageCount = 1 // default if pagination missing

        try {
            // find the table containing results
            val tableRows = document.select(SELECTOR_SEARCH_TABLE_ROWS)

            for (row in tableRows) {
                val nameElement = row.selectFirst(SELECTOR_SEARCH_NAME_LINK)
                val seedersElement = row.selectFirst(SELECTOR_SEARCH_SEEDERS)
                val leechersElement = row.selectFirst(SELECTOR_SEARCH_LEECHERS)
                val sizeElement = row.selectFirst(SELECTOR_SEARCH_SIZE)
                val timeElement = row.selectFirst(SELECTOR_SEARCH_TIME)
                val uploaderElement = row.selectFirst(SELECTOR_SEARCH_UPLOADER_LINK)

                val name = nameElement?.text() ?: "n/a"
                val url = nameElement?.attr("href") ?: ""
                val torrentId = url.split("/").getOrNull(2) ?: "" // extract id (fragile)
                val seeders = seedersElement?.text() ?: "0"
                val leechers = leechersElement?.text() ?: "0"
                // remove hidden span text in size
                val size = sizeElement?.ownText() ?: "n/a"
                val time = timeElement?.text() ?: "n/a"
                val uploader = uploaderElement?.text() ?: "n/a"
                val uploaderLink = uploaderElement?.attr("href")

                if (torrentId.isNotEmpty()) { // basic check
                     items.add(
                        TorrentItem(
                            name = name,
                            torrentId = torrentId,
                            url = baseUrl + url, // make absolute
                            seeders = seeders,
                            leechers = leechers,
                            size = size,
                            time = time,
                            uploader = uploader,
                            uploaderLink = if (uploaderLink != null) baseUrl + uploaderLink else null
                        )
                    )
                }
            }

            // parse pagination
            val lastPageElement = document.select(SELECTOR_SEARCH_PAGINATION_LAST).first()
            pageCount = lastPageElement?.attr("href")?.split("/")?.lastOrNull()?.toIntOrNull() ?: currentPage

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing torrent list: ${e.message}", e)
            // return empty/partial result on error
        }

        return TorrentResult(
            items = items.toImmutableList(), // Convert to ImmutableList
            currentPage = currentPage,
            itemCount = items.size, // use actual count
            pageCount = pageCount
        )
    }

     private fun parseTorrentInfo(html: String): TorrentInfo {
        val document: Document = Jsoup.parse(html)
        // selectors need careful check of 1337x details page html
        try {
            val detailList = document.selectFirst(SELECTOR_INFO_DETAIL_LIST)

            val name = document.selectFirst(SELECTOR_INFO_HEADING)?.text()
            val category = detailList?.select(SELECTOR_INFO_CATEGORY)?.first()?.text()
            val type = detailList?.select(SELECTOR_INFO_TYPE)?.first()?.text()
            val language = detailList?.select(SELECTOR_INFO_LANGUAGE)?.first()?.text()
            val size = detailList?.select(SELECTOR_INFO_SIZE)?.first()?.text()
            val uploaderElement = detailList?.select(SELECTOR_INFO_UPLOADER)?.first()
            val uploader = uploaderElement?.selectFirst("a")?.text() ?: uploaderElement?.text() // get link text or span text
            val downloads = detailList?.select(SELECTOR_INFO_DOWNLOADS)?.first()?.text()
            val lastChecked = detailList?.select(SELECTOR_INFO_LAST_CHECKED)?.first()?.text()
            val dateUploaded = detailList?.select(SELECTOR_INFO_DATE_UPLOADED)?.first()?.text()
            val seeders = detailList?.select(SELECTOR_INFO_SEEDERS)?.first()?.text()
            val leechers = detailList?.select(SELECTOR_INFO_LEECHERS)?.first()?.text()

            val magnetLink = document.selectFirst(SELECTOR_INFO_MAGNET_LINK)?.attr("href")
            val infoHash = magnetLink?.substringAfter("urn:btih:", "")?.substringBefore('&')
            val description = document.selectFirst(SELECTOR_INFO_DESCRIPTION)?.html()

             // basic check: essential fields parsed?
             if (name.isNullOrBlank() || magnetLink.isNullOrBlank()) {
                 Log.e(TAG, "Failed to parse essential torrent details (name or magnet link). Name: $name, Magnet: $magnetLink")
                 throw IOException("Failed to parse essential torrent details from HTML.")
             }

            return TorrentInfo(
                name = name,
                category = category,
                type = type,
                language = language,
                size = size,
                uploader = uploader,
                downloads = downloads,
                lastChecked = lastChecked,
                dateUploaded = dateUploaded,
                seeders = seeders,
                leechers = leechers,
                magnetLink = magnetLink,
                infoHash = infoHash,
                description = description
                // todo: add genre, thumbnail, images if selectors found
            )
        } catch (e: Exception) {
             Log.e(TAG, "Error parsing torrent info: ${e.message}", e)
             throw IOException("Failed to parse torrent details", e) // re-throw as ioexception
        }
    }


    // public api methods

    suspend fun search(
        query: String,
        page: Int = 1,
        category: String? = null,
        sortBy: String? = null,
        order: String = TorrentOrder.DESC
    ): Result<TorrentResult> { // use kotlin result
        return try {
            val url = buildSearchUrl(query, page, category, sortBy, order)
            Log.d(TAG, "Searching URL: $url")
            val response = client.get(url)
            if (response.status == HttpStatusCode.OK) {
                val html: String = response.body()
                // Assume cookies were applied by interceptor if needed.
                // If this still fails later, the viewmodel needs to trigger the interactive bypass.
                // move parsing to default dispatcher (cpu intensive)
                val parsedResult = withContext(Dispatchers.Default) {
                    parseTorrentList(html, page)
                }
                Result.success(parsedResult)
            } else {
                 // Propagate specific HTTP errors
                Log.w(TAG, "Search request failed for $url with status: ${response.status}")
                Result.failure(IOException("Search request failed: ${response.status.value} ${response.status.description}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search exception for query '$query': ${e.message}", e)
            Result.failure(e)
        }
    }

     suspend fun getInfo(torrentId: String?, link: String?): Result<TorrentInfo> {
         return try {
             val url = buildInfoUrl(torrentId, link)
             Log.d(TAG, "Fetching info URL: $url")
             val response = client.get(url)
             if (response.status == HttpStatusCode.OK) {
                 val html: String = response.body()
                 // Assume cookies were applied by interceptor if needed.
                 // move parsing to default dispatcher (cpu intensive)
                 val parsedInfo = withContext(Dispatchers.Default) {
                     parseTorrentInfo(html)
                 }
                 Result.success(parsedInfo)
             } else {
                 Log.w(TAG, "Info request failed for $url with status: ${response.status}")
                 Result.failure(IOException("Info request failed: ${response.status.value} ${response.status.description}"))
             }
         } catch (e: Exception) {
             Log.e(TAG, "Get info exception: ${e.message}", e)
             Result.failure(e)
         }
     }

    // todo: implement trending, top, popular, browse similarly
}