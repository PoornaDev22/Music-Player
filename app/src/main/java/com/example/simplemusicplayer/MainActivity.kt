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
            Log.d("MainActivity", "Set icon to: ${if (isPlaying) "PAUSE" else "PLAY"}")
        } else {
            miniPlayer.visibility = View.GONE
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

        // Load and show playlists
        val playlists = playlistManager.getAllPlaylists()
        recyclerView.adapter = PlaylistAdapter(playlists) { playlistId ->
            openPlaylistSongs(playlistId)
        }
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

        // Check if music is playing when activity resumes
        checkMusicPlayingState()

        // Refresh current view
        when (currentView) {
            "all_audio" -> showAllAudioView()
            "playlists" -> showPlaylistsView()
        }
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
                showSongOptionsDialog(position)
            }
        )
    }

    private fun showSongOptionsDialog(position: Int) {
        if (position < 0 || position >= songList.size) return

        val song = songList[position]

        // Create options for the song menu with icons
        val options = arrayOf("Add to Playlist", "Play on Repeat")

        // Create custom adapter with icons
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView

                // Add icons to menu items
                when (position) {
                    0 -> view.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_playlist, 0, 0, 0)
                    1 -> view.setCompoundDrawablesWithIntrinsicBounds(
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
                    1 -> playSongOnRepeat(position)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddToPlaylistDialog(song: Song) {
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

    private fun playSongOnRepeat(position: Int) {
        // Play the song
        playSong(position)

        // Enable repeat one
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_REPEAT_ONE
        }
        startService(intent)

        Toast.makeText(this, "Playing on repeat", Toast.LENGTH_SHORT).show()
    }

    private fun openFullPlayer() {
        // We need to get current state from service
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_GET_STATE
        }
        startService(intent)

        // The service will broadcast the state, and we can open player with that info
        // For now, open with current song list (might be from playlist)
        val playerIntent = Intent(this, PlayerActivity::class.java).apply {
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, currentSongIndex)
            // Check if the current icon is pause (meaning it's playing)
            val isPlaying = btnPlayPause.drawable?.constantState ==
                    ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_media_pause)?.constantState
            putExtra(MusicService.EXTRA_IS_PLAYING, isPlaying)
        }
        startActivity(playerIntent)
    }
}