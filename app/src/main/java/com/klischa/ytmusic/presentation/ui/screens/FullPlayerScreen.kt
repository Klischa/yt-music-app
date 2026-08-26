package com.klischa.ytmusic.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbDownOffAlt
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ThumbUpOffAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.klischa.ytmusic.data.lyrics.LyricsService
import com.klischa.ytmusic.domain.model.LikeStatus
import com.klischa.ytmusic.domain.model.Playlist
import com.klischa.ytmusic.domain.model.Track
import com.klischa.ytmusic.presentation.ui.theme.AccentGreen
import com.klischa.ytmusic.presentation.ui.theme.DarkBackground
import com.klischa.ytmusic.presentation.ui.theme.DarkCard
import com.klischa.ytmusic.presentation.ui.theme.RedPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextSecondary

@Composable
fun FullPlayerScreen(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    likeStatus: LikeStatus,
    lyricsResult: LyricsService.LyricsResult?,
    isLyricsLoading: Boolean,
    queue: List<Track>,
    playlists: List<Playlist>,
    onLikeClick: (Track) -> Unit,
    onDislikeClick: (Track) -> Unit,
    onAddToPlaylist: (playlistId: String, Track) -> Unit,
    onTrackSelect: (Track) -> Unit,
    onCloseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    if (track == null) return

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Track, 1: Lyrics, 2: Queue
    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPosition by remember { mutableFloatStateOf(0f) }
    var isAddToPlaylistDialogOpen by remember { mutableStateOf(false) }

    val currentSliderValue = if (isUserSeeking) {
        userSeekPosition
    } else {
        progressMs.toFloat()
    }

    val maxSliderValue = durationMs.toFloat().coerceAtLeast(1f)
    val lyricsListState = rememberLazyListState()

    if (lyricsResult?.isSynced == true) {
        val activeLineIndex = lyricsResult.lines.indexOfLast { progressMs >= it.timestampMs }
        if (activeLineIndex >= 0) {
            LaunchedEffect(activeLineIndex) {
                lyricsListState.animateScrollToItem((activeLineIndex - 2).coerceAtLeast(0))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Свернуть",
                    tint = TextPrimary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Сейчас играет",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )

            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Скачать",
                    tint = TextPrimary
                )
            }
        }

        // Переключатель вкладок
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkBackground,
            contentColor = TextPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = RedPrimary
                )
            },
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Трек") },
                icon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Текст") },
                icon = { Icon(Icons.Default.Lyrics, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Очередь (${queue.size})") },
                icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedTab) {
            0 -> {
                // Вкладка 1: Обложка и плеер
                AsyncImage(
                    model = track.thumbnailUrl.ifEmpty { "https://music.youtube.com/img/on_platform_logo_dark.svg" },
                    contentDescription = track.title,
                    modifier = Modifier
                        .fillMaxWidth(0.80f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track.artist + if (track.album.isNotEmpty()) " • ${track.album}" else "",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextSecondary
                )

                // Панель действий: Лайк, Дизлайк, Добавить в плейлист
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onLikeClick(track) }) {
                        Icon(
                            imageVector = if (likeStatus == LikeStatus.LIKED) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                            contentDescription = "Лайк",
                            tint = if (likeStatus == LikeStatus.LIKED) RedPrimary else TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = { onDislikeClick(track) }) {
                        Icon(
                            imageVector = if (likeStatus == LikeStatus.DISLIKED) Icons.Default.ThumbDown else Icons.Default.ThumbDownOffAlt,
                            contentDescription = "Дизлайк",
                            tint = if (likeStatus == LikeStatus.DISLIKED) RedPrimary else TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    IconButton(onClick = { isAddToPlaylistDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "В плейлист",
                            tint = TextSecondary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            1 -> {
                // Вкладка 2: Караоке-текст
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .padding(16.dp)
                ) {
                    if (isLyricsLoading) {
                        CircularProgressIndicator(
                            color = RedPrimary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else if (lyricsResult == null || lyricsResult.lines.isEmpty()) {
                        Text(
                            text = "Текст песни пока не найден для этого трека.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            state = lyricsListState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(lyricsResult.lines) { line ->
                                val isCurrentLine = lyricsResult.isSynced && (progressMs >= line.timestampMs &&
                                        (lyricsResult.lines.getOrNull(lyricsResult.lines.indexOf(line) + 1)?.timestampMs ?: Long.MAX_VALUE) > progressMs)

                                Text(
                                    text = line.text,
                                    style = if (isCurrentLine)
                                        MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    else
                                        MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                                    color = if (isCurrentLine) AccentGreen else TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clickable {
                                            if (lyricsResult.isSynced && line.timestampMs > 0) {
                                                onSeek(line.timestampMs)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            2 -> {
                // Вкладка 3: Очередь треков
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkCard)
                        .padding(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(queue) { queueTrack ->
                            val isCurrent = queueTrack.id == track.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) RedPrimary.copy(alpha = 0.2f) else DarkCard)
                                    .clickable { onTrackSelect(queueTrack) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = queueTrack.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = queueTrack.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = if (isCurrent) RedPrimary else TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = queueTrack.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Ползунок перемотки (Slider)
        Slider(
            value = currentSliderValue.coerceIn(0f, maxSliderValue),
            onValueChange = {
                isUserSeeking = true
                userSeekPosition = it
            },
            onValueChangeFinished = {
                isUserSeeking = false
                onSeek(userSeekPosition.toLong())
            },
            valueRange = 0f..maxSliderValue,
            colors = SliderDefaults.colors(
                thumbColor = RedPrimary,
                activeTrackColor = RedPrimary,
                inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
            )
        )

        // Метки времени
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val curSec = (currentSliderValue / 1000).toLong()
            val durSec = (durationMs / 1000)
            Text(
                text = String.format("%02d:%02d", curSec / 60, curSec % 60),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = String.format("%02d:%02d", durSec / 60, durSec % 60),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Кнопки управления (Предыдущий, Играть/Пауза, Следующий)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousClick,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Предыдущий",
                    tint = TextPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(76.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    tint = RedPrimary,
                    modifier = Modifier.size(68.dp)
                )
            }

            IconButton(
                onClick = onNextClick,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Следующий",
                    tint = TextPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Диалог добавления в плейлист
        if (isAddToPlaylistDialogOpen) {
            Dialog(onDismissRequest = { isAddToPlaylistDialogOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Добавить в плейлист", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(12.dp))

                        if (playlists.isEmpty()) {
                            Text("Плейлистов пока нет. Создайте плейлист во вкладке Медиатека.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        } else {
                            LazyColumn {
                                items(playlists) { pl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onAddToPlaylist(pl.id, track)
                                                isAddToPlaylistDialogOpen = false
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = RedPrimary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(pl.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { isAddToPlaylistDialogOpen = false }) {
                                Text("Закрыть", color = RedPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
