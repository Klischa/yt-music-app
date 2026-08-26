package com.klischa.ytmusic.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.klischa.ytmusic.domain.model.Track
import com.klischa.ytmusic.presentation.ui.theme.DarkBackground
import com.klischa.ytmusic.presentation.ui.theme.RedPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextPrimary
import com.klischa.ytmusic.presentation.ui.theme.TextSecondary

@Composable
fun FullPlayerScreen(
    track: Track?,
    isPlaying: Boolean,
    progressMs: Long,
    durationMs: Long,
    onCloseClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    if (track == null) return

    var isUserSeeking by remember { mutableStateOf(false) }
    var userSeekPosition by remember { mutableFloatStateOf(0f) }

    val currentSliderValue = if (isUserSeeking) {
        userSeekPosition
    } else {
        progressMs.toFloat()
    }

    val maxSliderValue = durationMs.toFloat().coerceAtLeast(1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Верхняя панель: кнопка свернуть
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

        Spacer(modifier = Modifier.height(24.dp))

        // Большой постер альбома
        AsyncImage(
            model = track.thumbnailUrl.ifEmpty { "https://music.youtube.com/img/on_platform_logo_dark.svg" },
            contentDescription = track.title,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Название и исполнитель
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = track.artist + if (track.album.isNotEmpty()) " • ${track.album}" else "",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

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
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                    tint = RedPrimary,
                    modifier = Modifier.size(72.dp)
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
    }
}
