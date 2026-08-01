package com.example.miniivi_dantruong.ui.media

sealed class MediaStatus {
    object playing: MediaStatus()
    object paused: MediaStatus()
    object stopped: MediaStatus()
}