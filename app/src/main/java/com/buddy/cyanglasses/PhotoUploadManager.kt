package com.buddy.cyanglasses

import android.content.Context
import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object PhotoUploadManager {
    private const val TAG = "PhotoUploadManager"
    private const val PREFS_NAME = "UploadSettings"
    private const val KEY_API_ENDPOINT = "api_endpoint"
    private const val KEY_API_KEY = "api_key"
    private const val DEFAULT_ENDPOINT = "https://httpbin.org/post"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getApiEndpoint(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_ENDPOINT, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
    }

    fun setApiEndpoint(context: Context, endpoint: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_ENDPOINT, endpoint).apply()
        Log.d(TAG, "API endpoint set to: $endpoint")
    }

    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_API_KEY, null)
    }

    fun setApiKey(context: Context, apiKey: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (apiKey.isNullOrBlank()) {
            prefs.edit().remove(KEY_API_KEY).apply()
        } else {
            prefs.edit().putString(KEY_API_KEY, apiKey).apply()
        }
        Log.d(TAG, "API key ${if (apiKey.isNullOrBlank()) "cleared" else "set"}")
    }

    fun uploadPhoto(
        context: Context,
        file: File,
        callback: (success: Boolean, error: String?, responseBody: String?) -> Unit
    ) {
        val endpoint = getApiEndpoint(context)
        val apiKey = getApiKey(context)

        if (!file.exists() || !file.canRead()) {
            Log.e(TAG, "File does not exist or cannot be read: ${file.absolutePath}")
            callback(false, "File not found or not readable", null)
            return
        }

        Log.d(TAG, "Uploading photo: ${file.name} (${file.length()} bytes) to $endpoint")

        try {
            // Build multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "photo",
                    file.name,
                    file.asRequestBody("image/jpeg".toMediaType())
                )
                .addFormDataPart("timestamp", System.currentTimeMillis().toString())
                .addFormDataPart("filename", file.name)
                .build()

            // Build request with optional API key
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(requestBody)

            if (!apiKey.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                Log.d(TAG, "Added Authorization header")
            }

            val request = requestBuilder.build()

            // Execute async
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Upload failed: ${e.message}", e)
                    callback(false, "Network error: ${e.message}", null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()

                    if (response.isSuccessful) {
                        Log.d(TAG, "Upload successful! Response code: ${response.code}")
                        Log.d(TAG, "Response: ${responseBody?.take(200)}")
                        callback(true, null, responseBody)
                    } else {
                        Log.e(TAG, "Upload failed with code: ${response.code}")
                        Log.e(TAG, "Response: $responseBody")
                        callback(false, "Server error: ${response.code} ${response.message}", responseBody)
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Error creating upload request: ${e.message}", e)
            callback(false, "Error: ${e.message}", null)
        }
    }

    fun isEndpointConfigured(context: Context): Boolean {
        val endpoint = getApiEndpoint(context)
        return endpoint != DEFAULT_ENDPOINT && endpoint.isNotBlank()
    }

    fun validateEndpoint(endpoint: String): Boolean {
        if (endpoint.isBlank()) return false

        return try {
            val url = java.net.URL(endpoint)
            url.protocol in listOf("http", "https")
        } catch (e: Exception) {
            false
        }
    }
}
