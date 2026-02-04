package com.buddy.cyanglasses

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoAutoUploader {
    private const val TAG = "PhotoAutoUploader"
    private var lastUploadTime = 0L
    private var isUploading = false
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    interface UploadListener {
        fun onUploadStarted(filename: String)
        fun onUploadProgress(filename: String, message: String)
        fun onUploadSuccess(filename: String, responseBody: String?)
        fun onUploadFailed(filename: String, error: String)
    }

    fun onPhotoTaken(context: Context, listener: UploadListener?) {
        if (isUploading) {
            Log.w(TAG, "Upload already in progress, skipping")
            return
        }

        // Check if endpoint is configured
        if (!PhotoUploadManager.isEndpointConfigured(context)) {
            Log.w(TAG, "API endpoint not configured, skipping upload")
            listener?.onUploadFailed("none", "Backend API not configured. Go to Settings.")
            return
        }

        Log.d(TAG, "Photo taken, looking for newest file to upload...")

        // Find the newest photo in DCIM directories
        val newestPhoto = findNewestPhoto(context)

        if (newestPhoto == null) {
            Log.w(TAG, "No photos found in DCIM directories")
            listener?.onUploadFailed("none", "No photo found to upload")
            return
        }

        // Check if this file was modified after last upload (to avoid re-uploading)
        if (newestPhoto.lastModified() <= lastUploadTime) {
            Log.d(TAG, "Newest photo is not new (lastModified: ${newestPhoto.lastModified()}, lastUpload: $lastUploadTime)")
            listener?.onUploadFailed(newestPhoto.name, "No new photo to upload")
            return
        }

        uploadPhoto(context, newestPhoto, listener)
    }

    fun uploadSpecificPhoto(context: Context, photoFile: File, listener: UploadListener?) {
        if (!photoFile.exists()) {
            Log.e(TAG, "Photo file does not exist: ${photoFile.absolutePath}")
            listener?.onUploadFailed(photoFile.name, "File not found")
            return
        }

        uploadPhoto(context, photoFile, listener)
    }

    private fun uploadPhoto(context: Context, photoFile: File, listener: UploadListener?) {
        isUploading = true
        val filename = photoFile.name

        Log.d(TAG, "Starting upload: $filename (${photoFile.length()} bytes)")
        listener?.onUploadStarted(filename)
        listener?.onUploadProgress(filename, "Uploading...")

        PhotoUploadManager.uploadPhoto(context, photoFile) { success, error, responseBody ->
            isUploading = false

            if (success) {
                Log.d(TAG, "Upload successful: $filename")
                lastUploadTime = System.currentTimeMillis()
                listener?.onUploadSuccess(filename, responseBody)
            } else {
                Log.e(TAG, "Upload failed: $filename - $error")
                listener?.onUploadFailed(filename, error ?: "Unknown error")
            }
        }
    }

    private fun findNewestPhoto(context: Context): File? {
        val directories = listOf(
            File(context.getExternalFilesDir(null), "DCIM"),
            File(context.getExternalFilesDir(null), "DCIM_1"),
            context.getExternalFilesDir("DCIM")
        )

        var newestPhoto: File? = null
        var newestTime = 0L

        for (dir in directories) {
            if (dir == null || !dir.exists() || !dir.isDirectory) continue

            dir.listFiles()?.forEach { file ->
                if (file.isFile && isImageFile(file)) {
                    if (file.lastModified() > newestTime) {
                        newestTime = file.lastModified()
                        newestPhoto = file
                    }
                }
            }
        }

        if (newestPhoto != null) {
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(newestPhoto!!.lastModified()))
            Log.d(TAG, "Newest photo found: ${newestPhoto!!.name} (modified: $timeStr)")
        }

        return newestPhoto
    }

    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("jpg", "jpeg", "png")
    }

    fun getLastUploadTime(): Long {
        return lastUploadTime
    }

    fun isUploading(): Boolean {
        return isUploading
    }

    fun resetLastUploadTime() {
        lastUploadTime = 0L
        Log.d(TAG, "Last upload time reset")
    }
}
