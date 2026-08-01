package com.example.miniivi_dantruong.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.miniivi_dantruong.model.Song

@Composable
fun MediaScreen(viewModel: MediaViewModel = hiltViewModel(), modifier: Modifier = Modifier) {
    val songStatus by viewModel.mediaStatus.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSongs()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentSong != null) {
                PlayerBottomBar(
                    song = currentSong!!,
                    status = songStatus,
                    onPlayPauseClick = {
                        if (songStatus == MediaStatus.playing) viewModel.pause() else viewModel.play()
                    },
                    onNextClick = { viewModel.nextSong() },
                    onPrevClick = { viewModel.previousSong() }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(songs) { song ->
                SongCard(
                    song = song,
                    isSelected = song.id == currentSong?.id,
                    onClick = { viewModel.selectSong(song.id) }
                )
            }
        }
    }
}


@Composable
fun SongCard(
    song: Song,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Bài hát: ${song.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun PlayerBottomBar(
    song: Song,
    status: MediaStatus,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Đang phát: ${song.title}", // Thay bằng song.title
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevClick) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
            }

            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (status == MediaStatus.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play/Pause"
                )
            }

            IconButton(onClick = onNextClick) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next")
            }
        }
    }
}