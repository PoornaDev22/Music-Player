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

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var songList: List<Song> = emptyList()
    private var currentIndex = 0

    companion object {
        const val CHANNEL_ID = "MusicPlayerChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_TOGGLE = "ACTION_TOGGLE"
        const val ACTION_NEXT = "ACTION_NEXT"
        const val ACTION_PREVIOUS = "ACTION_PREVIOUS"
        const val ACTION_UPDATE_UI = "ACTION_UPDATE_UI"
        const val EXTRA_SONG_LIST = "EXTRA_SONG_LIST"
        const val EXTRA_SONG_INDEX = "EXTRA_SONG_INDEX"
        const val EXTRA_IS_PLAYING = "EXTRA_IS_PLAYING"
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
                    playSong(currentIndex)
                }
            }
            ACTION_TOGGLE -> {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        it.pause()
                        Log.d("MusicService", "Paused playback")
                        updateUI(false)
                        updateNotification(false)
                    } else {
                        it.start()
                        Log.d("MusicService", "Resumed playback")
                        updateUI(true)
                        updateNotification(true)
                    }
                }
            }
            ACTION_NEXT -> {
                if (songList.isNotEmpty()) {
                    playSong((currentIndex + 1) % songList.size)
                }
            }
            ACTION_PREVIOUS -> {
                if (songList.isNotEmpty()) {
                    playSong((currentIndex - 1 + songList.size) % songList.size)
                }
            }
        }
        return START_STICKY
    }

    private fun playSong(index: Int) {
        if (index < 0 || index >= songList.size) return
        currentIndex = index

        mediaPlayer?.release()
        val song = songList[index]

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@MusicService, Uri.parse(song.uri))
                prepare()
                start()
                setOnCompletionListener {
                    playSong((currentIndex + 1) % songList.size)
                }
            }

            Log.d("MusicService", "Started playing: ${song.title}")
            updateUI(true)
            updateNotification(true)
        } catch (e: Exception) {
            Log.e("MusicService", "Error playing song", e)
            e.printStackTrace()
        }
    }

    private fun updateUI(isPlaying: Boolean) {
        val intent = Intent(ACTION_UPDATE_UI).apply {
            putExtra(EXTRA_IS_PLAYING, isPlaying)
            putExtra(EXTRA_SONG_INDEX, currentIndex)
        }
        sendBroadcast(intent)
        Log.d("MusicService", "Sent broadcast: isPlaying=$isPlaying")
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
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
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
        mediaPlayer?.release()
        mediaPlayer = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }
}