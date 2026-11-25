package com.custom.player.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.custom.player.R
import com.custom.player.data.AlbumStat
import com.custom.player.data.AppThemeMode
import com.custom.player.data.ArtistStat
import com.custom.player.data.PlayerSettings
import com.custom.player.player.EqBandUi
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: PlayerViewModel = viewModel()
            val settings by vm.settings.collectAsState()
            val uiState by vm.uiState.collectAsState()
            val totalPlayTimeMs by vm.totalPlayTimeMs.collectAsState()
            val top5Albums by vm.top5Albums.collectAsState()
            val top5Artists by vm.top5Artists.collectAsState()

            ApplyLocale(languageCode = settings.languageCode)

            val context = LocalContext.current
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) vm.scanLibrary()
            }

            LaunchedEffect(Unit) {
                val hasPerm = ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    vm.scanLibrary()
                } else {
                    permissionLauncher.launch(permission)
                }
            }

            PlayerThemeWrapper(
                settings = settings,
                currentArtworkUri = uiState.currentTrack?.displayArtworkUri
            ) {
                MainPlayerScaffold(
                    vm = vm,
                    settings = settings,
                    uiState = uiState,
                    totalPlayTimeMs = totalPlayTimeMs,
                    top5Albums = top5Albums,
                    top5Artists = top5Artists,
                    onRescanClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(context, permission) ==
                            PackageManager.PERMISSION_GRANTED
                        if (hasPerm) vm.scanLibrary() else permissionLauncher.launch(permission)
                    }
                )
            }
        }
    }
}

@Composable
private fun ApplyLocale(languageCode: String) {
    val context = LocalContext.current
    LaunchedEffect(languageCode) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        if (config.locale.language != languageCode) {
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            (context as? Activity)?.recreate()
        }
    }
}

private enum class MainTab {
    LIBRARY, PLAYLISTS, STATS, SETTINGS
}

