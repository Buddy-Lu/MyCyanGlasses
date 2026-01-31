package com.buddy.cyanglasses

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.buddy.cyanglasses.databinding.ActivityDeviceScanBinding
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.scan.BleScannerHelper
import com.oudmon.ble.base.scan.ScanRecord
import com.oudmon.ble.base.scan.ScanWrapperCallback
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class DeviceScanActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DeviceScanActivity"
        private const val SCAN_TIMEOUT = 15000L // 15 seconds
    }

    private lateinit var binding: ActivityDeviceScanBinding
    private val deviceList = mutableListOf<ScannedDevice>()
    private lateinit var adapter: DeviceAdapter
    private val handler = Handler(Looper.getMainLooper())

    private val stopScanRunnable = Runnable {
        stopScanning()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        EventBus.getDefault().register(this)

        setupViews()
        requestPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        handler.removeCallbacks(stopScanRunnable)
        BleScannerHelper.getInstance().stopScan(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBluetoothEvent(event: BluetoothEvent) {
        Log.i(TAG, "BluetoothEvent received: connect=${event.connect}")

        if (event.connect) {
            // Connection complete and SDK is ready
            binding.progressBar.visibility = View.GONE
            binding.tvStatus.text = "Connected and ready!"
            Toast.makeText(this, "Connected to ${GlassesConnectionState.deviceName}!", Toast.LENGTH_SHORT).show()

            setResult(RESULT_OK)
            finish()
        } else {
            binding.progressBar.visibility = View.GONE
            binding.tvStatus.text = "Connection failed or disconnected"
        }
    }

    private fun setupViews() {
        adapter = DeviceAdapter(deviceList) { device ->
            connectToDevice(device)
        }

        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = adapter

        binding.btnScan.setOnClickListener {
            startScanning()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Permission.BLUETOOTH_SCAN)
            permissions.add(Permission.BLUETOOTH_CONNECT)
        }
        permissions.add(Permission.ACCESS_FINE_LOCATION)
        permissions.add(Permission.ACCESS_COARSE_LOCATION)

        XXPermissions.with(this)
            .permission(permissions)
            .request(object : OnPermissionCallback {
                override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                    if (all) {
                        Log.i(TAG, "All permissions granted")
                        checkBluetoothEnabled()
                    }
                }

                override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                    Toast.makeText(this@DeviceScanActivity,
                        "Bluetooth permissions required to scan for glasses",
                        Toast.LENGTH_LONG).show()
                    if (never) {
                        XXPermissions.startPermissionActivity(this@DeviceScanActivity, permissions)
                    }
                }
            })
    }

    private fun checkBluetoothEnabled() {
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(intent, 100)
                }
            } else {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(intent, 100)
            }
        }
    }

    private fun startScanning() {
        deviceList.clear()
        adapter.notifyDataSetChanged()

        binding.btnScan.isEnabled = false
        binding.btnScan.text = "Scanning..."
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "Scanning for HeyCyan glasses..."

        BleScannerHelper.getInstance().reSetCallback()
        BleScannerHelper.getInstance().scanDevice(this, null, scanCallback)

        handler.postDelayed(stopScanRunnable, SCAN_TIMEOUT)
    }

    private fun stopScanning() {
        BleScannerHelper.getInstance().stopScan(this)
        binding.btnScan.isEnabled = true
        binding.btnScan.text = "Scan for Devices"
        binding.progressBar.visibility = View.GONE
        binding.tvStatus.text = "Found ${deviceList.size} device(s)"
        handler.removeCallbacks(stopScanRunnable)
    }

    private val scanCallback = object : ScanWrapperCallback {
        override fun onStart() {
            Log.i(TAG, "Scan started")
        }

        override fun onStop() {
            Log.i(TAG, "Scan stopped")
        }

        override fun onLeScan(device: BluetoothDevice?, rssi: Int, scanRecord: ByteArray?) {
            if (device != null && !device.name.isNullOrEmpty()) {
                val scannedDevice = ScannedDevice(
                    name = device.name ?: "Unknown",
                    address = device.address,
                    rssi = rssi
                )

                if (!deviceList.any { it.address == scannedDevice.address }) {
                    Log.i(TAG, "Found device: ${device.name} - ${device.address}")
                    runOnUiThread {
                        deviceList.add(scannedDevice)
                        deviceList.sortByDescending { it.rssi }
                        adapter.notifyDataSetChanged()
                        binding.tvStatus.text = "Found ${deviceList.size} device(s)"
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error: $errorCode")
            runOnUiThread {
                Toast.makeText(this@DeviceScanActivity,
                    "Scan failed: $errorCode", Toast.LENGTH_SHORT).show()
                stopScanning()
            }
        }

        override fun onParsedData(device: BluetoothDevice?, scanRecord: ScanRecord?) {}
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {}
    }

    private fun connectToDevice(device: ScannedDevice) {
        stopScanning()

        binding.tvStatus.text = "Connecting to ${device.name}..."
        binding.progressBar.visibility = View.VISIBLE

        // Store device info
        GlassesConnectionState.deviceAddress = device.address
        GlassesConnectionState.deviceName = device.name
        DeviceManager.getInstance().deviceAddress = device.address

        Log.i(TAG, "Connecting to ${device.name} at ${device.address}")

        // Connect - the EventBus will notify us when connection is complete
        BleOperateManager.getInstance().connectDirectly(device.address)

        // Timeout for connection
        handler.postDelayed({
            if (!GlassesConnectionState.isReady) {
                binding.progressBar.visibility = View.GONE
                binding.tvStatus.text = "Connection timeout. Try again."
                Toast.makeText(this, "Connection timeout", Toast.LENGTH_SHORT).show()
            }
        }, 15000)
    }
}

data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

class DeviceAdapter(
    private val devices: List<ScannedDevice>,
    private val onClick: (ScannedDevice) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(android.R.id.text1)
        val tvAddress: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        holder.tvName.text = "${device.name} (${device.rssi} dBm)"
        holder.tvAddress.text = device.address
        holder.itemView.setOnClickListener { onClick(device) }
    }

    override fun getItemCount() = devices.size
}
