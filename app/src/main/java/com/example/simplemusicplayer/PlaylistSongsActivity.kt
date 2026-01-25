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
import android.view.ViewGroup
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
    private lateinit var shuffleButton: ImageButton
    private lateinit var playlistTitle: TextView
    private lateinit var recyclerView: RecyclerView

    // 🎵 Mini Player Views for this activity
    private lateinit var miniPlayer: View
    private lateinit var miniSongTitle: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnClose: ImageButton

    companion object {
        const val EXTRA_PLAYLIST_ID = "playlist_id"
    }

    // Broadcast receiver to update UI based on service state
    private val musicUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("PlaylistSongsActivity", "Broadcast received: ${intent?.action}")

            when (intent?.action) {
                MusicService.ACTION_UPDATE_UI -> {
                    val isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    val songTitle = intent.getStringExtra(MusicService.EXTRA_SONG_TITLE)

                    updateMiniPlayer(isPlaying, songTitle)
                }
            }
        }
    }

    private fun updateMiniPlayer(isPlaying: Boolean, songTitle: String?) {
        if (isPlaying) {
            miniPlayer.visibility = View.VISIBLE

            // Use song title from broadcast if available
            if (!songTitle.isNullOrEmpty()) {
                miniSongTitle.text = songTitle
            } else {
                miniSongTitle.text = "Now Playing"
            }

            // Update play/pause button icon
            val iconRes = if (isPlaying) {
                android.R.drawable.ic_media_pause
            } else {
                android.R.drawable.ic_media_play
            }
            btnPlayPause.setImageResource(iconRes)
            Log.d("PlaylistSongsActivity", "Set icon to: ${if (isPlaying) "PAUSE" else "PLAY"}")
        } else {
            miniPlayer.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist_songs)

        playlistManager = PlaylistManager(this)

        backButton = findViewById(R.id.btnBack)
        shuffleButton = findViewById(R.id.btnShuffle)
        playlistTitle = findViewById(R.id.playlistTitle)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 🎵 Mini Player init for this activity
        miniPlayer = findViewById(R.id.miniPlayer)
        miniSongTitle = findViewById(R.id.miniSongTitle)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnClose = findViewById(R.id.btnClose)

        // Open full player when mini player is clicked
        miniPlayer.setOnClickListener {
            openFullPlayer()
        }

        // Setup mini player controls
        setupMiniPlayerControls()

        backButton.setOnClickListener {
            finish()
        }

        shuffleButton.setOnClickListener {
            if (songList.isNotEmpty()) {
                // Play first song with shuffle enabled
                val intent = Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_START
                    putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
                    putExtra(MusicService.EXTRA_SONG_INDEX, 0)
                }
                ContextCompat.startForegroundService(this, intent)

                val shuffleIntent = Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_SHUFFLE
                }
                startService(shuffleIntent)

                Toast.makeText(this, "Shuffle enabled for playlist", Toast.LENGTH_SHORT).show()
            }
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

    @Suppress("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        // Register broadcast receiver when activity is visible
        val filter = IntentFilter(MusicService.ACTION_UPDATE_UI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicUpdateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(musicUpdateReceiver, filter)
        }
        Log.d("PlaylistSongsActivity", "BroadcastReceiver registered")

        // Check if music is playing when activity resumes
        checkMusicPlayingState()
    }

    private fun checkMusicPlayingState() {
        // Ask service for current state
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_GET_STATE
        }
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        // Unregister when activity is not visible
        try {
            unregisterReceiver(musicUpdateReceiver)
            Log.d("PlaylistSongsActivity", "BroadcastReceiver unregistered")
        } catch (e: Exception) {
            Log.e("PlaylistSongsActivity", "Error unregistering receiver", e)
        }
    }

    private fun setupMiniPlayerControls() {
        btnPlayPause.setOnClickListener {
            Log.d("PlaylistSongsActivity", "Play/Pause clicked")
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_TOGGLE
            }
            startService(intent)
        }

        btnNext.setOnClickListener {
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            startService(intent)
        }

        btnPrev.setOnClickListener {
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            startService(intent)
        }

        btnClose.setOnClickListener {
            Log.d("PlaylistSongsActivity", "Close clicked")
            // Hide the mini player
            miniPlayer.visibility = View.GONE

            // Stop the music service
            val intent = Intent(this, MusicService::class.java)
            stopService(intent)
        }
    }

    private fun showSongOptionsDialog(position: Int) {
        if (position < 0 || position >= songList.size) return

        val song = songList[position]

        // Create options for the song menu with icons
        val options = arrayOf(
            "Add to other playlist",
            "Remove from this playlist",
            "Play on Repeat"
        )

        // Create custom adapter with icons
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView

                // Add icons to menu items
                when (position) {
                    0 -> view.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_playlist, 0, 0, 0)
                    1 -> view.setCompoundDrawablesWithIntrinsicBounds(
                        android.R.drawable.ic_delete, 0, 0, 0)
                    2 -> view.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_repeat_menu, 0, 0, 0)
                }
                view.compoundDrawablePadding = 16

                return view
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Song Options")
            .setAdapter(adapter) { _, which ->
                when (which) {
                    0 -> showAddToPlaylistDialog(song)
                    1 -> showRemoveConfirmationDialog(song, position)
                    2 -> playSongOnRepeat(position)
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

        // Show mini player immediately
        miniPlayer.visibility = View.VISIBLE
        miniSongTitle.text = songList[position].title
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)

        Toast.makeText(this, "Playing: ${songList[position].title}", Toast.LENGTH_SHORT).show()
    }

    private fun playSongOnRepeat(position: Int) {
        if (position < 0 || position >= songList.size) return

        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_START
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, position)
        }
        ContextCompat.startForegroundService(this, intent)

        // Enable repeat one
        val repeatIntent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_REPEAT_ONE
        }
        startService(repeatIntent)

        // Show mini player immediately
        miniPlayer.visibility = View.VISIBLE
        miniSongTitle.text = songList[position].title
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)

        Toast.makeText(this, "Playing on repeat: ${songList[position].title}", Toast.LENGTH_SHORT).show()
    }

    private fun openFullPlayer() {
        // We need to get current state from service
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_GET_STATE
        }
        startService(intent)

        // Open player with current playlist songs
        val playerIntent = Intent(this, PlayerActivity::class.java).apply {
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            // Check if the current icon is pause (meaning it's playing)
            val isPlaying = btnPlayPause.drawable?.constantState ==
                    ContextCompat.getDrawable(this@PlaylistSongsActivity, android.R.drawable.ic_media_pause)?.constantState
            putExtra(MusicService.EXTRA_IS_PLAYING, isPlaying)
        }
        startActivity(playerIntent)
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