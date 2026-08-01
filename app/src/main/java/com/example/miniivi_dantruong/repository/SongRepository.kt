package com.example.miniivi_dantruong.repository

import com.example.miniivi_dantruong.data.local.LocalSongDataSource
import com.example.miniivi_dantruong.model.Song
import javax.inject.Inject

class SongRepository @Inject constructor(
    private val localSongDataSource: LocalSongDataSource
) {
    fun getSongs(): List<Song> = localSongDataSource.getMockSongs()
}