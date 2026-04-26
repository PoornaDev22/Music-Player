package com.example.simplemusicplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class PlayerActivity : AppCompatActivity() {

    private lateinit var albumArt: ImageView
    private lateinit var songTitle: TextView
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton

    private var songList: List<Song> = emptyList()
    private var currentIndex = 0
    private var isPlaying = false
    private var isShuffleEnabled = false
    private var isRepeatOneEnabled = false
    private var duration = 0
    private var currentPosition = 0

    private val handler = Handler(Looper.getMainLooper())
    private var updateSeekBarRunnable: Runnable? = null

    private val musicUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MusicService.ACTION_UPDATE_UI -> {
                    isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)
                    currentIndex = intent.getIntExtra(MusicService.EXTRA_SONG_INDEX, 0)
                    isShuffleEnabled = intent.getBooleanExtra(MusicService.EXTRA_IS_SHUFFLE, false)
                    isRepeatOneEnabled = intent.getBooleanExtra(MusicService.EXTRA_IS_REPEAT_ONE, false)

                    // Read the song title the service already resolves correctly
                    // (accounting for shuffle order). This is what was missing before.
                    val title = intent.getStringExtra(MusicService.EXTRA_SONG_TITLE)
                    if (!title.isNullOrEmpty()) {
                        songTitle.text = title
                    }

                    updateUI()
                }
                MusicService.ACTION_UPDATE_PROGRESS -> {
                    currentPosition = intent.getIntExtra(MusicService.EXTRA_CURRENT_POSITION, 0)
                    duration = intent.getIntExtra(MusicService.EXTRA_DURATION, 0)
                    isShuffleEnabled = intent.getBooleanExtra(MusicService.EXTRA_IS_SHUFFLE, false)
                    isRepeatOneEnabled = intent.getBooleanExtra(MusicService.EXTRA_IS_REPEAT_ONE, false)
                    updateProgress()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        albumArt = findViewById(R.id.albumArt)
        songTitle = findViewById(R.id.songTitle)
        currentTime = findViewById(R.id.currentTime)
        totalTime = findViewById(R.id.totalTime)
        seekBar = findViewById(R.id.seekBar)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnBack = findViewById(R.id.btnBack)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)

        songList = intent.getParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST) ?: emptyList()
        currentIndex = intent.getIntExtra(MusicService.EXTRA_SONG_INDEX, 0)
        isPlaying = intent.getBooleanExtra(MusicService.EXTRA_IS_PLAYING, false)

        // Show the title immediately from the intent's song list while we wait
        // for the service broadcast to confirm the real (shuffle-aware) title.
        songTitle.isSelected = true // enable marquee scrolling
        if (songList.isNotEmpty() && currentIndex in songList.indices) {
            songTitle.text = songList[currentIndex].title
        }

        setupControls()
        updateUI()
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(MusicService.ACTION_UPDATE_UI)
            addAction(MusicService.ACTION_UPDATE_PROGRESS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(musicUpdateReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(musicUpdateReceiver, filter)
        }

        // Ask the service for its current state; it will broadcast ACTION_UPDATE_UI
        // which carries the correct title (already shuffle-aware from our MusicService fix).
        val intent = Intent(this, MusicService::class.java).apply {
            action = MusicService.ACTION_GET_STATE
        }
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(musicUpdateReceiver)
        } catch (e: Exception) {
            Log.e("PlayerActivity", "Error unregistering receiver", e)
        }
    }

    private fun setupControls() {
        btnBack.setOnClickListener { finish() }

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

        btnShuffle.setOnClickListener {
            startService(Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_SHUFFLE
            })
        }

        btnRepeat.setOnClickListener {
            startService(Intent(this, MusicService::class.java).apply {
                action = MusicService.ACTION_REPEAT_ONE
            })
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) currentTime.text = formatTime(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    startService(Intent(this@PlayerActivity, MusicService::class.java).apply {
                        action = MusicService.ACTION_SEEK
                        putExtra(MusicService.EXTRA_SEEK_POSITION, it.progress)
                    })
                }
            }
        })
    }

    private fun updateUI() {
        // songTitle.text is set by the broadcast receiver — don't overwrite it here.
        songTitle.isSelected = true // keep marquee scrolling active

        btnPlayPause.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        btnShuffle.setImageResource(
            if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
        )
        btnRepeat.setImageResource(
            if (isRepeatOneEnabled) R.drawable.ic_repeat_one_on else R.drawable.ic_repeat_one_off
        )
        albumArt.setImageResource(R.drawable.ic_music_note)
    }

    private fun updateProgress() {
        seekBar.max = duration
        seekBar.progress = currentPosition
        currentTime.text = formatTime(currentPosition)
        totalTime.text = formatTime(duration)
    }

    private fun formatTime(milliseconds: Int): String {
        val seconds = (milliseconds / 1000) % 60
        val minutes = (milliseconds / (1000 * 60)) % 60
        val hours = (milliseconds / (1000 * 60 * 60))
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%d:%02d", minutes, seconds)
    }
}