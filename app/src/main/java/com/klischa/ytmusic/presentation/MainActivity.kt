package com.klischa.ytmusic.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.klischa.ytmusic.data.auth.UserAccountManager
import com.klischa.ytmusic.presentation.ui.components.MiniPlayerBar
import com.klischa.ytmusic.presentation.ui.screens.DownloadsScreen
import com.klischa.ytmusic.presentation.ui.screens.FullPlayerScreen
import com.klischa.ytmusic.presentation.ui.screens.LibraryScreen
import com.klischa.ytmusic.presentation.ui.screens.LoginScreen
import com.klischa.ytmusic.presentation.ui.screens.SearchScreen
import com.klischa.ytmusic.presentation.ui.theme.AccentGreen
import com.klischa.ytmusic.presentation.ui.theme.DarkCard
import com.klischa.ytmusic.presentation.ui.theme.DarkSurface
import com.klischa.ytmusic.presentation.ui.theme.RedPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextPrimary
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    searchViewModel: SearchViewModel,
    playerViewModel: MusicPlayerViewModel,
    libraryViewModel: LibraryViewModel
) {
    val context = LocalContext.current
    val accountManager = remember { UserAccountManager.getInstance(context) }
    val isLoggedIn by accountManager.isLoggedIn.collectAsState()
    val userName by accountManager.userAccountName.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Search, 1: Library, 2: Downloads
    var isAccountDialogOpen by remember { mutableStateOf(false) }
    var isLoginScreenOpen by remember { mutableStateOf(false) }

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🎵 My YT Music",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                },
                actions = {
                    IconButton(onClick = { isAccountDialogOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Аккаунт",
                            tint = if (isLoggedIn) AccentGreen else TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
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

        // Диалог управления аккаунтом Google / YouTube
        if (isAccountDialogOpen) {
            Dialog(onDismissRequest = { isAccountDialogOpen = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = if (isLoggedIn) AccentGreen else RedPrimary,
                            modifier = Modifier.size(56.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (isLoggedIn) (userName ?: "Google / YouTube Music Аккаунт") else "Вход в YouTube Music",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (isLoggedIn)
                                "Сессия активна. Доступ к аудиопотокам и вашим плейлистам открыт."
                            else
                                "Авторизация позволяет открывать ваши плейлисты и снимает любые блокировки воспроизведения.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isLoggedIn) {
                            OutlinedButton(
                                onClick = {
                                    accountManager.logout()
                                    isAccountDialogOpen = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Выйти из аккаунта", color = RedPrimary)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isAccountDialogOpen = false
                                    isLoginScreenOpen = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Войти через Google / YouTube")
                            }
                        }
                    }
                }
            }
        }

        // Полноэкранный вход через Google (Web Login)
        if (isLoginScreenOpen) {
            Dialog(
                onDismissRequest = { isLoginScreenOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                LoginScreen(
                    onDismiss = { isLoginScreenOpen = false },
                    onLoginSuccess = {
                        isLoginScreenOpen = false
                        searchViewModel.loadTrending()
                    }
                )
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
