package com.example.simplemusicplayer

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import androidx.fragment.app.DialogFragment

class PlaylistDialogFragment : DialogFragment() {
    private lateinit var playlistManager: PlaylistManager
    private lateinit var allPlaylists: List<Playlist>
    private var selectedPlaylistId: Int = -1
    private var songUri: String = ""
    private var songTitle: String = ""

    interface PlaylistDialogListener {
        fun onPlaylistCreated(name: String, songUri: String, songTitle: String)
        fun onSongAddedToPlaylist(playlistId: Int, songUri: String, songTitle: String)
    }

    private var listener: PlaylistDialogListener? = null

    companion object {
        private const val ARG_SONG_URI = "song_uri"
        private const val ARG_SONG_TITLE = "song_title"

        fun newInstance(songUri: String, songTitle: String): PlaylistDialogFragment {
            val fragment = PlaylistDialogFragment()
            val args = Bundle()
            args.putString(ARG_SONG_URI, songUri)
            args.putString(ARG_SONG_TITLE, songTitle)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playlistManager = PlaylistManager(requireContext())
        allPlaylists = playlistManager.getAllPlaylists()

        arguments?.let {
            songUri = it.getString(ARG_SONG_URI) ?: ""
            songTitle = it.getString(ARG_SONG_TITLE) ?: ""
        }

        try {
            listener = parentFragment as PlaylistDialogListener
        } catch (e: ClassCastException) {
            throw ClassCastException("Calling fragment must implement PlaylistDialogListener")
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_playlist, null)

        val playlistListView = view.findViewById<ListView>(R.id.playlistListView)
        val newPlaylistEditText = view.findViewById<EditText>(R.id.newPlaylistEditText)

        val playlistNames = allPlaylists.map { it.name }.toMutableList()
        playlistNames.add("Create new playlist...")

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, playlistNames)
        playlistListView.adapter = adapter

        playlistListView.setOnItemClickListener { _, _, position, _ ->
            if (position == allPlaylists.size) {
                // "Create new playlist..." selected
                newPlaylistEditText.visibility = View.VISIBLE
                newPlaylistEditText.requestFocus()
                selectedPlaylistId = -1
            } else {
                // Existing playlist selected
                selectedPlaylistId = allPlaylists[position].id
                newPlaylistEditText.visibility = View.GONE
            }
        }

        return AlertDialog.Builder(requireContext())
            .setTitle("Add to Playlist")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                if (selectedPlaylistId != -1) {
                    // Add to existing playlist
                    listener?.onSongAddedToPlaylist(selectedPlaylistId, songUri, songTitle)
                } else if (newPlaylistEditText.text.toString().isNotBlank()) {
                    // Create new playlist and add song
                    val playlistName = newPlaylistEditText.text.toString()
                    listener?.onPlaylistCreated(playlistName, songUri, songTitle)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
}