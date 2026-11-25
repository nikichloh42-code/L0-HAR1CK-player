package com.custom.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.custom.player.R
import com.custom.player.data.TrackEntity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NowPlayingScreen(
    uiState: PlayerUiState,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleLike: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = uiState.currentTrack ?: return
    val duration = uiState.durationMs.coerceAtLeast(1L)
    val position = uiState.positionMs.coerceIn(0L, duration)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
            }
            Text(
                text = track.displayAlbum,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            IconButton(onClick = onOpenEqualizer) {
                Icon(Icons.Default.Equalizer, contentDescription = stringResource(R.string.equalizer))
            }
        }

        // Максимально крупная обложка: пару мм (8dp) от краёв и лёгкое скругление (11dp)
        AsyncImage(
            model = track.displayArtworkUri,
            contentDescription = track.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(11.dp))
        )

        // Название, артист и лайк
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.displayTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.displayArtist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { onToggleLike(track.id) }) {
                    Icon(
                        imageVector = if (track.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (track.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Slider(
                value = position.toFloat(),
                onValueChange = { onSeekTo(it.toLong()) },
                valueRange = 0f..duration.toFloat()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatDurationShort(position), style = MaterialTheme.typography.bodySmall)
                Text(formatDurationShort(duration), style = MaterialTheme.typography.bodySmall)
            }
        }

        // Кнопки управления воспроизведением и переход к очереди
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onSkipPrevious, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(34.dp))
            }

            FilledIconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(68.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp)
                )
            }

            IconButton(onClick = onSkipNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(34.dp))
            }

            IconButton(onClick = onOpenQueue) {
                Icon(Icons.Default.QueueMusic, contentDescription = stringResource(R.string.queue_title))
            }
        }
    }
}

@Composable
fun QueueScreen(
    uiState: PlayerUiState,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onAddTrackToQueue: (TrackEntity) -> Unit,
    onSetRepeatMode: (QueueRepeatMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showAddPicker by remember { mutableStateOf(false) }

    val queue = uiState.queueTracks
    val currentId = uiState.currentTrackId
    val duration = uiState.durationMs.coerceAtLeast(1L)
    val progress = (uiState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    val containerBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
    val cardBg = Color.Black.copy(alpha = 0.22f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(containerBg)
    ) {
        // Верхний мини-плеер текущего трека
        uiState.currentTrack?.let { current ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = current.displayArtworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current.displayTitle,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = current.displayArtist,
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onTogglePlayPause) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f)
                )
            }
        }

        // Стрелка вниз для закрытия шторки очереди
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f)
            )
        }

        // Заголовок «Очередь» и кнопки справа (фокус на текущем треке + добавить в очередь)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.queue_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Row {
                IconButton(
                    onClick = {
                        val idx = queue.indexOfFirst { it.id == currentId }
                        if (idx >= 0) {
                            coroutineScope.launch { listState.animateScrollToItem(idx) }
                        }
                    }
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = Color.White)
                }
                IconButton(onClick = { showAddPicker = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_queue), tint = Color.White)
                }
            }
        }

        // Список треков очереди с перетаскиванием за ручку (=)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                val isActive = item.id == currentId
                var dragOffsetY by remember(item.id, index) { mutableFloatStateOf(0f) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) Color.White.copy(alpha = 0.24f) else cardBg)
                        .clickable { onPlayQueueIndex(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.displayArtworkUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.displayTitle,
                            color = Color.White,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.displayArtist} - ${item.displayAlbum}",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(26.dp)
                            .pointerInput(index, queue.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragEnd = {
                                        val rowHeightPx = 64f
                                        val deltaSlots = (dragOffsetY / rowHeightPx).roundToInt()
                                        val targetIndex = (index + deltaSlots).coerceIn(0, (queue.size - 1).coerceAtLeast(0))
                                        if (targetIndex != index) {
                                            onMoveItem(index, targetIndex)
                                        }
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = { dragOffsetY = 0f },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                    }
                                )
                            }
                    )
                }
            }
        }

        // Нижняя панель из 3 режимов: Повтор одного / По очереди / Вперемешку
        Surface(
            color = Color.Black.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModeButton(
                    icon = Icons.Default.RepeatOne,
                    label = stringResource(R.string.repeat_one),
                    selected = uiState.repeatMode == QueueRepeatMode.REPEAT_ONE,
                    onClick = { onSetRepeatMode(QueueRepeatMode.REPEAT_ONE) }
                )
                ModeButton(
                    icon = Icons.Default.Repeat,
                    label = stringResource(R.string.repeat_all),
                    selected = uiState.repeatMode == QueueRepeatMode.REPEAT_ALL,
                    onClick = { onSetRepeatMode(QueueRepeatMode.REPEAT_ALL) }
                )
                ModeButton(
                    icon = Icons.Default.Shuffle,
                    label = stringResource(R.string.shuffle_mode),
                    selected = uiState.repeatMode == QueueRepeatMode.SHUFFLE,
                    onClick = { onSetRepeatMode(QueueRepeatMode.SHUFFLE) }
                )
            }
        }
    }

    if (showAddPicker) {
        AlertDialog(
            onDismissRequest = { showAddPicker = false },
            title = { Text(stringResource(R.string.add_to_queue)) },
            text = {
                LazyColumn(modifier = Modifi
