package com.example.korrent.data.remote

import android.net.Uri
import android.util.Log
import com.example.korrent.data.model.* // import data models
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException // for network/parsing errors

class TorrentService {

    private val client = NetworkModule.client
    private val baseUrl = NetworkModule.BASE_URL

    // --- url building logic (simplified from py1337x.utils) ---

    private fun sanitizeQuery(query: String): String {
        return query.replace(" ", "+") // basic space replacement
    }

    private fun buildSearchUrl(
        query: String,
        page: Int = 1,
        category: String? = null,
        sortBy: String? = null,
        order: String = TorrentOrder.DESC
    ): String {
        val sanitizedQuery = sanitizeQuery(query)
        val path = if (category != null && sortBy != null) {
            // category and sort specified
            "/sort-category-search/${sanitizedQuery}/${category}/${sortBy.replaceFirstChar { it.uppercase() }}/${order.replaceFirstChar { it.uppercase() }}/${page}/"
        } else if (category != null) {
            // only category specified
            "/category-search/${sanitizedQuery}/${category}/${page}/"
        } else if (sortBy != null) {
            // only sort specified
            "/sort-search/${sanitizedQuery}/${sortBy.replaceFirstChar { it.uppercase() }}/${order.replaceFirstChar { it.uppercase() }}/${page}/"
        } else {
            // basic search
            "/search/${sanitizedQuery}/${page}/"
        }
        return baseUrl + path
    }

     private fun buildInfoUrl(torrentId: String?, link: String?): String {
        if (torrentId != null) {
            // construct url from torrentid (assuming standard format)
            // need to find the exact format from py1337x or website structure
            // placeholder: assume id is part of the path like /torrent/id/name/
            // this needs verification!
            return "$baseUrl/torrent/$torrentId/placeholder/" // placeholder url structure
        } else if (link != null) {
            // use the provided link directly if it's absolute, otherwise prepend base url
            return if (link.startsWith("http")) link else baseUrl + link
        } else {
            throw IllegalArgumentException("either torrentid or link must be provided")
        }
    }


    // --- parsing logic (simplified from py1337x.parser) ---

