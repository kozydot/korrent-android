package com.example.korrent.data.repository

import com.example.korrent.data.model.TorrentInfo
import com.example.korrent.data.model.TorrentOrder
import com.example.korrent.data.model.TorrentResult

// interface for accessing torrent data
// abstracts data source (api, cache, etc.)
interface TorrentRepository {

    suspend fun searchTorrents(
        query: String,
        page: Int = 1,
        category: String? = null,
        sortBy: String? = null,
        order: String = TorrentOrder.DESC
    ): Result<TorrentResult>

    suspend fun getTorrentInfo(
        torrentId: String?,
        link: String?
    ): Result<TorrentInfo>

    // --- URL Building ---
    // Expose URL building logic needed by ViewModel for bypass
    fun buildSearchUrl(
        query: String,
        page: Int = 1,
        category: String? = null,
        sortBy: String? = null,
        order: String = TorrentOrder.DESC
    ): String

    fun buildInfoUrl(
        torrentId: String?,
        link: String?
    ): String

    // TODO: Add methods for trending, top, popular, browse if needed
}