@Composable
private fun MainPlayerScaffold(
    vm: PlayerViewModel,
    settings: PlayerSettings,
    uiState: PlayerUiState,
    totalPlayTimeMs: Long,
    top5Albums: List<AlbumStat>,
    top5Artists: List<ArtistStat>,
    onRescanClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.LIBRARY) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            Column {
                uiState.currentTrack?.let { current ->
                    MiniPlayerBar(
                        title = current.displayTitle,
                        artist = current.displayArtist,
                        artworkUri = current.displayArtworkUri,
                        isPlaying = uiState.isPlaying,
                        isLiked = current.isLiked,
                        onTogglePlayPause = vm::togglePlayPause,
                        onToggleLike = { vm.toggleLike(current.id) },
                        onOpenQueue = { showQueue = true },
                        onOpenFullPlayer = { showNowPlaying = true }
                    )
                }

                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == MainTab.LIBRARY,
                        onClick = { selectedTab = MainTab.LIBRARY },
                        icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_library), maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.PLAYLISTS,
                        onClick = { selectedTab = MainTab.PLAYLISTS },
                        icon = { Icon(Icons.Default.Album, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_playlists), maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.STATS,
                        onClick = { selectedTab = MainTab.STATS },
                        icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_stats), maxLines = 1) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == MainTab.SETTINGS,
                        onClick = { selectedTab = MainTab.SETTINGS },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings), maxLines = 1) }
                    )
                }
            }
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (selectedTab) {
            MainTab.LIBRARY -> LibraryScreen(
                uiState = uiState,
                currentSort = settings.sortOption,
                onSearchQueryChange = vm::updateSearchQuery,
                onSearchCategoryChange = vm::updateSearchCategory,
                onSortChange = vm::setSortOption,
                onPlayTrack = { track, list ->
                    vm.playTrackFromList(track, list)
                },
                onToggleLike = vm::toggleLike,
                onAddToQueue = { vm.addTrackToQueue(it, playNext = false) },
                onSaveTrackEdit = vm::updateTrackMetadata,
                onClearFilters = {
                    vm.selectAutoPlaylist(null)
                    vm.selectAlbumFilter(null)
                    vm.selectArtistFilter(null)
                },
                modifier = contentModifier
            )

            MainTab.PLAYLISTS -> PlaylistsOverviewScreen(
                uiState = uiState,
                onSelectAutoPlaylist = {
                    vm.selectAutoPlaylist(it)
                    selectedTab = MainTab.LIBRARY
                },
                onSelectAlbum = {
                    vm.selectAlbumFilter(it)
                    selectedTab = MainTab.LIBRARY
                },
                onSelectArtist = {
                    vm.selectArtistFilter(it)
                    selectedTab = MainTab.LIBRARY
                },
                modifier = contentModifier
            )

            MainTab.STATS -> StatsScreen(
                totalPlayTimeMs = totalPlayTimeMs,
                top5Albums = top5Albums,
                top5Artists = top5Artists,
                modifier = contentModifier
            )

            MainTab.SETTINGS -> SettingsAndEqualizerScreen(
                settings = settings,
                eqBands = uiState.eqBands,
                eqPresets = uiState.eqPresets,
                activePreset = uiState.activeEqPreset,
                onThemeChange = vm::setThemeMode,
                onScaleChange = vm::setUiScale,
                onWallpaperPicked = vm::setWallpaper,
                onClearWallpaper = { vm.setWallpaper(null) },
                onWallpaperDimChange = vm::setWallpaperDim,
                onWallpaperBlurChange = vm::setWallpaperBlur,
                onWallpaperAlphaChange = vm::setWallpaperAlpha,
                onLanguageChange = vm::setLanguageCode,
                onEqToggle = vm::setEqualizerEnabled,
                onEqBandChange = vm::setEqualizerBand,
                onEqPresetSelect = vm::selectEqualizerPreset,
                onRescanClick = onRescanClick,
                modifier = contentModifier
            )
        }
    }

    if (showNowPlaying && uiState.currentTrack != null) {
        NowPlayingScreen(
            uiState = uiState,
            onClose = { showNowPlaying = false },
            onTogglePlayPause = vm::togglePlayPause,
            onSeekTo = vm::seekTo,
            onSkipNext = vm::skipNext,
            onSkipPrevious = vm::skipPrevious,
            onToggleLike = vm::toggleLike,
            onOpenQueue = { showQueue = true },
            onOpenEqualizer = { showEqualizer = true }
        )
    }

    if (showQueue) {
        QueueScreen(
            uiState = uiState,
            onClose = { showQueue = false },
            onTogglePlayPause = vm::togglePlayPause,
            onPlayQueueIndex = vm::playFromQueueIndex,
            onMoveItem = vm::moveQueueItem,
            onAddTrackToQueue = { vm.addTrackToQueue(it, playNext = false) },
            onSetRepeatMode = vm::setRepeatMode
        )
    }

    if (showEqualizer) {
        EqualizerDialogScreen(
            enabled = settings.equalizerEnabled,
            bands = uiState.eqBands,
            presets = uiState.eqPresets,
            activePreset = uiState.activeEqPreset,
            onToggle = vm::setEqualizerEnabled,
            onBandChange = vm::setEqualizerBand,
            onPresetSelect = vm::selectEqualizerPreset,
            onClose = { showEqualizer = false }
        )
    }
}

@Composable
private fun MiniPlayerBar(
    title: String,
    artist: String,
    artworkUri: String?,
    isPlaying: Boolean,
    isLiked: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenFullPlayer: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpenFullPlayer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onToggleLike) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Default.QueueMusic, contentDescription = null)
            }
        }
    }
}

@Composable
private fun StatsScreen(
    totalPlayTimeMs: Long,
    top5Albums: List<AlbumStat>,
    top5Artists: List<ArtistStat>,
    modifier: Modifier = Modifier
) {
    val hLabel = stringResource(R.string.hours_short)
    val mLabel = stringResource(R.string.minutes_short)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 12.dp, 14.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.stats_total_time),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = formatHoursMinutes(totalPlayTimeMs, hLabel, mLabel),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.stats_top_albums),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        itemsIndexed(top5Albums) { index, album ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                    AsyncImage(
                        model = album.artworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(album.albumName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(album.artistName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = formatHoursMinutes(album.totalTimeMs, hLabel, mLabel),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.stats_top_artists),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        itemsIndexed(top5Artists) { index, artist ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    
                    
         
