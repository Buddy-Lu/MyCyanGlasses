package com.buddy.cyanglasses

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.buddy.cyanglasses.databinding.ActivityMainBinding
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private var isRecordingVideo = false
    private var isRecordingAudio = false
    private val logBuilder = StringBuilder()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private val scanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            updateConnectionUI()
            appendLog("Returned from scan - isConnected: ${GlassesConnectionState.isConnected}, isReady: ${GlassesConnectionState.isReady}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EventBus.getDefault().register(this)

        appendLog("App started")
        setupUI()
        setupDeviceListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }

    override fun onResume() {
        super.onResume()
        updateConnectionUI()
        updateUploadStatusUI()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBluetoothEvent(event: BluetoothEvent) {
        appendLog("BluetoothEvent: connect=${event.connect}, isReady=${GlassesConnectionState.isReady}")
        updateConnectionUI()
    }

    private fun appendLog(message: String) {
        val timestamp = timeFormat.format(Date())
        val logLine = "[$timestamp] $message\n"

        Log.d(TAG, message)

        runOnUiThread {
            logBuilder.append(logLine)
            binding.tvDebugLog.text = logBuilder.toString()

            // Auto-scroll to bottom
            binding.scrollLog.post {
                binding.scrollLog.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    private fun setupUI() {
        // Connection buttons
        binding.btnScanConnect.setOnClickListener {
            appendLog("Opening scan activity...")
            scanLauncher.launch(Intent(this, DeviceScanActivity::class.java))
        }

        binding.btnDisconnect.setOnClickListener {
            appendLog("Disconnecting...")
            disconnectGlasses()
        }

        // Control buttons
        binding.btnTakePhoto.setOnClickListener {
            appendLog("Take Photo button pressed")
            takePhoto()
        }

        binding.btnStartVideo.setOnClickListener {
            appendLog("Video button pressed (current state: recording=$isRecordingVideo)")
            toggleVideoRecording()
        }

        binding.btnStartAudio.setOnClickListener {
            appendLog("Audio button pressed (current state: recording=$isRecordingAudio)")
            toggleAudioRecording()
        }

        binding.btnSyncTime.setOnClickListener {
            appendLog("Sync Time button pressed")
            syncTime()
        }

        binding.btnGetBattery.setOnClickListener {
            appendLog("Get Battery button pressed")
            getBattery()
        }

        binding.btnGetVersion.setOnClickListener {
            appendLog("Get Version button pressed")
            getVersion()
        }

        binding.btnGetMediaCount.setOnClickListener {
            appendLog("Get Media Count button pressed")
            getMediaCount()
        }

        // Log control buttons
        binding.btnClearLog.setOnClickListener {
            logBuilder.clear()
            binding.tvDebugLog.text = "Log cleared.\n"
        }

        binding.btnCopyLog.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Debug Log", logBuilder.toString())
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Log copied to clipboard!", Toast.LENGTH_SHORT).show()
        }

        // Gallery buttons
        binding.btnGallery.setOnClickListener {
            appendLog("Opening gallery...")
            startActivity(Intent(this, GalleryActivity::class.java))
        }

        binding.btnDownloadMedia.setOnClickListener {
            appendLog("Download Media button pressed")
            downloadMedia()
        }

        binding.btnSettings.setOnClickListener {
            appendLog("Opening settings...")
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupDeviceListener() {
        appendLog("Setting up device listener...")

        try {
            LargeDataHandler.getInstance().addOutDeviceListener(100, object : GlassesDeviceNotifyListener() {
                override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
                    appendLog("NOTIFY: cmdType=$cmdType, dataSize=${response.loadData?.size ?: 0}")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        handleDeviceNotification(response)
                    }
                }
            })
            appendLog("Device listener registered")
        } catch (e: Exception) {
            appendLog("ERROR setting up listener: ${e.message}")
        }

        // Add battery callback
        try {
            LargeDataHandler.getInstance().addBatteryCallBack("main") { cmdType, response ->
                appendLog("BATTERY callback: cmdType=$cmdType, response=$response")
                runOnUiThread {
                    binding.tvStatus.text = "Battery: $response%"
                }
            }
            appendLog("Battery callback registered")
        } catch (e: Exception) {
            appendLog("ERROR setting up battery callback: ${e.message}")
        }
    }

    private fun handleDeviceNotification(response: GlassesDeviceNotifyRsp) {
        try {
            val data = response.loadData
            if (data != null && data.size > 6) {
                val notifyType = data[6].toInt() and 0xFF
                appendLog("Notification type: 0x${notifyType.toString(16)}")

                when (notifyType) {
                    0x01 -> { // Photo captured notification
                        appendLog("Photo captured notification received!")
                        triggerPhotoUpload()
                    }
                    0x05 -> { // Battery update
                        if (data.size > 8) {
                            val battery = data[7].toInt() and 0xFF
                            val charging = (data[8].toInt() and 0xFF) == 1
                            appendLog("Battery update: $battery%, charging=$charging")
                            runOnUiThread {
                                val chargingText = if (charging) " (Charging)" else ""
                                binding.tvStatus.text = "Battery: $battery%$chargingText"
                            }
                        }
                    }
                    0x02 -> {
                        appendLog("Quick recognition triggered from glasses")
                    }
                    0x03 -> {
                        if (data.size > 7 && data[7].toInt() == 1) {
                            appendLog("Glasses microphone started")
                        }
                    }
                    else -> {
                        appendLog("Unknown notification type: 0x${notifyType.toString(16)}")
                    }
                }
            }
        } catch (e: Exception) {
            appendLog("ERROR parsing notification: ${e.message}")
        }
    }

    private fun updateConnectionUI() {
        val isConnected = GlassesConnectionState.isConnected
        val isReady = GlassesConnectionState.isReady

        // Only enable controls if SDK is fully ready (initEnable called)
        binding.btnDisconnect.isEnabled = isConnected
        binding.btnTakePhoto.isEnabled = isReady
        binding.btnStartVideo.isEnabled = isReady
        binding.btnStartAudio.isEnabled = isReady
        binding.btnSyncTime.isEnabled = isReady
        binding.btnGetBattery.isEnabled = isReady
        binding.btnGetVersion.isEnabled = isReady
        binding.btnGetMediaCount.isEnabled = isReady
        binding.btnDownloadMedia.isEnabled = isReady

        if (isReady) {
            binding.tvConnectionStatus.text = "Ready: ${GlassesConnectionState.deviceName ?: "Unknown"}"
            binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            appendLog("UI updated: READY (connected=${isConnected}, ready=${isReady})")
        } else if (isConnected) {
            binding.tvConnectionStatus.text = "Connected (initializing...)"
            binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            appendLog("UI updated: CONNECTED but not ready yet")
        } else {
            binding.tvConnectionStatus.text = "Not Connected"
            binding.tvConnectionStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            appendLog("UI updated: NOT CONNECTED")
        }
    }

    private fun disconnectGlasses() {
        try {
            BleOperateManager.getInstance().unBindDevice()
            GlassesConnectionState.isConnected = false
            GlassesConnectionState.deviceAddress = null
            GlassesConnectionState.deviceName = null
            updateConnectionUI()
            appendLog("Disconnect command sent")
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            appendLog("ERROR disconnecting: ${e.message}")
        }
    }

    private fun takePhoto() {
        binding.tvStatus.text = "Taking photo..."
        appendLog("Sending photo command: [0x02, 0x01, 0x01]")

        try {
            LargeDataHandler.getInstance().glassesControl(
                byteArrayOf(0x02, 0x01, 0x01)
            ) { cmdType, response ->
                appendLog("PHOTO RESPONSE: cmdType=$cmdType")
                appendLog("  dataType=${response.dataType}")
                appendLog("  errorCode=${response.errorCode}")
                appendLog("  workTypeIng=${response.workTypeIng}")
                appendLog("  imageCount=${response.imageCount}")
                appendLog("  videoCount=${response.videoCount}")
                appendLog("  recordCount=${response.recordCount}")

                runOnUiThread {
                    val statusMsg = "Photo: type=${response.dataType}, err=${response.errorCode}, mode=${response.workTypeIng}"
                    binding.tvStatus.text = statusMsg

                    if (response.dataType == 1 && response.errorCode == 0) {
                        Toast.makeText(this, "Photo command sent!", Toast.LENGTH_SHORT).show()

                        // Trigger auto-upload after photo is taken (fallback if notification doesn't work)
                        // Delay to give the photo time to be saved
                        binding.root.postDelayed({
                            triggerPhotoUpload()
                        }, 2000)
                    }
                }
            }
            appendLog("Photo command dispatched")
        } catch (e: Exception) {
            appendLog("ERROR sending photo command: ${e.message}")
            binding.tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun toggleVideoRecording() {
        isRecordingVideo = !isRecordingVideo
        val command: Byte = if (isRecordingVideo) 0x02 else 0x03
        val cmdName = if (isRecordingVideo) "START" else "STOP"

        binding.tvStatus.text = "${cmdName}ing video..."
        appendLog("Sending video $cmdName command: [0x02, 0x01, 0x${command.toString(16)}]")

        try {
            LargeDataHandler.getInstance().glassesControl(
                byteArrayOf(0x02, 0x01, command)
            ) { cmdType, response ->
                appendLog("VIDEO RESPONSE: cmdType=$cmdType")
                appendLog("  dataType=${response.dataType}")
                appendLog("  errorCode=${response.errorCode}")
                appendLog("  workTypeIng=${response.workTypeIng}")

                runOnUiThread {
                    binding.tvStatus.text = "Video: type=${response.dataType}, err=${response.errorCode}, mode=${response.workTypeIng}"

                    if (response.dataType == 1 && response.errorCode == 0) {
                        if (isRecordingVideo) {
                            binding.btnStartVideo.text = "Stop Video"
                        } else {
                            binding.btnStartVideo.text = "Start Video"
                        }
                    } else {
                        isRecordingVideo = !isRecordingVideo // Revert
                    }
                }
            }
            appendLog("Video command dispatched")
        } catch (e: Exception) {
            appendLog("ERROR sending video command: ${e.message}")
            isRecordingVideo = !isRecordingVideo // Revert
            binding.tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun toggleAudioRecording() {
        isRecordingAudio = !isRecordingAudio
        val command: Byte = if (isRecordingAudio) 0x08 else 0x0c
        val cmdName = if (isRecordingAudio) "START" else "STOP"

        binding.tvStatus.text = "${cmdName}ing audio..."
        appendLog("Sending audio $cmdName command: [0x02, 0x01, 0x${command.toString(16)}]")

        try {
            LargeDataHandler.getInstance().glassesControl(
                byteArrayOf(0x02, 0x01, command)
            ) { cmdType, response ->
                appendLog("AUDIO RESPONSE: cmdType=$cmdType")
                appendLog("  dataType=${response.dataType}")
                appendLog("  errorCode=${response.errorCode}")
                appendLog("  workTypeIng=${response.workTypeIng}")

                runOnUiThread {
                    binding.tvStatus.text = "Audio: type=${response.dataType}, err=${response.errorCode}, mode=${response.workTypeIng}"

                    if (response.dataType == 1 && response.errorCode == 0) {
                        if (isRecordingAudio) {
                            binding.btnStartAudio.text = "Stop Audio"
                        } else {
                            binding.btnStartAudio.text = "Start Audio"
                        }
                    } else {
                        isRecordingAudio = !isRecordingAudio // Revert
                    }
                }
            }
            appendLog("Audio command dispatched")
        } catch (e: Exception) {
            appendLog("ERROR sending audio command: ${e.message}")
            isRecordingAudio = !isRecordingAudio // Revert
            binding.tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun syncTime() {
        binding.tvStatus.text = "Syncing time..."
        appendLog("Sending sync time command...")

        try {
            LargeDataHandler.getInstance().syncTime { cmdType, response ->
                appendLog("TIME SYNC RESPONSE: cmdType=$cmdType, response=$response")

                runOnUiThread {
                    binding.tvStatus.text = "Time synced!"
                    Toast.makeText(this, "Time synchronized", Toast.LENGTH_SHORT).show()
                }
            }
            appendLog("Sync time command dispatched")
        } catch (e: Exception) {
            appendLog("ERROR syncing time: ${e.message}")
            binding.tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun getBattery() {
        binding.tvStatus.text = "Getting battery..."
        appendLog("Requesting battery status...")

        try {
            LargeDataHandler.getInstance().syncBattery()
            appendLog("Battery request dispatched")
        } catch (e: Exception) {
            appendLog("ERROR getting battery: ${e.message}")
            binding.tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun getVersion() {
        binding.tvStatus.text = "Getting version..."
        appendLog("Requesting device version info...")

        try {
            LargeDataHandler.getInstance().syncDeviceInfo { cmdType, response ->
                appendLog("VERSION RESPONSE: cmdType=$cmdType")

                if (response != null) {
                    appendLog("  hardwareVersion=${response.hardwareVersion}")
                    appendLog("  firmwareVersion=${response.firmwareVersion}")
                    appendLog("  wifiHardwareVersion=${response.wifiHardwareVersion}")
                    appendLog("  wifiFirmwareVersion=${response.wifiFirmwareVersion}")

                    runOnUiThread {
                        val info = "HW:${response.hardwareVersion} FW:${response.firmwareVersion}"
                        binding.tvStatus.text = info
                        Toast.makeText(this, "Version info retrieved", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    appendLog("  response is NULL")
                    runOnUiThread {
                        binding.tvStatus.text = "Version: null response"
                    }
                }
            }
            appendLog("Version request dispatched")
        } catch (e: Exception) {
            appendLog("ERROR getting version: ${e.message}")
            binding.tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun getMediaCount() {
        binding.tvStatus.text = "Getting media count..."
        appendLog("Requesting media count: [0x02, 0x04]")

        try {
            LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x04)) { cmdType, response ->
                appendLog("MEDIA COUNT RESPONSE: cmdType=$cmdType")
                appendLog("  dataType=${response.dataType}")
                appendLog("  errorCode=${response.errorCode}")
                appendLog("  imageCount=${response.imageCount}")
                appendLog("  videoCount=${response.videoCount}")
                appendLog("  recordCount=${response.recordCount}")

                runOnUiThread {
                    val info = "Photos:${response.imageCount} Videos:${response.videoCount} Audio:${response.recordCount}"
                    binding.tvStatus.text = info
                }
            }
            appendLog("Media count request dispatched")
        } catch (e: Exception) {
            appendLog("ERROR getting media count: ${e.message}")
            binding.tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun downloadMedia() {
        appendLog("Download Media button pressed")

        // First, get the media count to show what's available
        try {
            LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x04)) { cmdType, response ->
                appendLog("Media check: photos=${response.imageCount}, videos=${response.videoCount}, audio=${response.recordCount}")

                val totalFiles = response.imageCount + response.videoCount + response.recordCount

                runOnUiThread {
                    if (totalFiles > 0) {
                        binding.tvStatus.text = "Found: ${response.imageCount} photos, ${response.videoCount} videos, ${response.recordCount} audio"
                        showDownloadInstructions(response.imageCount, response.videoCount, response.recordCount)
                    } else {
                        binding.tvStatus.text = "No media files on glasses"
                        Toast.makeText(this, "No files found on glasses", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            appendLog("ERROR checking media: ${e.message}")
            binding.tvStatus.text = "Error: ${e.message}"
        }
    }

    private fun showDownloadInstructions(photos: Int, videos: Int, audio: Int) {
        val message = """
            Found on glasses:
            • Photos: $photos
            • Videos: $videos
            • Audio: $audio

            To download files:
            1. Connect glasses to WiFi hotspot
            2. Use the HeyCyan app or copy files via USB when connected to computer

            Downloaded files will appear in the Gallery.
        """.trimIndent()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Media Files")
            .setMessage(message)
            .setPositiveButton("Open Gallery") { _, _ ->
                startActivity(Intent(this, GalleryActivity::class.java))
            }
            .setNegativeButton("OK", null)
            .show()
    }

    private fun triggerPhotoUpload() {
        appendLog("Triggering auto-upload...")

        PhotoAutoUploader.onPhotoTaken(this, object : PhotoAutoUploader.UploadListener {
            override fun onUploadStarted(filename: String) {
                appendLog("Upload started: $filename")
                runOnUiThread {
                    binding.tvUploadStatus.text = "Uploading: $filename"
                }
            }

            override fun onUploadProgress(filename: String, message: String) {
                appendLog("Upload progress: $filename - $message")
                runOnUiThread {
                    binding.tvUploadStatus.text = "Uploading: $filename - $message"
                }
            }

            override fun onUploadSuccess(filename: String, responseBody: String?) {
                appendLog("Upload SUCCESS: $filename")
                appendLog("Response: ${responseBody?.take(200)}")
                runOnUiThread {
                    binding.tvUploadStatus.text = "✓ Uploaded: $filename"
                    Toast.makeText(this@MainActivity, "Photo uploaded successfully!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onUploadFailed(filename: String, error: String) {
                appendLog("Upload FAILED: $filename - $error")
                runOnUiThread {
                    binding.tvUploadStatus.text = "✗ Upload failed: $error"
                    if (error.contains("not configured")) {
                        Toast.makeText(this@MainActivity, "Configure backend in Settings first", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun updateUploadStatusUI() {
        val isConfigured = PhotoUploadManager.isEndpointConfigured(this)
        val endpoint = PhotoUploadManager.getApiEndpoint(this)

        runOnUiThread {
            if (isConfigured) {
                binding.tvUploadStatus.text = "Auto-upload: Enabled\nEndpoint: ${endpoint.take(40)}..."
                binding.tvUploadStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                binding.tvUploadStatus.text = "Auto-upload: Not configured (using default test endpoint)"
                binding.tvUploadStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
        }
    }
}


