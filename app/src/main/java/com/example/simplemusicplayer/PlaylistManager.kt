package com.example.simplemusicplayer

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaylistManager(private val context: Context) {
    private val sharedPref: SharedPreferences = context.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val playlistKey = "user_playlists"
    private var nextPlaylistId = 1

    init {
        // Get next available ID
        val playlists = getAllPlaylists()
        if (playlists.isNotEmpty()) {
            nextPlaylistId = playlists.maxByOrNull { it.id }?.id?.plus(1) ?: 1
        }
    }

    fun getAllPlaylists(): List<Playlist> {
        val json = sharedPref.getString(playlistKey, "[]")
        return gson.fromJson(json, object : TypeToken<List<Playlist>>() {}.type)
    }

    fun createPlaylist(name: String): Playlist {
        val playlists = getAllPlaylists().toMutableList()
        val newPlaylist = Playlist(id = nextPlaylistId, name = name)
        playlists.add(newPlaylist)
        savePlaylists(playlists)
        nextPlaylistId++
        return newPlaylist
    }

    fun addSongToPlaylist(playlistId: Int, songUri: String, songTitle: String) {
        val playlists = getAllPlaylists().toMutableList()
        val playlistIndex = playlists.indexOfFirst { it.id == playlistId }

        if (playlistIndex != -1) {
            val playlist = playlists[playlistIndex]
            if (!playlist.songUris.contains(songUri)) {
                playlist.songUris.add(songUri)
                playlist.songTitles.add(songTitle)
                savePlaylists(playlists)
            }
        }
    }

    fun removeSongFromPlaylist(playlistId: Int, songUri: String) {
        val playlists = getAllPlaylists().toMutableList()
        val playlistIndex = playlists.indexOfFirst { it.id == playlistId }

        if (playlistIndex != -1) {
            val playlist = playlists[playlistIndex]
            val songIndex = playlist.songUris.indexOf(songUri)
            if (songIndex != -1) {
                playlist.songUris.removeAt(songIndex)
                playlist.songTitles.removeAt(songIndex)
                savePlaylists(playlists)
            }
        }
    }

    fun getPlaylist(playlistId: Int): Playlist? {
        return getAllPlaylists().find { it.id == playlistId }
    }

    fun deletePlaylist(playlistId: Int) {
        val playlists = getAllPlaylists().toMutableList()
        val iterator = playlists.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == playlistId) {
                iterator.remove()
                break
            }
        }
        savePlaylists(playlists)
    }

    fun renamePlaylist(playlistId: Int, newName: String) {
        val playlists = getAllPlaylists().toMutableList()
        val playlistIndex = playlists.indexOfFirst { it.id == playlistId }

        if (playlistIndex != -1) {
            val oldPlaylist = playlists[playlistIndex]
            val newPlaylist = oldPlaylist.copy(name = newName)
            playlists[playlistIndex] = newPlaylist
            savePlaylists(playlists)
        }
    }

    private fun savePlaylists(playlists: List<Playlist>) {
        val json = gson.toJson(playlists)
        sharedPref.edit().putString(playlistKey, json).apply()
    }

    fun getSongsForPlaylist(playlistId: Int, allSongs: List<Song>): List<Song> {
        val playlist = getPlaylist(playlistId) ?: return emptyList()
        return allSongs.filter { song ->
            playlist.songUris.contains(song.uri)
        }
    }
}