package com.custom.player.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dateAddedSec: Long,
    val contentUri: String,
    val customTitle: String? = null,
    val customArtist: String? = null,
    val customAlbum: String? = null,
    val customArtworkUri: String? = null,
    val originalArtworkUri: String? = null,
    val isLiked: Boolean = false,
    val playCount: Int = 0,
    val totalPlayTimeMs: Long = 0L,
    val lastPlayedAtMs: Long = 0L
) {
    val displayTitle: String get() = customTitle?.takeIf { it.isNotBlank() } ?: title
    val displayArtist: String get() = customArtist?.takeIf { it.isNotBlank() } ?: artist
    val displayAlbum: String get() = customAlbum?.takeIf { it.isNotBlank() } ?: album
    val displayArtworkUri: String? get() = customArtworkUri?.takeIf { it.isNotBlank() } ?: originalArtworkUri
}