    private fun parseTorrentList(html: String, currentPage: Int): TorrentResult {
        val document: Document = Jsoup.parse(html)
        val items = mutableListOf<TorrentItem>()
        // var itemCount = 0 // Removed unused variable
        var pageCount = 1 // default if pagination not found

        try {
            // find the table containing results (selector might need adjustment)
            val tableRows = document.select("table.table-list tbody tr")
            // itemCount = tableRows.size // Removed assignment to unused variable

            for (row in tableRows) {
                val nameElement = row.selectFirst("td.name a:nth-child(2)") // link containing name and href
                val seedersElement = row.selectFirst("td.seeds")
                val leechersElement = row.selectFirst("td.leeches")
                val sizeElement = row.selectFirst("td.size")
                val timeElement = row.selectFirst("td.time")
                val uploaderElement = row.selectFirst("td.coll-5 a") // uploader link

                val name = nameElement?.text() ?: "n/a"
                val url = nameElement?.attr("href") ?: ""
                val torrentId = url.split("/").getOrNull(2) ?: "" // extract id from url (fragile)
                val seeders = seedersElement?.text() ?: "0"
                val leechers = leechersElement?.text() ?: "0"
                // remove potential hidden span text in size
                val size = sizeElement?.ownText() ?: "n/a"
                val time = timeElement?.text() ?: "n/a"
                val uploader = uploaderElement?.text() ?: "n/a"
                val uploaderLink = uploaderElement?.attr("href")

                if (torrentId.isNotEmpty()) { // basic validation
                     items.add(
                        TorrentItem(
                            name = name,
                            torrentId = torrentId,
                            url = baseUrl + url, // make absolute url
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

            // parse pagination (selector might need adjustment)
            val lastPageElement = document.select(".pagination li.last a").first()
            pageCount = lastPageElement?.attr("href")?.split("/")?.lastOrNull()?.toIntOrNull() ?: currentPage

        } catch (e: Exception) {
            Log.e("TorrentService", "error parsing torrent list: ${e.message}", e)
            // return empty/partial result on error
        }

        return TorrentResult(
            items = items,
            currentPage = currentPage,
            itemCount = items.size, // use actual parsed items count
            pageCount = pageCount
        )
    }

     private fun parseTorrentInfo(html: String): TorrentInfo {
        val document: Document = Jsoup.parse(html)
        // selectors need careful inspection of the 1337x torrent details page html structure
        try {
            // more robust selectors targeting the structure commonly seen
            val detailList = document.selectFirst("div.box-info-detail ul")

            val name = document.selectFirst("div.box-info-heading h1")?.text()
            val category = detailList?.select("li:has(strong:contains(category)) > span")?.first()?.text()
            val type = detailList?.select("li:has(strong:contains(type)) > span")?.first()?.text()
            val language = detailList?.select("li:has(strong:contains(language)) > span")?.first()?.text()
            val size = detailList?.select("li:has(strong:contains(total size)) > span")?.first()?.text()
            // uploader might be inside an <a> tag within the span
            val uploaderElement = detailList?.select("li:has(strong:contains(uploaded by)) > span")?.first()
            val uploader = uploaderElement?.selectFirst("a")?.text() ?: uploaderElement?.text() // get link text or span text
            val downloads = detailList?.select("li:has(strong:contains(downloads)) > span")?.first()?.text()
            val lastChecked = detailList?.select("li:has(strong:contains(last checked)) > span")?.first()?.text()
            val dateUploaded = detailList?.select("li:has(strong:contains(date uploaded)) > span")?.first()?.text()
            val seeders = detailList?.select("li span.seeds")?.first()?.text() // more specific within the list
            val leechers = detailList?.select("li span.leeches")?.first()?.text() // more specific within the list

            // corrected magnet link selector: find the first magnet link within the main torrent detail box
            val magnetLink = document.selectFirst("div.torrent-detail-page a[href^=magnet:]")?.attr("href")
            // extract infohash using string manipulation to avoid uri.parse issues
            val infoHash = magnetLink?.substringAfter("urn:btih:", "")?.substringBefore('&')
            // corrected description selector
            val description = document.selectFirst("div#description")?.html()

             // basic validation: check if essential fields were parsed
             if (name.isNullOrBlank() || magnetLink.isNullOrBlank()) {
                 Log.e("TorrentService", "failed to parse essential torrent details (name or magnet link). name: $name, magnet: $magnetLink")
                 throw IOException("failed to parse essential torrent details from html.")
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
                // add other fields like genre, thumbnail, images if selectors are found
            )
        } catch (e: Exception) {
             Log.e("TorrentService", "error parsing torrent info: ${e.message}", e)
             throw IOException("failed to parse torrent details", e) // re-throw as ioexception
        }
    }


    // --- public api methods ---

    suspend fun search(
        query: String,
        page: Int = 1,
        category: String? = null,
        sortBy: String? = null,
        order: String = TorrentOrder.DESC
    ): Result<TorrentResult> { // use kotlin result for error handling
        return try {
            val url = buildSearchUrl(query, page, category, sortBy, order)
            Log.d("TorrentService", "searching url: $url")
            val response = client.get(url)
            if (response.status == HttpStatusCode.OK) {
                val html: String = response.body()
                // assume cookies were applied by interceptor if needed.
                // if this still fails later, the viewmodel needs to trigger the interactive bypass.
                Result.success(parseTorrentList(html, page))
            } else {
                 // propagate specific http errors
                Log.w("TorrentService", "search request failed for $url with status: ${response.status}")
                Result.failure(IOException("search request failed: ${response.status.value} ${response.status.description}"))
            }
        } catch (e: Exception) {
            Log.e("TorrentService", "search exception for query '$query': ${e.message}", e)
            Result.failure(e)
        }
    }

     suspend fun getInfo(torrentId: String?, link: String?): Result<TorrentInfo> {
         return try {
             val url = buildInfoUrl(torrentId, link)
             Log.d("TorrentService", "fetching info url: $url")
             val response = client.get(url)
             if (response.status == HttpStatusCode.OK) {
                 val html: String = response.body()
                 // removed raw html logging
                 // assume cookies were applied by interceptor if needed.
                 Result.success(parseTorrentInfo(html))
             } else {
                 Log.w("TorrentService", "info request failed for $url with status: ${response.status}")
                 Result.failure(IOException("info request failed: ${response.status.value} ${response.status.description}"))
             }
         } catch (e: Exception) {
             Log.e("TorrentService", "get info exception: ${e.message}", e)
             Result.failure(e)
         }
     }

    // todo: implement trending, top, popular, browse methods similarly
}