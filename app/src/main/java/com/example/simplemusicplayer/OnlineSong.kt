package com.example.simplemusicplayer

data class OnlineSong(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val url: String = "",
    val duration: Int = 0
)