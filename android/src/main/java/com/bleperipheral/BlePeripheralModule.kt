package com.bleperipheral

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import java.util.*

class BlePeripheralModule(reactContext: ReactApplicationContext) : 
    ReactContextBaseJavaModule(reactContext) {
    
    private val context = reactContext
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) 
        as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private val characteristics = mutableMapOf<String, BluetoothGattCharacteristic>()
    private var currentService: BluetoothGattService? = null
    
    override fun getName(): String = "BlePeripheral"
    
    @ReactMethod
    fun startAdvertising(
        serviceUUID: String,
        deviceName: String,
        promise: Promise
    ) {
        try {
            // Check if Bluetooth is enabled
            if (!bluetoothAdapter.isEnabled) {
                promise.reject("BLUETOOTH_OFF", "Bluetooth is not enabled")
                return
            }
            
            advertiser = bluetoothAdapter.bluetoothLeAdvertiser
            
            if (advertiser == null) {
                promise.reject("ADVERTISER_ERROR", "BLE Advertising not supported")
                return
            }
            
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()
            
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(ParcelUuid(UUID.fromString(serviceUUID)))
                .build()
            
            advertiser?.startAdvertising(settings, data, advertiseCallback)
            
            // Setup GATT server
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            
            // Create service
            currentService = BluetoothGattService(
                UUID.fromString(serviceUUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            
            val response = Arguments.createMap()
            response.putString("status", "advertising")
            response.putString("deviceName", deviceName)
            promise.resolve(response)
            
        } catch (e: SecurityException) {
            promise.reject("PERMISSION_ERROR", "Missing Bluetooth permissions: ${e.message}")
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }
    
    @ReactMethod
    fun stopAdvertising(promise: Promise) {
        try {
            advertiser?.stopAdvertising(advertiseCallback)
            gattServer?.clearServices()
            gattServer?.close()
            characteristics.clear()
            
            val response = Arguments.createMap()
            response.putString("status", "stopped")
            promise.resolve(response)
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }
    
    @ReactMethod
    fun addCharacteristic(
        serviceUUID: String,
        characteristicUUID: String,
        properties: ReadableArray,
        permissions: ReadableArray,
        promise: Promise
    ) {
        try {
            var props = 0
            var perms = 0
            
            // Parse properties
            for (i in 0 until properties.size()) {
                when (properties.getString(i)) {
                    "read" -> props = props or BluetoothGattCharacteristic.PROPERTY_READ
                    "write" -> props = props or BluetoothGattCharacteristic.PROPERTY_WRITE
                    "writeWithoutResponse" -> props = props or 
                        BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                    "notify" -> props = props or BluetoothGattCharacteristic.PROPERTY_NOTIFY
                    "indicate" -> props = props or BluetoothGattCharacteristic.PROPERTY_INDICATE
                }
            }
            
            // Parse permissions
            for (i in 0 until permissions.size()) {
                when (permissions.getString(i)) {
                    "readable" -> perms = perms or BluetoothGattCharacteristic.PERMISSION_READ
                    "writeable" -> perms = perms or BluetoothGattCharacteristic.PERMISSION_WRITE
                }
            }
            
            val characteristic = BluetoothGattCharacteristic(
                UUID.fromString(characteristicUUID),
                props,
                perms
            )
            
            characteristics[characteristicUUID] = characteristic
            
            // Add to service
            currentService?.addCharacteristic(characteristic)
            
            // Add service to GATT server if not already added
            if (gattServer?.getService(UUID.fromString(serviceUUID)) == null) {
                currentService?.let { gattServer?.addService(it) }
            }
            
            val response = Arguments.createMap()
            response.putString("status", "added")
            response.putString("uuid", characteristicUUID)
            promise.resolve(response)
            
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }
    
    @ReactMethod
    fun updateCharacteristicValue(
        characteristicUUID: String,
        value: String,
        promise: Promise
    ) {
        try {
            val characteristic = characteristics[characteristicUUID]
            if (characteristic != null) {
                characteristic.value = value.toByteArray()
                
                // Notify all connected devices
                val connectedDevices = bluetoothManager.getConnectedDevices(
                    BluetoothProfile.GATT
                )
                
                for (device in connectedDevices) {
                    gattServer?.notifyCharacteristicChanged(
                        device,
                        characteristic,
                        false
                    )
                }
                
                val response = Arguments.createMap()
                response.putBoolean("success", true)
                promise.resolve(response)
            } else {
                promise.reject("NOT_FOUND", "Characteristic not found")
            }
        } catch (e: Exception) {
            promise.reject("ERROR", e.message)
        }
    }
    
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            sendEvent("BlePeripheralAdvertisingStarted", Arguments.createMap())
        }
        
        override fun onStartFailure(errorCode: Int) {
            val params = Arguments.createMap()
            params.putInt("errorCode", errorCode)
            params.putString("error", getAdvertiseErrorString(errorCode))
            sendEvent("BlePeripheralError", params)
        }
        
        private fun getAdvertiseErrorString(errorCode: Int): String {
            return when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                else -> "Unknown error: $errorCode"
            }
        }
    }
    
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        
        override fun onConnectionStateChange(
            device: BluetoothDevice?,
            status: Int,
            newState: Int
        ) {
            val params = Arguments.createMap()
            params.putString("deviceAddress", device?.address)
            params.putInt("status", status)
            params.putString("state", if (newState == BluetoothProfile.STATE_CONNECTED) 
                "connected" else "disconnected")
            sendEvent("BlePeripheralConnectionStateChanged", params)
        }
        
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (value != null && characteristic != null) {
                val stringValue = String(value)
                
                val params = Arguments.createMap()
                params.putString("characteristicUUID", characteristic.uuid.toString())
                params.putString("value", stringValue)
                params.putString("deviceAddress", device?.address)
                sendEvent("BlePeripheralWriteReceived", params)
            }
            
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }
        
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                characteristic?.value
            )
        }
        
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
            
            // Handle subscription changes (for notifications)
            if (descriptor?.uuid == UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")) {
                val subscribed = value?.contentEquals(
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) ?: false
                
                val params = Arguments.createMap()
                params.putString("characteristicUUID", descriptor?.characteristic?.uuid?.toString())
                params.putString("deviceAddress", device?.address)
                
                if (subscribed) {
                    sendEvent("BlePeripheralSubscribed", params)
                } else {
                    sendEvent("BlePeripheralUnsubscribed", params)
                }
            }
        }
        
        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            val params = Arguments.createMap()
            if (status == BluetoothGatt.GATT_SUCCESS) {
                params.putString("serviceUUID", service?.uuid?.toString())
                sendEvent("BlePeripheralServiceAdded", params)
            } else {
                params.putString("error", "Failed to add service")
                sendEvent("BlePeripheralError", params)
            }
        }
    }
    
    private fun sendEvent(eventName: String, params: WritableMap) {
        context
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }
}
