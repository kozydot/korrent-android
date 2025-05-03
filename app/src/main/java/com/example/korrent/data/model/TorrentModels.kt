package com.example.korrent.data.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.Serializable

/**
 * represents a single torrent item in search results.
 * like py1337x.models.torrentitem
 */
@Serializable // make serializable if needed for caching or passing around
data class TorrentItem(
    val name: String,
    val torrentId: String, // renamed from torrent_id for kotlin style
    val url: String,
    val seeders: String,
    val leechers: String,
    val size: String,
    val time: String,
    val uploader: String,
    val uploaderLink: String? = null // renamed, nullable 'cause it might be missing
)

/**
 * represents the result of a torrent search.
 * like py1337x.models.torrentresult
 */
@Serializable
data class TorrentResult(
    val items: ImmutableList<TorrentItem>,
    val currentPage: Int,
    val itemCount: Int,
    val pageCount: Int
)

/**
 * represents detailed info about a torrent.
 * like py1337x.models.torrentinfo
 * all fields are nullable 'cause parsing might fail sometimes.
 */
@Serializable
data class TorrentInfo(
    val name: String? = null,
    val shortName: String? = null, // renamed
    val description: String? = null,
    val category: String? = null,
    val type: String? = null,
    val genre: ImmutableList<String>? = null,
    val language: String? = null,
    val size: String? = null,
    val thumbnail: String? = null,
    val images: ImmutableList<String>? = null,
    val uploader: String? = null,
    val uploaderLink: String? = null, // renamed
    val downloads: String? = null,
    val lastChecked: String? = null, // renamed
    val dateUploaded: String? = null, // renamed
    val seeders: String? = null,
    val leechers: String? = null,
    val magnetLink: String? = null, // renamed
    val infoHash: String? = null // renamed
)

// constants for categories and sorting, like py1337x types
// maybe move these to a constants file later

object TorrentCategory {
    const val MOVIES = "Movies"
    const val TV = "TV"
    const val GAMES = "Games"
    const val MUSIC = "Music"
    const val APPS = "Apps"
    const val ANIME = "Anime"
    const val DOCUMENTARIES = "Documentaries"
    const val OTHER = "Other"
    const val XXX = "XXX" // note: consider filtering this if needed
    // add more if the lib supports them, check py1337x.types.category
}

object TorrentSort {
    const val TIME = "time"
    const val SIZE = "size"
    const val SEEDERS = "seeders"
    const val LEECHERS = "leechers"
}

object TorrentOrder {
    const val ASC = "asc"
    const val DESC = "desc"
}