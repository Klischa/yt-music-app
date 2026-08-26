package com.klischa.ytmusic.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.klischa.ytmusic.domain.model.Playlist
import com.klischa.ytmusic.domain.model.Track
import com.klischa.ytmusic.presentation.ui.components.TrackItemRow
import com.klischa.ytmusic.presentation.ui.theme.DarkBackground
import com.klischa.ytmusic.presentation.ui.theme.DarkCard
import com.klischa.ytmusic.presentation.ui.theme.RedPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextSecondary
import com.klischa.ytmusic.presentation.viewmodel.MusicPlayerViewModel

@Composable
fun LibraryScreen(
    playerViewModel: MusicPlayerViewModel
) {
    val playlists by playerViewModel.playlists.collectAsState()
    val likedTracks by playerViewModel.likedTracks.collectAsState()
    val downloadStates by playerViewModel.downloadStates.collectAsState()

    var isCreatePlaylistDialogOpen by remember { mutableStateOf(false) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var isLikedSongsOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📚 Ваша Медиатека",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )

            Button(
                onClick = { isCreatePlaylistDialogOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Создать")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // 1. Карточка «Понравившиеся»
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isLikedSongsOpen = true }
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(RedPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "❤️ Понравившиеся",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${likedTracks.size} треков",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        if (likedTracks.isNotEmpty()) {
                            IconButton(onClick = { playerViewModel.playTrack(likedTracks.first(), likedTracks) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Слушать", tint = RedPrimary)
                            }
                        }
                    }
                }
            }

            // 2. Список пользовательских плейлистов
            items(playlists, key = { it.id }) { playlist ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedPlaylist = playlist }
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (playlist.coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = playlist.coverUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(RedPrimary.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = TextPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${playlist.trackCount} треков" + if (playlist.description.isNotEmpty()) " • ${playlist.description}" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = { playerViewModel.deletePlaylist(playlist.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = TextSecondary)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Диалог создания плейлиста
        if (isCreatePlaylistDialogOpen) {
            var newTitle by remember { mutableStateOf("") }
            var newDesc by remember { mutableStateOf("") }

            Dialog(onDismissRequest = { isCreatePlaylistDialogOpen = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Создать новый плейлист", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            placeholder = { Text("Название плейлиста") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = newDesc,
                            onValueChange = { newDesc = it },
                            placeholder = { Text("Описание (необязательно)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { isCreatePlaylistDialogOpen = false }) {
                                Text("Отмена", color = TextSecondary)
                            }
                            Button(
                                onClick = {
                                    if (newTitle.isNotBlank()) {
                                        playerViewModel.createPlaylist(newTitle, newDesc)
                                        isCreatePlaylistDialogOpen = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                            ) {
                                Text("Создать")
                            }
                        }
                    }
                }
            }
        }

        // Экран деталей понравившихся треков
        if (isLikedSongsOpen) {
            Dialog(onDismissRequest = { isLikedSongsOpen = false }) {
                Card(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("❤️ Понравившиеся (${likedTracks.size})", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                            TextButton(onClick = { isLikedSongsOpen = false }) { Text("Закрыть", color = RedPrimary) }
                        }

                        if (likedTracks.isNotEmpty()) {
                            Button(
                                onClick = {
                                    playerViewModel.playTrack(likedTracks.first(), likedTracks)
                                    isLikedSongsOpen = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Слушать всё")
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(likedTracks, key = { it.id }) { track ->
                                TrackItemRow(
                                    track = track,
                                    downloadState = downloadStates[track.id],
                                    onClick = {
                                        playerViewModel.playTrack(track, likedTracks)
                                        isLikedSongsOpen = false
                                    },
                                    onDownloadClick = { playerViewModel.downloadTrack(track) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Экран деталей плейлиста
        if (selectedPlaylist != null) {
            val pl = selectedPlaylist!!
            Dialog(onDismissRequest = { selectedPlaylist = null }) {
                Card(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBackground),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(pl.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { selectedPlaylist = null }) { Text("Закрыть", color = RedPrimary) }
                        }

                        if (pl.tracks.isNotEmpty()) {
                            Button(
                                onClick = {
                                    playerViewModel.playTrack(pl.tracks.first(), pl.tracks)
                                    selectedPlaylist = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Слушать плейлист (${pl.tracks.size})")
                            }
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(pl.tracks, key = { it.id }) { track ->
                                TrackItemRow(
                                    track = track,
                                    downloadState = downloadStates[track.id],
                                    onClick = {
                                        playerViewModel.playTrack(track, pl.tracks)
                                        selectedPlaylist = null
                                    },
                                    onDownloadClick = { playerViewModel.downloadTrack(track) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
