package com.example.simplemusicplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Playlist(
    val id: Int,
    val name: String,
    val songUris: MutableList<String> = mutableListOf(),
    val songTitles: MutableList<String> = mutableListOf()
) : Parcelable