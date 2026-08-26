package com.klischa.ytmusic.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.klischa.ytmusic.domain.model.Track
import com.klischa.ytmusic.presentation.ui.components.TrackItemRow
import com.klischa.ytmusic.presentation.ui.theme.DarkBackground
import com.klischa.ytmusic.presentation.ui.theme.DarkCard
import com.klischa.ytmusic.presentation.ui.theme.RedPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextSecondary
import com.klischa.ytmusic.presentation.viewmodel.MusicPlayerViewModel
import com.klischa.ytmusic.presentation.viewmodel.SearchViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel,
    playerViewModel: MusicPlayerViewModel
) {
    val query by searchViewModel.searchQuery.collectAsState()
    val results by searchViewModel.searchResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()
    val errorMessage by searchViewModel.errorMessage.collectAsState()
    val downloadStates by playerViewModel.downloadStates.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    val popularSuggestions = listOf("Queen", "Miyagi", "Linkin Park", "Rauf & Faik", "The Weeknd", "Кино", "Imagine Dragons", "Eminem")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Поисковая строка
        OutlinedTextField(
            value = query,
            onValueChange = { searchViewModel.onQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Поиск треков, исполнителей...") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Поиск",
                    tint = TextSecondary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { searchViewModel.onQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Очистить",
                            tint = TextSecondary
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboardController?.hide()
                if (query.isNotBlank()) searchViewModel.performSearch(query)
            }),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkCard,
                unfocusedContainerColor = DarkCard,
                focusedBorderColor = RedPrimary,
                unfocusedBorderColor = DarkCard,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )

        // Блок быстрых подсказок
        if (query.isEmpty()) {
            Text(
                text = "🔥 Популярные исполнители и жанры:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                color = TextSecondary
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                popularSuggestions.forEach { suggestion ->
                    SuggestionChip(
                        onClick = {
                            searchViewModel.onQueryChanged(suggestion)
                            searchViewModel.performSearch(suggestion)
                        },
                        label = { Text(suggestion, color = TextPrimary) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = DarkCard
                        ),
                        border = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RedPrimary)
            }
        } else if (errorMessage != null && results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Ошибка подключения: $errorMessage",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { searchViewModel.loadTrending() },
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text("Повторить")
                    }
                }
            }
        } else if (results.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ничего не найдено по запросу \"$query\".\nПопробуйте ввести другое название.",
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
                items(results, key = { it.id }) { track ->
                    TrackItemRow(
                        track = track,
                        downloadState = downloadStates[track.id],
                        onClick = {
                            playerViewModel.playTrack(track, results)
                        },
                        onDownloadClick = {
                            playerViewModel.downloadTrack(track)
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
