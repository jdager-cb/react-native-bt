package com.toughbuilt.bluetooth

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler

import androidx.annotation.Nullable

import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableArray
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener

class BtModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), PermissionListener{

  // REACT METHODS

  /**
   * Check if Bluetooth is enabled
   */
  @ReactMethod
  fun checkBT(promise: Promise){
    if(bluetoothAdapter != null){
      promise.resolve(bluetoothAdapter.isEnabled)
    }
    else{
      promise.reject("Adapter Error", "Bluetooth Adapter not found")
    }
  }

  /**
   * Prompt user to enable Bluetooth
   */
  @ReactMethod
  fun enableBT(promise: Promise){
    val activity: Activity? = currentActivity
    if(bluetoothAdapter != null){
      if(!bluetoothAdapter.isEnabled){
        val btIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        activity?.startActivityForResult(btIntent, REQUEST_ENABLE_BT)
      }
      promise.resolve(bluetoothAdapter.isEnabled)
    }
    else{
      promise.reject("Adapter Error", "Bluetooth Adapter not found")
    }
  }

  /**
   * List avaible Bluetooth devices
   */
  @ReactMethod
  fun discoverBT(promise: Promise){
    mPromise = promise
    if (bluetoothAdapter != null) {
      if(bluetoothAdapter.isEnabled){
        val activity: PermissionAwareActivity = currentActivity as PermissionAwareActivity
        if (activity.checkSelfPermission(
            Manifest.permission.BLUETOOTH_SCAN
          ) != PackageManager.PERMISSION_GRANTED
        ) {

          activity.requestPermissions(
            arrayOf(Manifest.permission.BLUETOOTH_SCAN),
            1,
            this
          )
        }
        try{
          Handler().postDelayed(Runnable {
            bluetoothLEScanner?.stopScan(leScanCallback)
            promise.resolve(leDevices)
          }, 10000)
          bluetoothLEScanner?.startScan(leScanCallback)
        }
        catch(e: Exception){
          promise.reject("Discovery Error", e.message)
        }
      }
      else{
        promise.reject("Adapter Error", "Bluetooth Adapter is inactive")
      }
    }
    else{
      promise.reject("Adapter Error", "Bluetooth Adapter not found")
    }
  }

  /**
   * Connect to the Device by MAC Address
   */
  @ReactMethod
  fun connectBT(id: String, promise: Promise) {
    mPromise = promise;
    if (bluetoothAdapter != null) {
      if(bluetoothAdapter.isEnabled == true){
        connect(id)
      } 
      else{
        promise.reject("Adapter Error", "Bluetooth Adapter is inactive")
      }
    }
    else{
      promise.reject("Adapter Error", "Bluetooth Adapter not found")
    }
  }

  // INNER METHODS
  /**
   * Transform Bluetooth Device into JS JSON
  */
  private fun mapDevice(device: BluetoothDevice?): WritableMap {
    val params: WritableMap = Arguments.createMap()
    if (device != null) {
      params.putString("deviceName", device?.name)
      params.putString("deviceAddress", device?.address)
      if (device?.bluetoothClass != null) {
        params.putInt("deviceClass", device.bluetoothClass.deviceClass)
      }
    }
    return params
  }

  /**
   * Send an event to JS
   */
  private fun sendEvent(eventName: String, @Nullable params: WritableMap) {
    context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, params)
  }

  /**
   * Init connect Thread
   */
  fun connect(address: String): Boolean {
    bluetoothAdapter?.let { adapter ->
      try {
        val device = adapter.getRemoteDevice(address)
        bluetoothGatt = device?.connectGatt(context, false, bluetoothGattCallback)
        mPromise?.resolve(bluetoothGatt?.services)
        return true
      } catch (exception: IllegalArgumentException) {
        mPromise?.reject("Connection Error", exception.message)
        return false
      }
    } ?: run {
      mPromise?.reject("Connection Error", "Cann't connect to the Device")
      return false
    }
  }

  // EVENT LISTENERS
  /**
   * Handle Bluetooth Status
  */
  private val activityEventListener =
    object : BaseActivityEventListener() {
      override fun onActivityResult(activity: Activity?, requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_ENABLE_BT) {
          if (resultCode == Activity.RESULT_OK) {
          } else {
          }
        }
      }
    }

  /**
   * Handle Bluetooth Scan Event
   */
  private val leScanCallback: ScanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
      super.onScanResult(callbackType, result)
      val rawDevice: BluetoothDevice? = result.device
      if (rawDevice != null && rawDevice?.bondState != BluetoothDevice.BOND_BONDED) {
        val device: WritableMap = mapDevice(rawDevice)
        leDevices.pushMap(device)
      }
    }
  }

  /**
   * Handle Bluetooth Device Connection
   */
  private val bluetoothGattCallback = object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
      if (newState == BluetoothProfile.STATE_CONNECTED) {
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
      }
    }
  }

  // BASE METHODS OVERRIDE
  /**
   * Its necesary
  */
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
    return true
  }

  /**
   * Its necesary
   */
  override fun getName(): String {
    return "Bt"
  }

  // CONSTRUCTOR AND ATRIBUTES
  private val bluetoothManager: BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter?
  private val bluetoothLEScanner : BluetoothLeScanner?
  private val context: ReactContext
  private var mPromise: Promise? = null
  private var bluetoothGatt: BluetoothGatt? = null
  private val leDevices: WritableArray = Arguments.createArray()

  init {
    context = reactContext
    bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    bluetoothAdapter = bluetoothManager.adapter
    bluetoothLEScanner = bluetoothAdapter?.bluetoothLeScanner
    context.addActivityEventListener(activityEventListener)
  }

  companion object {
    const val REQUEST_ENABLE_BT: Int = 1
    const val SCAN_PERIOD: Long = 10000
  }
}
