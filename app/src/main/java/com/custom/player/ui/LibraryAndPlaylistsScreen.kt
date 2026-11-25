package com.custom.player.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.custom.player.R
import com.custom.player.data.SortOption
import com.custom.player.data.TrackEntity

@Composable
fun LibraryScreen(
    uiState: PlayerUiState,
    currentSort: SortOption,
    onSearchQueryChange: (String) -> Unit,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    onSortChange: (SortOption) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onToggleLike: (Long) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onSaveTrackEdit: (TrackEntity, String, String, String, Uri?) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    val query = uiState.searchQuery.trim().lowercase()

    val baseFilteredTracks = remember(
        uiState.tracks,
        uiState.selectedAutoPlaylist,
        uiState.selectedAlbumFilter,
        uiState.selectedArtistFilter
    ) {
        var list = uiState.tracks
        uiState.selectedAlbumFilter?.let { album ->
            list = list.filter { it.displayAlbum.equals(album, ignoreCase = true) }
        }
        uiState.selectedArtistFilter?.let { artist ->
            list = list.filter { it.displayArtist.equals(artist, ignoreCase = true) }
        }
        when (uiState.selectedAutoPlaylist) {
            AutoPlaylistType.RECENT -> list.filter { it.lastPlayedAtMs > 0L }.sortedByDescending { it.lastPlayedAtMs }
            AutoPlaylistType.LIKED -> list.filter { it.isLiked }
            AutoPlaylistType.MOST_PLAYED -> list.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
            null -> list
        }
    }

    val matchedTracks = remember(baseFilteredTracks, query) {
        if (query.isEmpty()) baseFilteredTracks
        else baseFilteredTracks.filter {
            it.displayTitle.lowercase().contains(query) ||
                it.displayArtist.lowercase().contains(query) ||
                it.displayAlbum.lowercase().contains(query)
        }
    }

    val matchedArtists = remember(uiState.tracks, query) {
        val artists = uiState.tracks.map { it.displayArtist }.distinct().sorted()
        if (query.isEmpty()) artists else artists.filter { it.lowercase().contains(query) }
    }

    val matchedAlbums = remember(uiState.tracks, query) {
        val albums = uiState.tracks
            .groupBy { it.displayAlbum to it.displayArtist }
            .map { (pair, group) -> Triple(pair.first, pair.second, group.firstOrNull()?.displayArtworkUri) }
            .sortedBy { it.first.lowercase() }
        if (query.isEmpty()) albums else albums.filter {
            it.first.lowercase().contains(query) || it.second.lowercase().contains(query)
        }
    }

    val autoPlaylists = listOf(
        AutoPlaylistType.RECENT to stringResource(R.string.playlist_recent),
        AutoPlaylistType.LIKED to stringResource(R.string.playlist_liked),
        AutoPlaylistType.MOST_PLAYED to stringResource(R.string.playlist_most_played)
    ).filter { (_, label) -> query.isEmpty() || label.lowercase().contains(query) }

    Column(modifier = modifier.fillMaxSize()) {
        // Верхний поиск
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )

        // Вкладки категорий поиска
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf(
                SearchCategory.ALL to stringResource(R.string.filter_all),
                SearchCategory.TRACKS to stringResource(R.string.filter_tracks),
                SearchCategory.ARTISTS to stringResource(R.string.filter_artists),
                SearchCategory.PLAYLISTS to stringResource(R.string.filter_playlists),
                SearchCategory.ALBUMS to stringResource(R.string.filter_albums)
            )
            categories.forEach { (cat, label) ->
                FilterChip(
                    selected = uiState.searchCategory == cat,
                    onClick = { onSearchCategoryChange(cat) },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }

        // Строка сортировки и активных фильтров
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val activeFilterLabel = uiState.selectedAlbumFilter
                ?: uiState.selectedArtistFilter
                ?: uiState.selectedAutoPlaylist?.let { type ->
                    when (type) {
                        AutoPlaylistType.RECENT -> stringResource(R.string.playlist_recent)
                        AutoPlaylistType.LIKED -> stringResource(R.string.playlist_liked)
                        AutoPlaylistType.MOST_PLAYED -> stringResource(R.string.playlist_most_played)
                    }
                }

            if (activeFilterLabel != null) {
                FilterChip(
                    selected = true,
                    onClick = onClearFilters,
                    label = { Text(activeFilterLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            } else {
                Text(
                    text = "${matchedTracks.size} ${stringResource(R.string.tab_library).lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            BoxWithSortMenu(
                expanded = showSortMenu,
                onToggle = { showSortMenu = !showSortMenu },
                onDismiss = { showSortMenu = false },
                currentSort = currentSort,
                onSelectSort = {
                    onSortChange(it)
                    showSortMenu = false
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            val cat = uiState.searchCategory

            if (cat == SearchCategory.ALL || cat == SearchCategory.PLAYLISTS) {
                if (autoPlaylists.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.filter_playlists))
                    }
                    items(autoPlaylists) { (type, title) ->
                        val icon = when (type) {
                            AutoPlaylistType.RECENT -> Icons.Default.History
                            AutoPlaylistType.LIKED -> Icons.Default.Favorite
                            AutoPlaylistType.MOST_PLAYED -> Icons.Default.Whatshot
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onClearFilters()
                                    onSearchCategoryChange(SearchCategory.TRACKS)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (cat == SearchCategory.ALL || cat == SearchCategory.ARTISTS) {
                if (matchedArtists.isNotEmpty() && query.isNotEmpty() || cat == SearchCategory.ARTISTS) {
                    item {
                        SectionHeader(stringResource(R.string.filter_artists))
                    }
                    items(matchedArtists.take(if (cat == SearchCategory.ALL) 5 else 100)) { artist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onClearFilters()
                                    onSearchCategoryChange(SearchCategory.TRACKS)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(artist, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            if (cat == SearchCategory.ALL || cat == SearchCategory.ALBUMS) {
                if (matchedAlbums.isNotEmpty() && query.isNotEmpty() || cat == SearchCategory.ALBUMS) {
                    item {
                        SectionHeader(stringResource(R.string.filter_albums))
                    }
                    items(matchedAlbums.take(if (cat == SearchCategory.ALL) 5 else 100)) { (album, artist, art) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onClearFilters()
                                    onSearchCategoryChange(SearchCategory.TRACKS)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = art,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(album, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.Default.Album, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (cat == SearchCategory.ALL || cat == SearchCategory.TRACKS) {
                item {
                    SectionHeader(stringResource(R.string.filter_tracks))
                }
                items(matchedTracks, key = { it.id }) { track ->
                    TrackRowItem(
                        track = track,
                        isCurrent = track.id == uiState.currentTrackId,
                        onPlay = { onPlayTrack(track, matchedTracks) },
                        onToggleLike = { onToggleLike(track.id) },
                        onAddToQueue = { onAddToQueue(track) },
                        onEdit = { editingTrack = track }
                    )
                }
            }
        }
    }

    editingTrack?.let { target ->
        EditTrackDialog(
            track = target,
            onDismiss = { editingTrack = null },
            onSave = { title, artist, album, coverUri ->
                onSaveTrackEdit(target, title, artist, album, coverUri)
                editingTrack = null
            }
        )
    }
}

@Composable
fun PlaylistsOverviewScreen(
    uiState: PlayerUiState,
    onSelectAutoPlaylist: (AutoPlaylistType) -> Unit,
    onSelectAlbum: (String) -> Unit,
    onSelectArtist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val albums = remember(uiState.tracks) {
        uiState.tracks
            .groupBy { it.displayAlbum }
            .map { (album, list) -> Triple(album, list.firstOrNull()?.displayArtist ?: "", list.firstOrNull()?.displayArtworkUri) }
            .sortedBy { it.first.lowercase() }
    }
    val artists = remember(uiState.tracks) {
        uiState.tracks.map { it.displayArtist }.distinct().sortedBy { it.lowercase() }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionHeader(stringResource(R.string.filter_playlists))
        }
        val autoItems = listOf(
            Triple(AutoPlaylistType.RECENT, stringResource(R.string.playlist_recent), Icons.Default.History),
            Triple(AutoPlaylistType.LIKED, stringResource(R.string.playlist_liked), Icons.Default.Favorite),
            Triple(AutoPlaylistType.MOST_PLAYED, stringResource(R.string.playlist_most_played), Icons.Default.Whatshot)
        )
        items(autoItems) { (type, title, icon) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAutoPlaylist(type) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item { SectionHeader(stringResource(R.string.filter_albums)) }
        items(albums) { (album, artist, art) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectAlbum(album) }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(album, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.filter_artists)) }
        items(artists) { artist ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectArtist(artist) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
