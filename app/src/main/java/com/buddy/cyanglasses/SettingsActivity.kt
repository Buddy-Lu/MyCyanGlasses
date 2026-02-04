package com.buddy.cyanglasses

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.buddy.cyanglasses.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()
        setupUI()
    }

    private fun loadSettings() {
        val endpoint = PhotoUploadManager.getApiEndpoint(this)
        val apiKey = PhotoUploadManager.getApiKey(this)

        binding.etApiEndpoint.setText(endpoint)
        binding.etApiKey.setText(apiKey ?: "")
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnTest.setOnClickListener {
            testConnection()
        }

        binding.btnReset.setOnClickListener {
            resetToDefaults()
        }
    }

    private fun saveSettings() {
        val endpoint = binding.etApiEndpoint.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim()

        // Validate endpoint
        if (endpoint.isBlank()) {
            Toast.makeText(this, "API endpoint cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (!PhotoUploadManager.validateEndpoint(endpoint)) {
            Toast.makeText(this, "Invalid URL format. Must start with http:// or https://", Toast.LENGTH_LONG).show()
            return
        }

        // Save settings
        PhotoUploadManager.setApiEndpoint(this, endpoint)
        PhotoUploadManager.setApiKey(this, apiKey.ifBlank { null })

        Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
        binding.tvStatus.text = "Settings saved successfully"
    }

    private fun testConnection() {
        val endpoint = binding.etApiEndpoint.text.toString().trim()

        if (!PhotoUploadManager.validateEndpoint(endpoint)) {
            Toast.makeText(this, "Invalid URL format", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvStatus.text = "Testing connection..."
        binding.btnTest.isEnabled = false

        // Create a temporary test file
        val testFile = java.io.File(cacheDir, "test_image.jpg")
        try {
            // Create a minimal JPEG file (1x1 pixel)
            testFile.writeBytes(
                byteArrayOf(
                    0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                    0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                    0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                    0xFF.toByte(), 0xD9.toByte()
                )
            )

            PhotoUploadManager.uploadPhoto(this, testFile) { success, error, _ ->
                runOnUiThread {
                    binding.btnTest.isEnabled = true
                    testFile.delete()

                    if (success) {
                        binding.tvStatus.text = "✓ Connection successful!"
                        Toast.makeText(this, "Connection test passed!", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.tvStatus.text = "✗ Connection failed: $error"
                        Toast.makeText(this, "Test failed: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            binding.btnTest.isEnabled = true
            binding.tvStatus.text = "✗ Error: ${e.message}"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetToDefaults() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reset Settings")
            .setMessage("Reset to default httpbin.org test endpoint?")
            .setPositiveButton("Reset") { _, _ ->
                PhotoUploadManager.setApiEndpoint(this, "https://httpbin.org/post")
                PhotoUploadManager.setApiKey(this, null)
                loadSettings()
                binding.tvStatus.text = "Settings reset to defaults"
                Toast.makeText(this, "Settings reset", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
