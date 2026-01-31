package com.buddy.cyanglasses

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.oudmon.ble.base.bluetooth.BleAction
import com.oudmon.ble.base.bluetooth.BleBaseControl
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackCloneReceiver
import com.oudmon.ble.base.communication.LargeDataHandler
import org.greenrobot.eventbus.EventBus

class MyApplication : Application() {

    companion object {
        private const val TAG = "MyApplication"
        lateinit var instance: MyApplication
            private set
        lateinit var CONTEXT: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        CONTEXT = applicationContext
        initBle()
        Log.i(TAG, "HeyCyan Glasses SDK initialized")
    }

    private fun initBle() {
        // Initialize SDK components (order matters!)
        LargeDataHandler.getInstance()
        BleOperateManager.getInstance(this)
        BleOperateManager.getInstance().setApplication(this)
        BleOperateManager.getInstance().init()

        // Register the SDK's callback receiver for BLE events
        val intentFilter = BleAction.getIntentFilter()
        val myBleReceiver = MyBluetoothReceiver()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(myBleReceiver, intentFilter)

        // Register system broadcast receiver for Bluetooth state changes
        val deviceFilter = BleAction.getDeviceIntentFilter()
        val deviceReceiver = BluetoothStateReceiver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(deviceReceiver, deviceFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(deviceReceiver, deviceFilter)
        }

        BleBaseControl.getInstance(this).setmContext(this)
        Log.i(TAG, "BLE initialization complete")
    }
}

// Global state object
object GlassesConnectionState {
    var isConnected: Boolean = false
    var isReady: Boolean = false  // True when initEnable() has been called
    var deviceAddress: String? = null
    var deviceName: String? = null
}

// Connection event for EventBus
data class BluetoothEvent(val connect: Boolean)

/**
 * This receiver handles the SDK's BLE callbacks.
 * CRITICAL: initEnable() must be called in onServiceDiscovered() before commands work!
 */
class MyBluetoothReceiver : QCBluetoothCallbackCloneReceiver() {

    companion object {
        private const val TAG = "MyBluetoothReceiver"
    }

    override fun connectStatue(device: BluetoothDevice?, connected: Boolean) {
        Log.i(TAG, "connectStatue: device=${device?.name}, connected=$connected")

        if (device != null && connected) {
            if (device.name != null) {
                DeviceManager.getInstance().deviceName = device.name
                GlassesConnectionState.deviceName = device.name
            }
            GlassesConnectionState.deviceAddress = device.address
            GlassesConnectionState.isConnected = true
        } else {
            GlassesConnectionState.isConnected = false
            GlassesConnectionState.isReady = false
            EventBus.getDefault().post(BluetoothEvent(false))
        }
    }

    override fun onServiceDiscovered() {
        Log.i(TAG, "onServiceDiscovered - INITIALIZING SDK COMMUNICATION")

        // THIS IS CRITICAL! Must call initEnable() before any commands will work
        LargeDataHandler.getInstance().initEnable()

        BleOperateManager.getInstance().isReady = true
        GlassesConnectionState.isReady = true

        Log.i(TAG, "initEnable() called - SDK is now ready for commands")

        // Notify the app that connection is complete
        EventBus.getDefault().post(BluetoothEvent(true))
    }

    override fun onCharacteristicChange(address: String?, uuid: String?, data: ByteArray?) {
        Log.d(TAG, "onCharacteristicChange: uuid=$uuid, dataSize=${data?.size}")
    }

    override fun onCharacteristicRead(uuid: String?, data: ByteArray?) {
        Log.d(TAG, "onCharacteristicRead: uuid=$uuid, dataSize=${data?.size}")
    }
}

/**
 * Handles system Bluetooth state changes
 */
class BluetoothStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        Log.i(TAG, "Bluetooth turned OFF")
                        BleOperateManager.getInstance().setBluetoothTurnOff(false)
                        BleOperateManager.getInstance().disconnect()
                        GlassesConnectionState.isConnected = false
                        GlassesConnectionState.isReady = false
                        EventBus.getDefault().post(BluetoothEvent(false))
                    }
                    BluetoothAdapter.STATE_ON -> {
                        Log.i(TAG, "Bluetooth turned ON")
                        BleOperateManager.getInstance().setBluetoothTurnOff(true)
                        // Auto-reconnect if we had a previous connection
                        val lastAddress = DeviceManager.getInstance().deviceAddress
                        if (!lastAddress.isNullOrEmpty()) {
                            Log.i(TAG, "Attempting auto-reconnect to $lastAddress")
                            BleOperateManager.getInstance().reConnectMac = lastAddress
                            BleOperateManager.getInstance().connectDirectly(lastAddress)
                        }
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Log.i(TAG, "ACL Connected")
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Log.i(TAG, "ACL Disconnected")
                GlassesConnectionState.isConnected = false
                GlassesConnectionState.isReady = false
            }
            BluetoothDevice.ACTION_FOUND -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device != null) {
                    BleOperateManager.getInstance().createBondBluetoothJieLi(device)
                }
            }
        }
    }
}
