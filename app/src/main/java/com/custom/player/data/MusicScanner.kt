package com.custom.player.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicScanner(private val context: Context) {

    suspend fun scanDeviceTracks(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<TrackEntity>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val albumArtBaseUri = Uri.parse("content://media/external/audio/albumart")

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 1000"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Без названия"
                val artist = cursor.getString(artistCol)?.takeIf {
                    it.isNotBlank() && it != "<unknown>"
                } ?: "Неизвестный исполнитель"
                val album = cursor.getString(albumCol)?.takeIf {
                    it.isNotBlank() && it != "<unknown>"
                } ?: "Без альбома"
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durationCol)
                val dateAdded = cursor.getLong(dateAddedCol)

                val contentUri = ContentUris.withAppendedId(collection, id).toString()
                val artworkUri = if (albumId > 0) {
                    ContentUris.withAppendedId(albumArtBaseUri, albumId).toString()
                } else {
                    null
                }

                tracks.add(
                    TrackEntity(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        dateAddedSec = dateAdded,
                        contentUri = contentUri,
                        originalArtworkUri = artworkUri
                    )
                )
            }
        }
        tracks
    }
}
