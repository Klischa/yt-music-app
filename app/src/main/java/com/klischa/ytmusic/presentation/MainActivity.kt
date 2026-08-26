package com.klischa.ytmusic.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klischa.ytmusic.presentation.ui.components.MiniPlayerBar
import com.klischa.ytmusic.presentation.ui.screens.DownloadsScreen
import com.klischa.ytmusic.presentation.ui.screens.FullPlayerScreen
import com.klischa.ytmusic.presentation.ui.screens.LibraryScreen
import com.klischa.ytmusic.presentation.ui.screens.SearchScreen
import com.klischa.ytmusic.presentation.ui.theme.DarkCard
import com.klischa.ytmusic.presentation.ui.theme.DarkSurface
import com.klischa.ytmusic.presentation.ui.theme.RedPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextSecondary
import com.klischa.ytmusic.presentation.ui.theme.YTMusicTheme
import com.klischa.ytmusic.presentation.util.PermissionsHelper
import com.klischa.ytmusic.presentation.viewmodel.LibraryViewModel
import com.klischa.ytmusic.presentation.viewmodel.MusicPlayerViewModel
import com.klischa.ytmusic.presentation.viewmodel.SearchViewModel

class MainActivity : ComponentActivity() {

    private val playerViewModel: MusicPlayerViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        libraryViewModel.loadDownloads()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionsHelper.hasPermissions(this)) {
            permissionLauncher.launch(PermissionsHelper.getRequiredPermissions())
        }

        setContent {
            YTMusicTheme {
                MainContent(
                    searchViewModel = searchViewModel,
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel
                )
            }
        }
    }
}

@Composable
fun MainContent(
    searchViewModel: SearchViewModel,
    playerViewModel: MusicPlayerViewModel,
    libraryViewModel: LibraryViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Search, 1: Library, 2: Downloads

    val currentTrack by playerViewModel.currentTrack.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val progressMs by playerViewModel.currentPositionMs.collectAsState()
    val durationMs by playerViewModel.durationMs.collectAsState()
    val isFullPlayerOpen by playerViewModel.isFullPlayerExpanded.collectAsState()
    val lyricsResult by playerViewModel.lyrics.collectAsState()
    val isLyricsLoading by playerViewModel.isLyricsLoading.collectAsState()
    val queue by playerViewModel.queue.collectAsState()
    val likeStatus by playerViewModel.currentTrackLikeStatus.collectAsState()
    val playlists by playerViewModel.playlists.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Поиск") },
                    label = { Text("Поиск") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RedPrimary,
                        selectedTextColor = RedPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = DarkCard
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                    },
                    icon = { Icon(Icons.Default.LibraryMusic, contentDescription = "Медиатека") },
                    label = { Text("Медиатека") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RedPrimary,
                        selectedTextColor = RedPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = DarkCard
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        libraryViewModel.loadDownloads()
                    },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Загрузки") },
                    label = { Text("Загрузки") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RedPrimary,
                        selectedTextColor = RedPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary,
                        indicatorColor = DarkCard
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Прикрепленный фоновый HTML5 YouTube Audio Engine (гарантирует воспроизведение звука)
            AndroidView(
                factory = { ctx ->
                    playerViewModel.createAndAttachAudioPlayer(ctx)
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0.01f)
            )

            when (selectedTab) {
                0 -> SearchScreen(searchViewModel = searchViewModel, playerViewModel = playerViewModel)
                1 -> LibraryScreen(playerViewModel = playerViewModel)
                2 -> DownloadsScreen(libraryViewModel = libraryViewModel, playerViewModel = playerViewModel)
            }

            // MiniPlayerBar прикрепленный снизу
            if (currentTrack != null) {
                Box(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    MiniPlayerBar(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        progressMs = progressMs,
                        durationMs = durationMs,
                        onBarClick = { playerViewModel.setFullPlayerExpanded(true) },
                        onPlayPauseClick = { playerViewModel.togglePlayPause() },
                        onNextClick = { playerViewModel.skipNext() }
                    )
                }
            }
        }

        // Полноэкранный плеер с караоке-текстом, очередью, лайками и плейлистами
        if (isFullPlayerOpen && currentTrack != null) {
            Dialog(
                onDismissRequest = { playerViewModel.setFullPlayerExpanded(false) },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                FullPlayerScreen(
                    track = currentTrack,
                    isPlaying = isPlaying,
                    progressMs = progressMs,
                    durationMs = durationMs,
                    likeStatus = likeStatus,
                    lyricsResult = lyricsResult,
                    isLyricsLoading = isLyricsLoading,
                    queue = queue,
                    playlists = playlists,
                    onLikeClick = { playerViewModel.toggleLike(it) },
                    onDislikeClick = { playerViewModel.toggleDislike(it) },
                    onAddToPlaylist = { playlistId, trackToAdd ->
                        playerViewModel.addTrackToPlaylist(playlistId, trackToAdd)
                    },
                    onTrackSelect = { selectedTrack ->
                        playerViewModel.playTrack(selectedTrack, queue)
                    },
                    onCloseClick = { playerViewModel.setFullPlayerExpanded(false) },
                    onPlayPauseClick = { playerViewModel.togglePlayPause() },
                    onSeek = { playerViewModel.seekTo(it) },
                    onNextClick = { playerViewModel.skipNext() },
                    onPreviousClick = { playerViewModel.skipPrevious() },
                    onDownloadClick = { playerViewModel.downloadTrack(currentTrack!!) }
                )
            }
        }
    }
}
