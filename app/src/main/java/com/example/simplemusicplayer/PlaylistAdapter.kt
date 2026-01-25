package com.example.simplemusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistAdapter(
    private val playlists: List<Playlist>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.PlaylistViewHolder>() {

    class PlaylistViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.playlistName)
        val songCount: TextView = itemView.findViewById(R.id.songCount)
        val playlistIcon: ImageView = itemView.findViewById(R.id.playlistIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist, parent, false)
        return PlaylistViewHolder(view)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val playlist = playlists[position]
        holder.name.text = playlist.name
        holder.songCount.text = "${playlist.songUris.size} songs"

        holder.itemView.setOnClickListener {
            onClick(playlist.id)
        }
    }

    override fun getItemCount() = playlists.size
}