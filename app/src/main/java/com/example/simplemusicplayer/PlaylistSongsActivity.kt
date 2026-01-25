package com.example.simplemusicplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class PlaylistSongsActivity : AppCompatActivity() {
    private lateinit var playlistManager: PlaylistManager
    private lateinit var allSongs: List<Song>
    private lateinit var playlist: Playlist
    private var playlistId: Int = -1
    private val songList = mutableListOf<Song>()

    private lateinit var backButton: ImageButton
    private lateinit var playlistTitle: TextView
    private lateinit var recyclerView: RecyclerView

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_songs)

        playlistManager = PlaylistManager(this)

        backButton = findViewById(R.id.btnBack)
        playlistTitle = findViewById(R.id.playlistTitle)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        backButton.setOnClickListener {
            finish()
        }

        playlistId = intent.getIntExtra(EXTRA_PLAYLIST_ID, -1)
        if (playlistId != -1) {
            playlist = playlistManager.getPlaylist(playlistId) ?: run {
                finish()
                return
            }

            playlistTitle.text = playlist.name

            // Get all songs from the device
            allSongs = loadAllSongs()
            songList.clear()
            songList.addAll(playlistManager.getSongsForPlaylist(playlistId, allSongs))

            recyclerView.adapter = SongAdapterWithMenu(songList,
                onSongClick = { position ->
                    playSong(position)
                },
                onMenuClick = { position ->
                    showSongOptionsDialog(position)
                }
            )
        }
    }

    private fun showSongOptionsDialog(position: Int) {
        if (position < 0 || position >= songList.size) return

        val song = songList[position]

        val options = arrayOf("Add to other playlist", "Remove from this playlist")

        AlertDialog.Builder(this)
            .setTitle("Song Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAddToPlaylistDialog(song)
                    1 -> showRemoveConfirmationDialog(song, position)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val allPlaylists = playlistManager.getAllPlaylists()
        // Filter out current playlist
        val otherPlaylists = allPlaylists.filter { it.id != playlistId }

        val playlistNames = otherPlaylists.map { it.name }.toMutableList()
        playlistNames.add("Create new playlist...")

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_playlist, null)
        val playlistListView = dialogView.findViewById<ListView>(R.id.playlistListView)
        val newPlaylistEditText = dialogView.findViewById<EditText>(R.id.newPlaylistEditText)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, playlistNames)
        playlistListView.adapter = adapter

        var selectedPlaylistId = -1

        playlistListView.setOnItemClickListener { _, _, pos, _ ->
            if (pos == otherPlaylists.size) {
                // "Create new playlist..." selected
                newPlaylistEditText.visibility = View.VISIBLE
                newPlaylistEditText.requestFocus()
                selectedPlaylistId = -1
            } else {
                // Existing playlist selected
                selectedPlaylistId = otherPlaylists[pos].id
                newPlaylistEditText.visibility = View.GONE
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Add to Playlist")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                if (selectedPlaylistId != -1) {
                    // Add to existing playlist
                    playlistManager.addSongToPlaylist(selectedPlaylistId, song.uri, song.title)
                    val playlist = playlistManager.getPlaylist(selectedPlaylistId)
                    Toast.makeText(this, "Added to playlist: ${playlist?.name}", Toast.LENGTH_SHORT).show()
                } else if (newPlaylistEditText.text.toString().isNotBlank()) {
                    // Create new playlist and add song
                    val playlistName = newPlaylistEditText.text.toString()
                    val newPlaylist = playlistManager.createPlaylist(playlistName)
                    playlistManager.addSongToPlaylist(newPlaylist.id, song.uri, song.title)
                    Toast.makeText(this, "Added to new playlist: $playlistName", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemoveConfirmationDialog(song: Song, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Remove Song")
            .setMessage("Remove '${song.title}' from this playlist?")
            .setPositiveButton("Remove") { _, _ ->
                playlistManager.removeSongFromPlaylist(playlistId, song.uri)
                songList.removeAt(position)
                recyclerView.adapter?.notifyItemRemoved(position)
                Toast.makeText(this, "Song removed from playlist", Toast.LENGTH_SHORT).show()

                // If playlist is empty, go back
                if (songList.isEmpty()) {
                    finish()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playSong(position: Int) {
        if (position < 0 || position >= songList.size) return

        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_START
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, position)
        }
        ContextCompat.startForegroundService(this, intent)

        // Open player activity
        val playerIntent = Intent(this, PlayerActivity::class.java).apply {
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, position)
            putExtra(MusicService.EXTRA_IS_PLAYING, true)
        }
        startActivity(playerIntent)
    }

    private fun loadAllSongs(): List<Song> {
        val songs = mutableListOf<Song>()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE
        )

        val cursor: Cursor? = contentResolver.query(
            uri,
            projection,
            null,
            null,
            MediaStore.Audio.Media.TITLE + " ASC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

            while (it.moveToNext()) {
                val id = it.getLong(idIndex)
                val title = it.getString(titleIndex)
                val contentUri: Uri = ContentUris.withAppendedId(uri, id)

                songs.add(Song(title, contentUri.toString()))
            }
        }

        return songs
    }
}