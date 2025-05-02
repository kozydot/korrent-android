package com.example.korrent.data.repository

import com.example.korrent.data.model.TorrentInfo
import com.example.korrent.data.model.TorrentOrder
import com.example.korrent.data.model.TorrentResult

/**
 * Interface for accessing torrent data.
 * Abstracts the data source (remote API, local cache, etc.).
 */
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

    // TODO: Add methods for trending, top, popular, browse if needed
}