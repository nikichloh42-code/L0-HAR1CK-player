package com.custom.player.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.custom.player.data.AlbumStat
import com.custom.player.data.AppThemeMode
import com.custom.player.data.ArtistStat
import com.custom.player.data.MusicScanner
import com.custom.player.data.PlayerDatabase
import com.custom.player.data.PlayerSettings
import com.custom.player.data.PreferencesManager
import com.custom.player.data.SortOption
import com.custom.player.data.TrackEntity
import com.custom.player.player.EqBandUi
import com.custom.player.player.EqualizerController
import com.custom.player.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SearchCategory {
    ALL, TRACKS, ARTISTS, PLAYLISTS, ALBUMS
}

enum class AutoPlaylistType {
    RECENT, LIKED, MOST_PLAYED
}

enum class QueueRepeatMode {
    REPEAT_ONE, REPEAT_ALL, SHUFFLE
}

data class PlayerUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val currentTrackId: Long? = null,
    val queueTrackIds: List<Long> = emptyList(),
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: QueueRepeatMode = QueueRepeatMode.REPEAT_ALL,
    val searchQuery: String = "",
    val searchCategory: SearchCategory = SearchCategory.ALL,
    val selectedAutoPlaylist: AutoPlaylistType? = null,
    val selectedAlbumFilter: String? = null,
    val selectedArtistFilter: String? = null,
    val isScanning: Boolean = false,
    val eqBands: List<EqBandUi> = emptyList(),
    val eqPresets: List<String> = emptyList(),
    val activeEqPreset: Int = 0
) {
    val currentTrack: TrackEntity?
        get() = tracks.find { it.id == currentTrackId }

    val queueTracks: List<TrackEntity>
        get() {
            val map = tracks.associateBy { it.id }
            return queueTrackIds.mapNotNull { map[it] }
        }
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val db = PlayerDatabase.getInstance(context)
    private val trackDao = db.trackDao()
    private val prefsManager = PreferencesManager(context)
    private val scanner = MusicScanner(context)
    private val eqController = EqualizerController()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null
    private var lastCountedTrackId: Long? = null

    val settings: StateFlow<PlayerSettings> = prefsManager.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerSettings())

    val totalPlayTimeMs: StateFlow<Long> = trackDao.observeTotalPlayTimeMs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val top5Albums: StateFlow<List<AlbumStat>> = trackDao.observeTop5Albums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val top5Artists: StateFlow<List<ArtistStat>> = trackDao.observeTop5Artists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(trackDao.observeAllTracks(), settings) { rawTracks, currentSettings ->
                sortTracks(rawTracks, currentSettings.sortOption)
            }.collect { sorted ->
                _uiState.update { state ->
                    val syncedQueue = if (state.queueTrackIds.isEmpty() && sorted.isNotEmpty()) {
                        sorted.map { it.id }
                    } else {
                        val validIds = sorted.map { it.id }.toSet()
                        state.queueTrackIds.filter { it in validIds }
                    }
                    state.copy(tracks = sorted, queueTrackIds = syncedQueue)
                }
            }
        }
        connectToMediaSession()
    }

    private fun sortTracks(list: List<TrackEntity>, sort: SortOption): List<TrackEntity> {
        return when (sort) {
            SortOption.TITLE_ASC -> list.sortedBy { it.displayTitle.lowercase() }
            SortOption.TITLE_DESC -> list.sortedByDescending { it.displayTitle.lowercase() }
            SortOption.ARTIST -> list.sortedBy { it.displayArtist.lowercase() }
            SortOption.DATE_ADDED -> list.sortedByDescending { it.dateAddedSec }
            SortOption.DURATION -> list.sortedByDescending { it.durationMs }
            SortOption.PLAY_COUNT -> list.sortedByDescending { it.playCount }
        }
    }

    private fun connectToMediaSession() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            mediaController = controller
            setupEqualizerForCurrentSession(controller)
            controller.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                    if (isPlaying) startTrackingPlayback() else progressJob?.cancel()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val id = mediaItem?.mediaId?.toLongOrNull()
                    _uiState.update {
                        it.copy(
                            currentTrackId = id,
                            positionMs = 0L,
                            durationMs = controller.duration.coerceAtLeast(0L)
                        )
                    }
                    if (id != null && id != lastCountedTrackId && controller.playWhenReady) {
                        lastCountedTrackId = id
                        viewModelScope.launch {
                            trackDao.incrementPlayCount(id, System.currentTimeMillis())
                        }
                    }
                    setupEqualizerForCurrentSession(controller)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _uiState.update {
                        it.copy(durationMs = controller.duration.coerceAtLeast(0L))
                    }
                }
            })
            syncQueueWithPlayer(controller, _uiState.value.queueTrackIds, startIndex = 0, playNow = false)
        }, MoreExecutors.directExecutor())
    }

    private fun setupEqualizerForCurrentSession(controller: MediaController) {
        val s = settings.value
        val sessionId = runCatching {
            val field = controller.javaClass.getDeclaredField("audioSessionId")
            field.isAccessible = true
            field.getInt(controller)
        }.getOrDefault(0)
        eqController.attachToSession(sessionId, s.equalizerEnabled, s.equalizerBands)
        _uiState.update {
            it.copy(
                eqBands = eqController.getBands(),
                eqPresets = eqController.getPresets(),
                activeEqPreset = s.equalizerPresetIndex
            )
        }
    }

    private fun startTrackingPlayback() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val tickMs = 500L
            while (isActive) {
                val ctrl = mediaController
                val trackId = _uiState.value.currentTrackId
                if (ctrl != null && ctrl.isPlaying) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.coerceAtLeast(0L)
                    _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                    if (trackId != null) {
                        trackDao.addPlayTime(trackId, tickMs)
                    }
                }
                delay(tickMs)
            }
        }
    }

    fun scanLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val scanned = scanner.scanDeviceTracks()
            trackDao.insertTracksIgnore(scanned)
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    fun playTrackFromList(track: TrackEntity, list: List<TrackEntity>) {
        val ids = list.map { it.id }
        val index = ids.indexOf(track.id).coerceAtLeast(0)
        lastCountedTrackId = track.id
        viewModelScope.launch {
            trackDao.incrementPlayCount(track.id, System.currentTimeMillis())
        }
        _uiState.update { it.copy(queueTrackIds = ids, currentTrackId = track.id) }
        mediaController?.let { syncQueueWithPlayer(it, ids, index, playNow = true) }
    }

    fun playFromQueueIndex(index: Int) {
        val ids = _uiState.value.queueTrackIds
        val trackId = ids.getOrNull(index) ?: return
        lastCountedTrackId = trackId
        viewModelScope.launch {
            trackDao.incrementPlayCount(trackId, System.currentTimeMillis())
        }
        _uiState.update { it.copy(currentTrackId = trackId) }
        mediaController?.let {
            it.seekToDefaultPosition(index)
            it.playWhenReady = true
            it.play()
        }
    }

    private fun syncQueueWithPlayer(
        controller: MediaController,
        trackIds: List<Long>,
        startIndex: Int,
        playNow: Boolean
    ) {
        val trackMap = _uiState.value.tracks.associateBy { it.id }
        val items = trackIds.mapNotNull { id ->
            val t = trackMap[id] ?: return@mapNotNull null
            val metadata = MediaMetadata.Builder()
                .setTitle(t.displayTitle)
                .setArtist(t.displayArtist)
                .setAlbumTitle(t.displayAlbum)
                .setArtworkUri(t.displayArtworkUri?.toUri())
                .build()
            MediaItem.Builder()
                .setMediaId(t.id.toString())
                .setUri(t.contentUri.toUri())
                .setMediaMetadata(metadata)
                .build()
        }
        if (items.isEmpty()) return
        controller.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        controller.prepare()
        if (playNow) {
            controller.playWhenReady = true
            controller.play()
        }
    }

    fun togglePlayPause() {
        val ctrl = mediaController ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    fun skipNext() {
        mediaController?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        mediaController?.seekToPreviousMediaItem()
    }

    fun setRepeatMode(mode: QueueRepeatMode) {
        _uiState.update { it.copy(repeatMode = mode) }
        val ctrl = mediaController ?: return
        when (mode) {
            QueueRepeatMode.REPEAT_ONE -> {
                ctrl.shuffleModeEnabled = false
                ctrl.repeatMode = Player.REPEAT_MODE_ONE
            }
            QueueRepeatMode.REPEAT_ALL -> {
                ctrl.shuffleModeEnabled = false
                ctrl.repeatMode = Player.REPEAT_MODE_ALL
            }
            QueueRepeatMode.SHUFFLE -> {
                ctrl.shuffleModeEnabled = true
                ctrl.repeatMode = Player.REPEAT_MODE_ALL
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.queueTrackIds.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@update state
            }
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            mediaController?.moveMediaItem(fromIndex, toIndex)
            state.copy(queueTrackIds = list)
        }
    }

    fun addTrackToQueue(track: TrackEntity, playNext: Boolean = false) {
        _uiState.update { state ->
            val list = state.queueTrackIds.toMutableList()
            val currentIdx = state.currentTrackId?.let { list.indexOf(it) } ?: -1
            val insertIdx = if (playNext && currentIdx >= 0) {
                (currentIdx + 1).coerceAtMost(list.size)
            } else {
                list.size
            }
            list.add(insertIdx, track.id)

            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.contentUri.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.displayTitle)
                        .setArtist(track.displayArtist)
                        .setAlbumTitle(track.displayAlbum)
                        .setArtworkUri(track.displayArtworkUri?.toUri())
                        .build()
                )
                .build()
            mediaController?.addMediaItem(insertIdx, mediaItem)
            state.copy(queueTrackIds = list)
        }
    }

    fun removeFromQueue(index: Int) {
        _uiState.update { state ->
            val list = state.queueTrackIds.toMutableList()
            if (index !in list.indices) return@update state
            list.removeAt(index)
            mediaController?.removeMediaItem(index)
            state.copy(queueTrackIds = list)
        }
    }

    fun toggleLike(trackId: Long) {
        viewModelScope.launch { trackDao.toggleLike(trackId) }
    }

    fun updateTrackMetadata(
        track: TrackEntity,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newCoverUri: Uri?
    ) {
        viewModelScope.launch {
            val updated = track.copy(
                customTitle = newTitle.trim().takeIf { it.isNotEmpty() },
                customArtist = newArtist.trim().takeIf { it.isNotEmpty() },
                customAlbum = newAlbum.trim().takeIf { it.isNotEmpty() },
                customArtworkUri = newCoverUri?.toString() ?: track.customArtworkUri
            )
            trackDao.updateTrack(updated)
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchCategory(category: SearchCategory) {
        _uiState.update { it.copy(searchCategory = category) }
    }

    fun selectAutoPlaylist(type: AutoPlaylistType?) {
        _uiState.update { it.copy(selectedAutoPlaylist = type) }
    }

    fun selectAlbumFilter(album: String?) {
        _uiState.update { it.copy(selectedAlbumFilter = album) }
    }

    fun selectArtistFilter(artist: String?) {
        _uiState.update { it.copy(selectedArtistFilter = artist) }
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch { prefsManager.setSortOption(option) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefsManager.setThemeMode(mode) }
    }

    fun setUiScale(scale: Float) {
        viewModelScope.launch { prefsManager.setUiScale(scale) }
    }

    fun setWallpaper(uri: Uri?) {
        viewModelScope.launch { prefsManager.setWallpaper(uri?.toString()) }
    }

    fun setWallpaperDim(dim: Float) {
        viewModelScope.launch { prefsManager.setWallpaperDim(dim) }
    }

    fun setWallpaperBlur(blur: Float) {
        viewModelScope.launch { prefsManager.setWallpaperBlur(blur) }
    }

    fun setWallpaperAlpha(alpha: Float) {
        viewModelScope.launch { prefsManager.setWallpaperAlpha(alpha) }
    }

    fun setLanguageCode(code: String) {
        viewModelScope.launch { prefsManager.setLanguageCode(code) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        eqController.setEnabled(enabled)
        val s = settings.value
        val levels = _uiState.value.eqBands.map { it.levelMillibels }
        viewModelScope.launch {
            prefsManager.setEqualizerState(enabled, s.equalizerPresetIndex, levels)
        }
    }

    fun setEqualizerBand(bandIndex: Short, levelMillibels: Int) {
        eqController.setBandLevel(bandIndex, levelMillibels)
        val updatedBands = eqController.getBands()
        _uiState.update { it.copy(eqBands = updatedBands) }
        val s = settings.value
        viewModelScope.launch {
            prefsManager.setEqualizerState(
                s.equalizerEnabled,
                s.equalizerPresetIndex,
                updatedBands.map { it.levelMillibels }
            )
        }
    }

    fun selectEqualizerPreset(presetIndex: Int) {
        val updatedBands = eqController.usePreset(presetIndex.toShort())
        _uiState.update { it.copy(eqBands = updatedBands, activeEqPreset = presetIndex) }
        val s = settings.value
        viewModelScope.launch {
            prefsManager.setEqualizerState(
                s.equalizerEnabled,
                presetIndex,
                updatedBands.map { it.levelMillibels }
            )
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        eqController.release()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
