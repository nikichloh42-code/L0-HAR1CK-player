package com.custom.player.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ArtistStat(
    val artistName: String,
    val totalTimeMs: Long
)

data class AlbumStat(
    val albumName: String,
    val artistName: String,
    val artworkUri: String?,
    val totalTimeMs: Long
)

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks")
    fun observeAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackById(trackId: Long): TrackEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracksIgnore(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("UPDATE tracks SET isLiked = NOT isLiked WHERE id = :trackId")
    suspend fun toggleLike(trackId: Long)

    @Query("""
        UPDATE tracks 
        SET playCount = playCount + 1, 
            lastPlayedAtMs = :playedAtMs 
        WHERE id = :trackId
    """)
    suspend fun incrementPlayCount(trackId: Long, playedAtMs: Long)

    @Query("""
        UPDATE tracks 
        SET totalPlayTimeMs = totalPlayTimeMs + :deltaMs 
        WHERE id = :trackId
    """)
    suspend fun addPlayTime(trackId: Long, deltaMs: Long)

    @Query("SELECT COALESCE(SUM(totalPlayTimeMs), 0) FROM tracks")
    fun observeTotalPlayTimeMs(): Flow<Long>

    @Query("""
        SELECT COALESCE(customArtist, artist) AS artistName,
               SUM(totalPlayTimeMs) AS totalTimeMs
        FROM tracks
        GROUP BY artistName
        HAVING totalTimeMs > 0
        ORDER BY totalTimeMs DESC
        LIMIT 5
    """)
    fun observeTop5Artists(): Flow<List<ArtistStat>>

    @Query("""
        SELECT COALESCE(customAlbum, album) AS albumName,
               COALESCE(customArtist, artist) AS artistName,
               COALESCE(customArtworkUri, originalArtworkUri) AS artworkUri,
               SUM(totalPlayTimeMs) AS totalTimeMs
        FROM tracks
        GROUP BY albumName, artistName
        HAVING totalTimeMs > 0
        ORDER BY totalTimeMs DESC
        LIMIT 5
    """)
    fun observeTop5Albums(): Flow<List<AlbumStat>>
}
