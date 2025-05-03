package com.example.korrent.data.repository

import com.example.korrent.data.model.TorrentInfo
import com.example.korrent.data.model.TorrentOrder
import com.example.korrent.data.model.TorrentResult
import com.example.korrent.data.remote.TorrentService

// implementation of torrentrepository using remote torrentservice
class TorrentRepositoryImpl(
    private val torrentService: TorrentService // injected service
) : TorrentRepository {

    override suspend fun searchTorrents(
        query: String,
        page: Int,
        category: String?,
        sortBy: String?,
        order: String
    ): Result<TorrentResult> {
        // pass call to service
        return torrentService.search(query, page, category, sortBy, order)
    }

    override suspend fun getTorrentInfo(
        torrentId: String?,
        link: String?
    ): Result<TorrentInfo> {
        // pass call to service
        return torrentService.getInfo(torrentId, link)
    }

    // todo: implement other repo methods via service calls
}