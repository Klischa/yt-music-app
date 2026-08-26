package com.klischa.ytmusic.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.klischa.ytmusic.domain.model.DownloadState
import com.klischa.ytmusic.presentation.ui.components.TrackItemRow
import com.klischa.ytmusic.presentation.ui.theme.DarkBackground
import com.klischa.ytmusic.presentation.ui.theme.TextSecondary
import com.klischa.ytmusic.presentation.viewmodel.LibraryViewModel
import com.klischa.ytmusic.presentation.viewmodel.MusicPlayerViewModel

@Composable
fun DownloadsScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: MusicPlayerViewModel
) {
    val downloadedTracks by libraryViewModel.downloadedTracks.collectAsState()
    val downloadStates by playerViewModel.downloadStates.collectAsState()

    LaunchedEffect(Unit) {
        libraryViewModel.loadDownloads()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Text(
            text = "📂 Загруженные треки (Music/MyYTMusic)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        if (downloadedTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет загруженных треков.\nНайдите песню во вкладке Поиск и нажмите кнопку скачивания!",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = TextSecondary,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(downloadedTracks, key = { it.id }) { track ->
                    TrackItemRow(
                        track = track,
                        downloadState = DownloadState.Completed(track.localUri!!, track.localUri.toString()),
                        onClick = {
                            playerViewModel.playTrack(track, downloadedTracks)
                        },
                        onDownloadClick = {}
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
