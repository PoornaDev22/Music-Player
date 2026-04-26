package com.example.simplemusicplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()

    // currentIndex is always a POSITION in the current play order.
    // When shuffle is OFF: play order is [0,1,...,n-1], so position == song index.
    // When shuffle is ON:  play order is shuffleOrder[], so songIndex = shuffleOrder[position].
    private var currentIndex = 0

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var progressUpdateRunnable: Runnable? = null

    private var isShuffleEnabled = false
    private var isRepeatOneEnabled = false
    private var shuffleOrder: MutableList<Int> = mutableListOf()

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
                val songIndex = intent.getIntExtra(EXTRA_SONG_INDEX, 0)
                if (songs != null) {
                    songList = songs
                    // Build the play order, respecting whatever shuffle state is already set.
                    // This fixes the bug where initializePlayOrder() always reset to sequential.
                    initializePlayOrder(songIndex)
                    playSong(currentIndex)
                }
            }
            ACTION_TOGGLE -> {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.pause()
                        stopProgressUpdates()
                        updateUI(false)
                        updateNotification(false)
                    } else {
                        it.start()
                        startProgressUpdates()
                        updateUI(true)
                        updateNotification(true)
                    }
                }
            }
            ACTION_NEXT -> {
                if (songList.isNotEmpty()) playNextSong()
            }
            ACTION_PREVIOUS -> {
                if (songList.isNotEmpty()) playPreviousSong()
            }
            ACTION_SHUFFLE -> {
                isShuffleEnabled = !isShuffleEnabled
                if (isShuffleEnabled) {
                    // currentIndex is currently a plain song index (shuffle was off).
                    // Build a new random order with the current song pinned to position 0.
                    buildShuffleOrderFrom(songIndex = currentIndex)
                    currentIndex = 0
                } else {
                    // currentIndex is a position in shuffleOrder.
                    // Convert back to the actual song index so sequential play stays coherent.
                    currentIndex = shuffleOrder[currentIndex]
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
                mediaPlayer?.let {
                    updateUI(it.isPlaying)
                    updateProgressBroadcast()
                }
            }
        }
        return START_STICKY
    }

    // Returns the actual song index for the current playback position.
    // This is the single source of truth used by playSong, updateUI, and updateNotification.
    private fun getActualSongIndex(): Int {
        return if (isShuffleEnabled) {
            shuffleOrder.getOrElse(currentIndex) { 0 }
        } else {
            currentIndex
        }
    }

    // Sets up the play order for a new song list.
    // If shuffle is OFF: currentIndex = songIndex (position == song index).
    // If shuffle is ON:  build a randomized order, pin songIndex at position 0,
    //                    so currentIndex = 0 and the right song plays first.
    private fun initializePlayOrder(songIndex: Int) {
        if (isShuffleEnabled) {
            buildShuffleOrderFrom(songIndex)
            currentIndex = 0
        } else {
            // Sequential — position and song index are the same thing.
            currentIndex = songIndex
        }
    }

    // Builds a fully randomized shuffleOrder with `songIndex` pinned at position 0.
    private fun buildShuffleOrderFrom(songIndex: Int) {
        shuffleOrder = songList.indices.toMutableList()
        shuffleOrder.remove(songIndex)   // pull the current song out
        shuffleOrder.shuffle()           // randomize the rest
        shuffleOrder.add(0, songIndex)   // put the current song first
    }

    // Plays the song at the given POSITION in the current play order.
    private fun playSong(position: Int) {
        if (songList.isEmpty()) return

        currentIndex = position
        val actualIndex = getActualSongIndex()

        if (actualIndex < 0 || actualIndex >= songList.size) return

        mediaPlayer?.release()
        val song = songList[actualIndex]

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MusicService, Uri.parse(song.uri))
                prepare()
                start()
                setOnCompletionListener {
                    if (isRepeatOneEnabled) {
                        playSong(currentIndex)
                    } else {
                        playNextSong()
                    }
                }
            }

            Log.d("MusicService", "Playing [pos=$currentIndex, song=$actualIndex]: ${song.title} | shuffle=$isShuffleEnabled repeat=$isRepeatOneEnabled")
            updateUI(true)
            updateNotification(true)
            startProgressUpdates()
        } catch (e: Exception) {
            Log.e("MusicService", "Error playing song", e)
            if (!isRepeatOneEnabled) playNextSong()
        }
    }

    private fun playNextSong() {
        if (songList.isEmpty()) return
        val nextPosition = (currentIndex + 1) % songList.size
        playSong(nextPosition)
    }

    private fun playPreviousSong() {
        if (songList.isEmpty()) return
        val prevPosition = if (currentIndex - 1 < 0) songList.size - 1 else currentIndex - 1
        playSong(prevPosition)
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressUpdateRunnable = object : Runnable {
            override fun run() {
                updateProgressBroadcast()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(progressUpdateRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressUpdateRunnable?.let { handler.removeCallbacks(it) }
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
        val actualIndex = getActualSongIndex()
        val intent = Intent(ACTION_UPDATE_UI).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_SONG_INDEX, currentIndex)
            putExtra(EXTRA_IS_SHUFFLE, isShuffleEnabled)
            putExtra(EXTRA_IS_REPEAT_ONE, isRepeatOneEnabled)
            // Use the real song index for the title, not the position index.
            if (actualIndex >= 0 && actualIndex < songList.size) {
                putExtra(EXTRA_SONG_TITLE, songList[actualIndex].title)
            }
        }
        sendBroadcast(intent)
        Log.d("MusicService", "updateUI: isPlaying=$isPlaying shuffle=$isShuffleEnabled repeat=$isRepeatOneEnabled")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Music Player Controls" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(isPlaying: Boolean) {
        // Use the real song index, not the play-order position.
        val actualIndex = getActualSongIndex()
        val title = songList.getOrNull(actualIndex)?.title ?: "Playing Music"

        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        fun servicePendingIntent(requestCode: Int, action: String) =
            PendingIntent.getService(
                this, requestCode,
                Intent(this, MusicService::class.java).apply { this.action = action },
                PendingIntent.FLAG_IMMUTABLE
            )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Player")
            .setContentText(title)
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_previous, "Previous", servicePendingIntent(1, ACTION_PREVIOUS))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                servicePendingIntent(2, ACTION_TOGGLE)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", servicePendingIntent(3, ACTION_NEXT))
            .addAction(
                if (isShuffleEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle,
                "Shuffle",
                servicePendingIntent(4, ACTION_SHUFFLE)
            )
            .addAction(
                if (isRepeatOneEnabled) R.drawable.ic_repeat_one_on else R.drawable.ic_repeat_one_off,
                "Repeat",
                servicePendingIntent(5, ACTION_REPEAT_ONE)
            )
            .build()

        val canPost = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        if (canPost) startForeground(NOTIFICATION_ID, notification)
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