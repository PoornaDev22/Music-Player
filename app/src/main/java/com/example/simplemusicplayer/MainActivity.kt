package com.example.simplemusicplayer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private val songList = mutableListOf<Song>()
    private var currentSongIndex = 0
    private lateinit var playlistManager: PlaylistManager

    // Tab buttons
    private lateinit var btnAllAudio: Button
    private lateinit var btnPlaylists: Button

    // Current view state
    private var currentView = "all_audio" // or "playlists"

    // 🎵 Mini Player Views
    private lateinit var miniPlayer: View
    private lateinit var miniSongTitle: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnClose: ImageButton

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) loadSongs()
            else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    // Broadcast receiver to update UI based on service state
    private val musicUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "Broadcast received: ${intent?.action}")

            when (intent?.action) {
                MusicService.ACTION_UPDATE_UI -> {
                    val isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    val index = intent.getIntExtra(MusicService.EXTRA_SONG_INDEX, 0)
                    currentSongIndex = index

                    Log.d("MainActivity", "Received update: isPlaying=$isPlaying, index=$index")

                    if (index >= 0 && index < songList.size) {
                        miniPlayer.visibility = View.VISIBLE
                        miniSongTitle.text = songList[index].title

                        // Update play/pause button icon
                        val iconRes = if (isPlaying) {
                            android.R.drawable.ic_media_pause
                        } else {
                            android.R.drawable.ic_media_play
                        }
                        btnPlayPause.setImageResource(iconRes)
                        Log.d("MainActivity", "Set icon to: ${if (isPlaying) "PAUSE" else "PLAY"}")
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        playlistManager = PlaylistManager(this)

        // Tab buttons
        btnAllAudio = findViewById(R.id.btnAllAudio)
        btnPlaylists = findViewById(R.id.btnPlaylists)

        // 🎵 Mini Player init
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

        // Setup tab button click listeners
        btnAllAudio.setOnClickListener {
            showAllAudioView()
        }

        btnPlaylists.setOnClickListener {
            showPlaylistsView()
        }

        setupMiniPlayerControls()
        checkPermission()
        checkNotificationPermission()

        // Show all audio by default
        showAllAudioView()
    }

    private fun showAllAudioView() {
        currentView = "all_audio"
        btnAllAudio.isEnabled = false
        btnPlaylists.isEnabled = true

        // Load and show all songs
        loadSongs()
    }

    private fun showPlaylistsView() {
        currentView = "playlists"
        btnAllAudio.isEnabled = true
        btnPlaylists.isEnabled = false

        // Load and show playlists with menu
        val playlists = playlistManager.getAllPlaylists()
        recyclerView.adapter = PlaylistAdapterWithMenu(playlists,
            onClick = { playlistId ->
                openPlaylistSongs(playlistId)
            },
            onMenuClick = { playlistId ->
                showPlaylistOptionsDialog(playlistId)
            }
        )
    }

    private fun openPlaylistSongs(playlistId: Int) {
        val intent = Intent(this, PlaylistSongsActivity::class.java).apply {
            putExtra(PlaylistSongsActivity.EXTRA_PLAYLIST_ID, playlistId)
        }
        startActivity(intent)
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
        Log.d("MainActivity", "BroadcastReceiver registered")

        // Refresh current view
        when (currentView) {
            "all_audio" -> showAllAudioView()
            "playlists" -> showPlaylistsView()
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister when activity is not visible
        try {
            unregisterReceiver(musicUpdateReceiver)
            Log.d("MainActivity", "BroadcastReceiver unregistered")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver", e)
        }
    }

    private fun setupMiniPlayerControls() {
        btnPlayPause.setOnClickListener {
            Log.d("MainActivity", "Play/Pause clicked")
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_TOGGLE
            }
            startService(intent)
        }

        btnNext.setOnClickListener {
            if (songList.isEmpty()) return@setOnClickListener
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            }
            startService(intent)
        }

        btnPrev.setOnClickListener {
            if (songList.isEmpty()) return@setOnClickListener
            val intent = Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            }
            startService(intent)
        }

        btnClose.setOnClickListener {
            Log.d("MainActivity", "Close clicked")
            // Hide the mini player
            miniPlayer.visibility = View.GONE

            // Stop the music service
            val intent = Intent(this, MusicService::class.java)
            stopService(intent)
        }
    }

    private fun checkPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(permission)
        } else {
            checkNotificationPermission()
            loadSongs()
        }
    }

    private fun playSong(index: Int) {
        if (index < 0 || index >= songList.size) return

        currentSongIndex = index

        // 🎵 Update Mini Player UI
        miniPlayer.visibility = View.VISIBLE
        miniSongTitle.text = songList[index].title
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)

        // Start the service for the selected song
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_START
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, index)
        }

        ContextCompat.startForegroundService(this, intent)
    }

    private fun loadSongs() {
        songList.clear()

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

                songList.add(Song(title, contentUri.toString()))
            }
        }

        recyclerView.adapter = SongAdapterWithMenu(songList,
            onSongClick = { position ->
                playSong(position)
            },
            onMenuClick = { position ->
                showPlaylistDialog(position)
            }
        )
    }

    private fun showPlaylistDialog(position: Int) {
        if (position < 0 || position >= songList.size) return

        val song = songList[position]
        val allPlaylists = playlistManager.getAllPlaylists()

        val playlistNames = allPlaylists.map { it.name }.toMutableList()
        playlistNames.add("Create new playlist...")

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_playlist, null)
        val playlistListView = dialogView.findViewById<ListView>(R.id.playlistListView)
        val newPlaylistEditText = dialogView.findViewById<EditText>(R.id.newPlaylistEditText)

        // Create adapter with custom layout for highlighting
        val adapter = ArrayAdapter(this, R.layout.dialog_playlist_item, playlistNames)
        playlistListView.adapter = adapter
        playlistListView.choiceMode = ListView.CHOICE_MODE_SINGLE

        var selectedPlaylistId = -1
        var selectedPosition = -1

        playlistListView.setOnItemClickListener { _, _, pos, _ ->
            // Update the selected position for highlighting
            selectedPosition = pos

            if (pos == allPlaylists.size) {
                // "Create new playlist..." selected
                newPlaylistEditText.visibility = View.VISIBLE
                newPlaylistEditText.requestFocus()
                selectedPlaylistId = -1
            } else {
                // Existing playlist selected
                selectedPlaylistId = allPlaylists[pos].id
                newPlaylistEditText.visibility = View.GONE
            }

            // Force update of views to show selection
            adapter.notifyDataSetChanged()
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

    private fun showPlaylistOptionsDialog(playlistId: Int) {
        val playlist = playlistManager.getPlaylist(playlistId) ?: return
        val options = arrayOf("Rename", "Delete")

        AlertDialog.Builder(this)
            .setTitle("Playlist Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenamePlaylistDialog(playlistId, playlist.name)
                    1 -> showDeletePlaylistDialog(playlistId, playlist.name)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenamePlaylistDialog(playlistId: Int, currentName: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rename_playlist, null)
        val editText = dialogView.findViewById<EditText>(R.id.editText)
        editText.setText(currentName)
        editText.setSelection(currentName.length)

        AlertDialog.Builder(this)
            .setTitle("Rename Playlist")
            .setView(dialogView)
            .setPositiveButton("Rename") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotBlank()) {
                    playlistManager.renamePlaylist(playlistId, newName)
                    showPlaylistsView() // Refresh the list
                    Toast.makeText(this, "Playlist renamed", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeletePlaylistDialog(playlistId: Int, playlistName: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Playlist")
            .setMessage("Are you sure you want to delete '$playlistName'?")
            .setPositiveButton("Delete") { _, _ ->
                playlistManager.deletePlaylist(playlistId)
                showPlaylistsView() // Refresh the list
                Toast.makeText(this, "Playlist deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openFullPlayer() {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, currentSongIndex)
            // Check if the current icon is pause (meaning it's playing)
            val isPlaying = btnPlayPause.drawable?.constantState ==
                    ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_media_pause)?.constantState
            putExtra(MusicService.EXTRA_IS_PLAYING, isPlaying)
        }
        startActivity(intent)
    }
}