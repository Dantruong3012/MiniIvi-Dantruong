package com.example.miniivi_dantruong.ui.media

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.miniivi_dantruong.model.Song
import com.example.miniivi_dantruong.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val songRepository: SongRepository
): ViewModel(), DefaultLifecycleObserver {
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs = _songs.asStateFlow()

    private val _mediaStatus = MutableStateFlow<MediaStatus>(MediaStatus.stopped)
    val mediaStatus = _mediaStatus.asStateFlow()


    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong = _currentSong.asStateFlow()


    fun loadSongs() {
        _songs.value = songRepository.getSongs()
    }

    fun play() {
        _mediaStatus.value = MediaStatus.playing
    }

    fun pause() {
        _mediaStatus.value = MediaStatus.paused
    }

    fun stop() {
        _mediaStatus.value = MediaStatus.stopped
    }

    fun nextSong() {
        val currentIndex = _songs.value.indexOfFirst { it == _currentSong.value }
        if (currentIndex != -1 && currentIndex < _songs.value.size - 1) {
            _currentSong.value = _songs.value[currentIndex + 1]
            _mediaStatus.value = MediaStatus.playing
        }
    }

    fun selectSong(songId: Int) {
        val song = _songs.value.find { it.id == songId }
        _currentSong.value = song
        _mediaStatus.value = MediaStatus.playing
    }

    fun previousSong() {
        val currentIndex = _songs.value.indexOfFirst { it == _currentSong.value }
        if (currentIndex > 0) {
            _currentSong.value = _songs.value[currentIndex - 1]
            _mediaStatus.value = MediaStatus.playing
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        Log.d("MiniIviLog", "MediaViewModel: onCreate triggered")
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d("MiniIviLog", "MediaViewModel: onStart triggered")
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.d("MiniIviLog", "MediaViewModel: onResume triggered")
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.d("MiniIviLog", "MediaViewModel: onPause triggered")
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d("MiniIviLog", "MediaViewModel: onStop triggered - pausing playback")
        pausePlayback()
    }

    private fun pausePlayback() {
        _mediaStatus.value = MediaStatus.paused
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Log.d("MiniIviLog", "MediaViewModel: onDestroy triggered")
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("MiniIviLog", "MediaViewModel: onCleared triggered")
    }

}