package com.example.simplemusicplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Rename the existing SongAdapter to SongAdapterBasic
class SongAdapterBasic(
    private val songs: List<Song>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<SongAdapterBasic.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.songTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.title.text = songs[position].title
        holder.itemView.setOnClickListener {
            onClick(position)
        }
    }

    override fun getItemCount() = songs.size
}

// New adapter with menu button
class SongAdapterWithMenu(
    private val songs: List<Song>,
    private val onSongClick: (Int) -> Unit,
    private val onMenuClick: (Int) -> Unit
) : RecyclerView.Adapter<SongAdapterWithMenu.SongViewHolder>() {

    class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.songTitle)
        val menuButton: ImageButton = itemView.findViewById(R.id.btnMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song_with_menu, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.title.text = songs[position].title
        holder.itemView.setOnClickListener {
            onSongClick(position)
        }
        holder.menuButton.setOnClickListener {
            onMenuClick(position)
        }
    }

    override fun getItemCount() = songs.size
}