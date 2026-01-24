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
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private val songList = mutableListOf<Song>()
    private var currentSongIndex = 0

    // 🎵 Mini Player Views
    private lateinit var miniPlayer: View
    private lateinit var miniSongTitle: TextView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton

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

        // 🎵 Mini Player init
        miniPlayer = findViewById(R.id.miniPlayer)
        miniSongTitle = findViewById(R.id.miniSongTitle)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)

        setupMiniPlayerControls()
        checkPermission()
        checkNotificationPermission()
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

        recyclerView.adapter = SongAdapter(songList) { position ->
            playSong(position)
        }
    }
}