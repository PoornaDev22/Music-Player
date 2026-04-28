package com.example.simplemusicplayer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class OnlineSongsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var btnShuffleAll: Button

    private val db = FirebaseFirestore.getInstance()
    private val songList = mutableListOf<Song>()
    private lateinit var playlistManager: PlaylistManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_online_songs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.onlineRecyclerView)
        progressBar = view.findViewById(R.id.onlineProgressBar)
        emptyText = view.findViewById(R.id.onlineEmptyText)
        btnShuffleAll = view.findViewById(R.id.btnOnlineShuffleAll)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        playlistManager = PlaylistManager(requireContext())

        // Shuffle all online songs — same behaviour as All Audio shuffle
        btnShuffleAll.setOnClickListener {
            if (songList.isNotEmpty()) {
                val randomIndex = (0 until songList.size).random()
                ContextCompat.startForegroundService(
                    requireContext(),
                    Intent(requireContext(), MusicService::class.java).apply {
                        action = MusicService.ACTION_START_SHUFFLED
                        putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
                        putExtra(MusicService.EXTRA_SONG_INDEX, randomIndex)
                    }
                )
            }
        }

        loadOnlineSongs()
    }

    private fun loadOnlineSongs() {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        db.collection("songs")
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                songList.clear()

                for (document in documents) {
                    val onlineSong = document.toObject(OnlineSong::class.java)
                    if (onlineSong.url.isNotEmpty()) {
                        songList.add(Song(title = onlineSong.title, uri = onlineSong.url))
                    }
                }

                if (songList.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    recyclerView.adapter = SongAdapterWithMenu(
                        songs = songList,
                        onSongClick = { position -> playSong(position) },
                        onMenuClick = { position -> showSongOptions(position) }
                    )
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Failed to load: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun playSong(index: Int) {
        if (index < 0 || index >= songList.size) return
        ContextCompat.startForegroundService(
            requireContext(),
            Intent(requireContext(), MusicService::class.java).apply {
                action = MusicService.ACTION_START
                putParcelableArrayListExtra(MusicService.EXTRA_SONG_LIST, ArrayList(songList))
                putExtra(MusicService.EXTRA_SONG_INDEX, index)
            }
        )
    }

    private fun showSongOptions(position: Int) {
        if (position < 0 || position >= songList.size) return
        val song = songList[position]
        AlertDialog.Builder(requireContext())
            .setTitle("Song Options")
            .setItems(arrayOf("Add to Playlist", "Play on Repeat")) { _, which ->
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_playlist, null)
        val playlistListView = dialogView.findViewById<ListView>(R.id.playlistListView)
        val newPlaylistEditText = dialogView.findViewById<EditText>(R.id.newPlaylistEditText)
        val adapter = ArrayAdapter(requireContext(), R.layout.dialog_playlist_item, playlistNames)
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
        AlertDialog.Builder(requireContext())
            .setTitle("Add to Playlist")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                if (selectedPlaylistId != -1) {
                    playlistManager.addSongToPlaylist(selectedPlaylistId, song.uri, song.title)
                    Toast.makeText(requireContext(), "Added to: ${playlistManager.getPlaylist(selectedPlaylistId)?.name}", Toast.LENGTH_SHORT).show()
                } else if (newPlaylistEditText.text.toString().isNotBlank()) {
                    val name = newPlaylistEditText.text.toString()
                    val newPlaylist = playlistManager.createPlaylist(name)
                    playlistManager.addSongToPlaylist(newPlaylist.id, song.uri, song.title)
                    Toast.makeText(requireContext(), "Added to new playlist: $name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun playSongOnRepeat(position: Int) {
        playSong(position)
        requireContext().startService(Intent(requireContext(), MusicService::class.java).apply {
            action = MusicService.ACTION_REPEAT_ONE
        })
        Toast.makeText(requireContext(), "Playing on repeat", Toast.LENGTH_SHORT).show()
    }
}