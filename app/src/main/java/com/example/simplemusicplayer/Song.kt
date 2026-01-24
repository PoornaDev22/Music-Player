package com.example.simplemusicplayer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val title: String,
    val uri: String
) : Parcelable
