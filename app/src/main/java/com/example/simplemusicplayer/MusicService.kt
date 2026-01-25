package com.example.simplemusicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import kotlin.random.Random

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var currentIndex = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null

    // Shuffle and repeat states
    private var isShuffleEnabled = false
    private var isRepeatOneEnabled = false
    private var shuffleOrder: MutableList<Int> = mutableListOf()
    private var originalOrder: MutableList<Int> = mutableListOf()

    companion object {
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_TOGGLE = "ACTION_TOGGLE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREVIOUS = "ACTION_PREVIOUS"
        const val ACTION_SHUFFLE = "ACTION_SHUFFLE"
        const val ACTION_REPEAT_ONE = "ACTION_REPEAT_ONE"
        const val ACTION_UPDATE_UI = "ACTION_UPDATE_UI"
        const val ACTION_UPDATE_PROGRESS = "ACTION_UPDATE_PROGRESS"
        const val ACTION_SEEK = "ACTION_SEEK"
        const val ACTION_GET_STATE = "ACTION_GET_STATE"
        const val EXTRA_SONG_LIST = "EXTRA_SONG_LIST"
        const val EXTRA_SONG_INDEX = "EXTRA_SONG_INDEX"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
        const val EXTRA_CURRENT_POSITION = "EXTRA_CURRENT_POSITION"
        const val EXTRA_DURATION = "EXTRA_DURATION"
        const val EXTRA_SEEK_POSITION = "EXTRA_SEEK_POSITION"
        const val EXTRA_IS_SHUFFLE = "EXTRA_IS_SHUFFLE"
        const val EXTRA_IS_REPEAT_ONE = "EXTRA_IS_REPEAT_ONE"
        const val EXTRA_SONG_TITLE = "EXTRA_SONG_TITLE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("MusicService", "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> {
                val songs = intent.getParcelableArrayListExtra<Song>(EXTRA_SONG_LIST)
                val index = intent.getIntExtra(EXTRA_SONG_INDEX, 0)
                if (songs != null) {
                    songList = songs
                    currentIndex = index
                    initializePlayOrder()
                    playSong(currentIndex)
                }
            }
            ACTION_TOGGLE -> {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.pause()
                        stopProgressUpdates()
                        Log.d("MusicService", "Paused playback")
                        updateUI(false)
                        updateNotification(false)
                    } else {
                        it.start()
                        startProgressUpdates()
                        Log.d("MusicService", "Resumed playback")
                        updateUI(true)
                        updateNotification(true)
                    }
                }
            }
            ACTION_NEXT -> {
                if (songList.isNotEmpty()) {
                    playNextSong()
                }
            }
            ACTION_PREVIOUS -> {
                if (songList.isNotEmpty()) {
                    playPreviousSong()
                }
            }
            ACTION_SHUFFLE -> {
                isShuffleEnabled = !isShuffleEnabled
                if (isShuffleEnabled) {
                    generateShuffleOrder()
                    currentIndex = shuffleOrder.indexOf(currentIndex)
                } else {
                    currentIndex = originalOrder[currentIndex]
                }
                updateUI(mediaPlayer?.isPlaying ?: false)
            }
            ACTION_REPEAT_ONE -> {
                isRepeatOneEnabled = !isRepeatOneEnabled
                updateUI(mediaPlayer?.isPlaying ?: false)
            }
            ACTION_SEEK -> {
                val position = intent.getIntExtra(EXTRA_SEEK_POSITION, 0)
                mediaPlayer?.seekTo(position)
                updateProgressBroadcast()
            }
            ACTION_GET_STATE -> {
                // Send current state to activity
                mediaPlayer?.let {
                    updateUI(it.isPlaying)
                    updateProgressBroadcast()
                }
            }
        }
        return START_STICKY
    }

    private fun initializePlayOrder() {
        originalOrder = songList.indices.toMutableList()
        shuffleOrder = originalOrder.toMutableList()
    }

    private fun generateShuffleOrder() {
        shuffleOrder = songList.indices.toMutableList()
        shuffleOrder.shuffle()
        // Ensure current song stays in position if it's playing
        if (mediaPlayer?.isPlaying == true) {
            val currentInShuffle = shuffleOrder.indexOf(currentIndex)
            if (currentInShuffle != -1) {
                shuffleOrder.removeAt(currentInShuffle)
                shuffleOrder.add(0, currentIndex)
            }
        }
    }

    private fun playSong(index: Int) {
        if (songList.isEmpty()) return

        val actualIndex = if (isShuffleEnabled) {
            shuffleOrder.getOrNull(index) ?: return
        } else {
            index
        }

        if (actualIndex < 0 || actualIndex >= songList.size) return
        currentIndex = index

        mediaPlayer?.release()
        val song = songList[actualIndex]

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MusicService, Uri.parse(song.uri))
                prepare()
                start()
                setOnCompletionListener {
                    if (isRepeatOneEnabled) {
                        // Repeat the same song
                        playSong(currentIndex)
                    } else {
                        // Play next song
                        playNextSong()
                    }
                }
            }

            Log.d("MusicService", "Started playing: ${song.title}, shuffle: $isShuffleEnabled, repeat: $isRepeatOneEnabled")
            updateUI(true)
            updateNotification(true)
            startProgressUpdates()
        } catch (e: Exception) {
            Log.e("MusicService", "Error playing song", e)
            e.printStackTrace()
            // Try to play next song if current fails
            if (!isRepeatOneEnabled) {
                playNextSong()
            }
        }
    }

    private fun playNextSong() {
        if (songList.isEmpty()) return

        val nextIndex = (currentIndex + 1) % songList.size
        playSong(nextIndex)
    }

    private fun playPreviousSong() {
        if (songList.isEmpty()) return

        val prevIndex = if (currentIndex - 1 < 0) songList.size - 1 else currentIndex - 1
        playSong(prevIndex)
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                updateProgressBroadcast()
                handler.postDelayed(this, 1000) // Update every second
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let {
            handler.removeCallbacks(it)
        }
    }

    private fun updateProgressBroadcast() {
        mediaPlayer?.let {
            val intent = Intent(ACTION_UPDATE_PROGRESS).apply {
                putExtra(EXTRA_CURRENT_POSITION, it.currentPosition)
                putExtra(EXTRA_DURATION, it.duration)
                putExtra(EXTRA_IS_SHUFFLE, isShuffleEnabled)
                putExtra(EXTRA_IS_REPEAT_ONE, isRepeatOneEnabled)
            }
            sendBroadcast(intent)
        }
    }

    private fun updateUI(isPlaying: Boolean) {
        val intent = Intent(ACTION_UPDATE_UI).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_SONG_INDEX, currentIndex)
            putExtra(EXTRA_IS_SHUFFLE, isShuffleEnabled)
            putExtra(EXTRA_IS_REPEAT_ONE, isRepeatOneEnabled)
            // Add song title to the broadcast
            if (currentIndex >= 0 && currentIndex < songList.size) {
                putExtra(EXTRA_SONG_TITLE, songList[currentIndex].title)
            }
        }
        sendBroadcast(intent)
        Log.d("MusicService", "Sent broadcast: isPlaying=$isPlaying, shuffle=$isShuffleEnabled, repeat=$isRepeatOneEnabled")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music Player Controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(isPlaying: Boolean) {
        val title = songList.getOrNull(currentIndex)?.title ?: "Playing Music"

        // Create intent to open the app
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for Previous action
        val prevIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            prevIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for Play/Pause action
        val toggleIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_TOGGLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this,
            2,
            toggleIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for Next action
        val nextIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for Shuffle action
        val shuffleIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_SHUFFLE
        }
        val shufflePendingIntent = PendingIntent.getService(
            this,
            4,
            shuffleIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for Repeat action
        val repeatIntent = Intent(this, MusicService::class.java).apply {
            action = ACTION_REPEAT_ONE
        }
        val repeatPendingIntent = PendingIntent.getService(
            this,
            5,
            repeatIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Player")
            .setContentText(title)
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Add notification action buttons
            .addAction(
                android.R.drawable.ic_media_previous,
                "Previous",
                prevPendingIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                togglePendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_next,
                "Next",
                nextPendingIntent
            )
            .addAction(
                if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle,
                "Shuffle",
                shufflePendingIntent
            )
            .addAction(
                if (isRepeatOneEnabled) R.drawable.ic_repeat_one_on else R.drawable.ic_repeat_one_off,
                "Repeat",
                repeatPendingIntent
            )
            .build()

        // Only post notification if permission granted (Android 13+)
        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (canPost) {
            startForeground(NOTIFICATION_ID, notification)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }
}