package com.buddy.cyanglasses

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buddy.cyanglasses.databinding.ActivityGalleryBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val mediaFiles = mutableListOf<MediaFile>()
    private lateinit var adapter: MediaAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        loadMediaFiles()
    }

    override fun onResume() {
        super.onResume()
        loadMediaFiles()
    }

    private fun setupViews() {
        adapter = MediaAdapter(mediaFiles) { mediaFile ->
            openMediaFile(mediaFile)
        }

        binding.recyclerMedia.layoutManager = GridLayoutManager(this, 3)
        binding.recyclerMedia.adapter = adapter

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnRefresh.setOnClickListener {
            loadMediaFiles()
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
        }

        binding.btnDeleteAll.setOnClickListener {
            deleteAllMedia()
        }
    }

    private fun loadMediaFiles() {
        mediaFiles.clear()

        // Check multiple possible directories
        val directories = listOf(
            File(getExternalFilesDir(null), "DCIM"),
            File(getExternalFilesDir(null), "DCIM_1"),
            getExternalFilesDir("DCIM")
        )

        for (dir in directories) {
            if (dir != null && dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile) {
                        val extension = file.extension.lowercase()
                        when (extension) {
                            "jpg", "jpeg", "png" -> {
                                mediaFiles.add(MediaFile(file, MediaType.IMAGE))
                            }
                            "mp4", "3gp", "mov" -> {
                                mediaFiles.add(MediaFile(file, MediaType.VIDEO))
                            }
                            "mp3", "wav", "aac", "m4a" -> {
                                mediaFiles.add(MediaFile(file, MediaType.AUDIO))
                            }
                        }
                    }
                }
            }
        }

        // Sort by date (newest first)
        mediaFiles.sortByDescending { it.file.lastModified() }

        adapter.notifyDataSetChanged()

        // Update status
        binding.tvStatus.text = if (mediaFiles.isEmpty()) {
            "No media files found.\nDownload media from your glasses first."
        } else {
            "${mediaFiles.size} file(s) found"
        }
    }

    private fun openMediaFile(mediaFile: MediaFile) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                mediaFile.file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                when (mediaFile.type) {
                    MediaType.IMAGE -> setDataAndType(uri, "image/*")
                    MediaType.VIDEO -> setDataAndType(uri, "video/*")
                    MediaType.AUDIO -> setDataAndType(uri, "audio/*")
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAllMedia() {
        if (mediaFiles.isEmpty()) {
            Toast.makeText(this, "No files to delete", Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete All Media")
            .setMessage("Are you sure you want to delete all ${mediaFiles.size} downloaded files?")
            .setPositiveButton("Delete") { _, _ ->
                var deleted = 0
                mediaFiles.forEach { mediaFile ->
                    if (mediaFile.file.delete()) {
                        deleted++
                    }
                }
                Toast.makeText(this, "Deleted $deleted files", Toast.LENGTH_SHORT).show()
                loadMediaFiles()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

data class MediaFile(
    val file: File,
    val type: MediaType
)

enum class MediaType {
    IMAGE, VIDEO, AUDIO
}

class MediaAdapter(
    private val mediaFiles: List<MediaFile>,
    private val onClick: (MediaFile) -> Unit
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imagePreview)
        val tvName: TextView = view.findViewById(R.id.tvFileName)
        val tvType: TextView = view.findViewById(R.id.tvFileType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val mediaFile = mediaFiles[position]
        val file = mediaFile.file

        holder.tvName.text = file.name
        holder.tvType.text = when (mediaFile.type) {
            MediaType.IMAGE -> "Photo"
            MediaType.VIDEO -> "Video"
            MediaType.AUDIO -> "Audio"
        }

        // Set icon/preview based on type
        when (mediaFile.type) {
            MediaType.IMAGE -> {
                // Load image thumbnail
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                    holder.imageView.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    holder.imageView.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            }
            MediaType.VIDEO -> {
                holder.imageView.setImageResource(android.R.drawable.ic_media_play)
            }
            MediaType.AUDIO -> {
                holder.imageView.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            }
        }

        holder.itemView.setOnClickListener { onClick(mediaFile) }
    }

    override fun getItemCount() = mediaFiles.size
}
