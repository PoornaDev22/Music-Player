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
    private lateinit var btnOnline: Button

    // Shuffle bar (All Audio tab only)
    private lateinit var shuffleBar: View
    private lateinit var btnShuffleAll: Button

    // Fragment container for the Online tab
    private lateinit var fragmentContainer: FrameLayout

    private var currentView = "all_audio"

    // Mini Player Views
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
            if (!granted) Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }

    private val musicUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
            miniSongTitle.text = if (!songTitle.isNullOrEmpty()) songTitle else "Now Playing"
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            miniPlayer.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        playlistManager = PlaylistManager(this)

        btnAllAudio = findViewById(R.id.btnAllAudio)
        btnPlaylists = findViewById(R.id.btnPlaylists)
        btnOnline = findViewById(R.id.btnOnline)
        shuffleBar = findViewById(R.id.shuffleBar)
        btnShuffleAll = findViewById(R.id.btnShuffleAll)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        miniPlayer = findViewById(R.id.miniPlayer)
        miniSongTitle = findViewById(R.id.miniSongTitle)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnClose = findViewById(R.id.btnClose)

        miniPlayer.setOnClickListener { openFullPlayer() }
        btnAllAudio.setOnClickListener { showAllAudioView() }
        btnPlaylists.setOnClickListener { showPlaylistsView() }
        btnOnline.setOnClickListener { showOnlineView() }

        // Shuffle all local songs — picks a random start, plays with shuffle on
        btnShuffleAll.setOnClickListener {
            if (songList.isNotEmpty()) {
                val randomIndex = (0 until songList.size).random()
                val intent = Intent(this, MusicService::class.java).apply {
                    action = MusicService.ACTION_START_SHUFFLED
                    putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
                    putExtra(MusicService.EXTRA_SONG_INDEX, randomIndex)
                }
                ContextCompat.startForegroundService(this, intent)
            }
        }

        setupMiniPlayerControls()
        checkPermission()
        checkNotificationPermission()
        showAllAudioView()
    }

    private fun showAllAudioView() {
        currentView = "all_audio"
        btnAllAudio.isEnabled = false
        btnPlaylists.isEnabled = true
        btnOnline.isEnabled = true

        recyclerView.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
        shuffleBar.visibility = View.VISIBLE   // show shuffle bar for All Audio

        loadSongs()
    }

    private fun showPlaylistsView() {
        currentView = "playlists"
        btnAllAudio.isEnabled = true
        btnPlaylists.isEnabled = false
        btnOnline.isEnabled = true

        recyclerView.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
        shuffleBar.visibility = View.GONE      // no shuffle bar for Playlists

        val playlists = playlistManager.getAllPlaylists()
        recyclerView.adapter = PlaylistAdapter(playlists) { playlistId ->
            openPlaylistSongs(playlistId)
        }
    }

    private fun showOnlineView() {
        currentView = "online"
        btnAllAudio.isEnabled = true
        btnPlaylists.isEnabled = true
        btnOnline.isEnabled = false

        recyclerView.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE
        shuffleBar.visibility = View.GONE      // Online fragment has its own shuffle button

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, OnlineSongsFragment())
                .commit()
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
        val filter = IntentFilter(MusicService.ACTION_UPDATE_UI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicUpdateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(musicUpdateReceiver, filter)
        }
        startService(Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_GET_STATE
        })
        when (currentView) {
            "all_audio" -> showAllAudioView()
            "playlists" -> showPlaylistsView()
            "online" -> showOnlineView()
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(musicUpdateReceiver) } catch (e: Exception) { }
    }

    private fun setupMiniPlayerControls() {
        btnPlayPause.setOnClickListener {
            startService(Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_TOGGLE
            })
        }
        btnNext.setOnClickListener {
            startService(Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_NEXT
            })
        }
        btnPrev.setOnClickListener {
            startService(Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_PREVIOUS
            })
        }
        btnClose.setOnClickListener {
            miniPlayer.visibility = View.GONE
            stopService(Intent(this, MusicService::class.java))
        }
    }

    private fun checkPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(permission)
        } else {
            checkNotificationPermission()
            loadSongs()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun playSong(index: Int) {
        if (index < 0 || index >= songList.size) return
        currentSongIndex = index
        miniPlayer.visibility = View.VISIBLE
        miniSongTitle.text = songList[index].title
        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        ContextCompat.startForegroundService(this, Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_START
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, index)
        })
    }

    private fun loadSongs() {
        songList.clear()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE)
        val cursor: Cursor? = contentResolver.query(
            uri, projection, null, null, MediaStore.Audio.Media.TITLE + " ASC"
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
            onSongClick = { position -> playSong(position) },
            onMenuClick = { position -> showSongOptionsDialog(position) }
        )
    }

    private fun showSongOptionsDialog(position: Int) {
        if (position < 0 || position >= songList.size) return
        val song = songList[position]
        val options = arrayOf("Add to Playlist", "Play on Repeat")
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                when (position) {
                    0 -> view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_playlist, 0, 0, 0)
                    1 -> view.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_repeat_menu, 0, 0, 0)
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
        val adapter = ArrayAdapter(this, R.layout.dialog_playlist_item, playlistNames)
        playlistListView.adapter = adapter
        playlistListView.choiceMode = ListView.CHOICE_MODE_SINGLE
        var selectedPlaylistId = -1
        playlistListView.setOnItemClickListener { _, _, pos, _ ->
            if (pos == allPlaylists.size) {
                newPlaylistEditText.visibility = View.VISIBLE
                newPlaylistEditText.requestFocus()
                selectedPlaylistId = -1
            } else {
                selectedPlaylistId = allPlaylists[pos].id
                newPlaylistEditText.visibility = View.GONE
            }
            adapter.notifyDataSetChanged()
        }
        AlertDialog.Builder(this)
            .setTitle("Add to Playlist")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                if (selectedPlaylistId != -1) {
                    playlistManager.addSongToPlaylist(selectedPlaylistId, song.uri, song.title)
                    Toast.makeText(this, "Added to: ${playlistManager.getPlaylist(selectedPlaylistId)?.name}", Toast.LENGTH_SHORT).show()
                } else if (newPlaylistEditText.text.toString().isNotBlank()) {
                    val name = newPlaylistEditText.text.toString()
                    val newPlaylist = playlistManager.createPlaylist(name)
                    playlistManager.addSongToPlaylist(newPlaylist.id, song.uri, song.title)
                    Toast.makeText(this, "Added to new playlist: $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playSongOnRepeat(position: Int) {
        playSong(position)
        startService(Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_REPEAT_ONE
        })
        Toast.makeText(this, "Playing on repeat", Toast.LENGTH_SHORT).show()
    }

    private fun openFullPlayer() {
        startService(Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_GET_STATE
        })
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
            putExtra(MusicService.EXTRA_SONG_INDEX, currentSongIndex)
            val isPlaying = btnPlayPause.drawable?.constantState ==
                    ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_media_pause)?.constantState
            putExtra(MusicService.EXTRA_IS_PLAYING, isPlaying)
        })
    }
}