package com.reactnativebt

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanRecord
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler

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
import okhttp3.internal.and
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.UUID

class BtModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext), PermissionListener{

  // REACT METHODS

  @ReactMethod
  fun addListener(type: String?) {
  }

  @ReactMethod
  fun removeListeners(type: Int?) {
  }

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
        if(!scanning){
          try{
            Handler().postDelayed(Runnable {
              scanning = false
              bluetoothLEScanner?.stopScan(leScanCallback)
              promise.resolve(leDevices)
            }, 10000)
            scanning = true
            bluetoothLEScanner?.startScan(leScanCallback)
          }
          catch(e: Exception){
            promise.reject("Discovery Error", e.message)
          }
        }
        else {
          scanning = false
          bluetoothLEScanner?.stopScan(leScanCallback)
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

  @ReactMethod
  fun disconnectBT() {
    close();
  }

  /**
   * Connect to the Device by MAC Address
   */
  @ReactMethod
  fun discoverCharacteristicBT(id: String, promise: Promise) {
    mPromise = promise;
    if (bluetoothAdapter != null) {
      if(bluetoothAdapter.isEnabled == true){
        if(connectedDevice != null && connectedDevice!!.address == id){
          bluetoothGatt?.discoverServices();
        }
        else{
          promise.reject("Connection Error", "No device connected")
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
  fun readCharacteristicBT(service: String, characteristic: String, promise: Promise) {
    mPromise = promise;
    if (bluetoothAdapter != null) {
      if(bluetoothAdapter.isEnabled == true){
        if(connectedDevice != null){
          val bleService : BluetoothGattService? = bluetoothGatt?.getService(UUID.fromString(service))
          val bleCharacteristic : BluetoothGattCharacteristic? = bleService?.getCharacteristic(UUID.fromString(characteristic))
          bluetoothGatt?.readCharacteristic(bleCharacteristic)
        }
        else{
          promise.reject("Connection Error", "No device connected")
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
  fun notifyCharacteristicBT(service: String, characteristic: String, promise: Promise) {
    mPromise = promise;
    if (bluetoothAdapter != null) {
      if(bluetoothAdapter.isEnabled == true){
        if(connectedDevice != null){
          val bleService : BluetoothGattService? = bluetoothGatt?.getService(UUID.fromString(service))
          val bleCharacteristic : BluetoothGattCharacteristic? = bleService?.getCharacteristic(UUID.fromString(characteristic))
          bluetoothGatt?.setCharacteristicNotification(bleCharacteristic, true)
          mPromise?.resolve("Listening " + characteristic)
        }
        else{
          promise.reject("Connection Error", "No device connected")
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

  // INNER METHODS

  /**
   * Bytes to string
   */
  private fun bytesToHex(bytes: ByteArray?): String? {
    val hexArray = "0123456789ABCDEF".toCharArray()
    val hexChars = CharArray(bytes?.size!!.times(2))
    for (j in bytes?.indices!!) {
      val v: Int = bytes!![j] and 0xFF
      hexChars[j * 2] = hexArray.get(v ushr 4)
      hexChars[j * 2 + 1] = hexArray.get(v and 0x0F)
    }
    return String(hexChars)
  }

  /**
   * Transform Bluetooth Device into JS JSON
   */
  private fun mapDevice(device: BluetoothDevice?, record: ScanRecord?): WritableMap {
    val params: WritableMap = Arguments.createMap()
    val dataString: String? = Base64.getEncoder().encodeToString(record?.bytes)
    val dataHex: String? = bytesToHex(record?.bytes)
    if (device != null) {
      params.putString("deviceName", device?.name)
      params.putString("deviceAddress", device?.address)
      if(dataString != null && dataHex != null){
        params.putString("deviceData", dataString)
        params.putString("deviceDataHex", dataHex)
        params.putString("deviceBytes", record?.manufacturerSpecificData.toString())
      }
      if (device?.bluetoothClass != null) {
        params.putString("deviceBytes", record?.manufacturerSpecificData.toString())
      }
    }
    return params
  }

  /**
   * Transform Device Services to JS JSON
   */
  private fun mapServices(services:List<BluetoothGattService>?) {
    val servicesMap: WritableArray = Arguments.createArray()
    services?.forEach { service ->
      var serviceInfo: WritableMap = Arguments.createMap()
      serviceInfo.putString("uuid", service.uuid.toString())

      val gattCharacteristics = service.characteristics
      var characteristicInfo: WritableArray = Arguments.createArray()
      gattCharacteristics.forEach { gattCharacteristic ->
        characteristicInfo.pushString(gattCharacteristic.uuid.toString())
      }
      serviceInfo.putArray("characteristics", characteristicInfo);
      servicesMap.pushMap(serviceInfo);
    }
    mPromise?.resolve(servicesMap)
  }

  /**
   * Transform Device Services to JS JSON
   */
  private fun mapCharacteristic(characteristic: BluetoothGattCharacteristic) {
    val servicesMap: WritableMap = Arguments.createMap()
    val data : ByteArray? = characteristic.value
    if(data?.isNotEmpty() == true){
      val hexString: String = data.joinToString(separator = " ") {
        String.format("%02X", it)
      }
      mPromise?.resolve(hexString)
    }
    mPromise?.resolve(servicesMap)
  }

  private fun sendEvent(eventName: String, params: WritableMap?) {
    context
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit(eventName, params)
  }

  /**
   * Init connect Thread
   */
  fun connect(address: String) {
    bluetoothAdapter?.let { adapter ->
      try {
        connectedDevice = adapter.getRemoteDevice(address)
        bluetoothGatt = connectedDevice?.connectGatt(context, false, bluetoothGattCallback)
        //return true
      } catch (exception: IllegalArgumentException) {
        mPromise?.reject("Connection Error", exception.message)
        //return false
      }
    } ?: run {
      mPromise?.reject("Connection Error", "Cann't connect to the Device")
      //return false
    }
  }

  /**
   * Disconnect BT
   */
  fun close(){
    bluetoothGatt?.let { gatt ->
        gatt.close()
        bluetoothGatt = null
        mPromise = null
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
      val record: ScanRecord? = result.scanRecord
      if (rawDevice != null && rawDevice?.bondState != BluetoothDevice.BOND_BONDED) {
        val device: WritableMap = mapDevice(rawDevice, record)
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
        mPromise?.resolve(true);
      } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        close()
        val params: WritableMap = Arguments.createMap();
        params.putBoolean("connected", false);
        sendEvent("status", params)
      }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        mapServices(bluetoothGatt?.services)
      } else {
        mPromise?.reject("No characteristics found")
      }
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
      if (status == BluetoothGatt.GATT_SUCCESS) {
        mapCharacteristic(characteristic)
      }
    }

    override fun onCharacteristicChanged( gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
      val params: WritableMap = Arguments.createMap();
      val data: ByteArray? = characteristic.value
      if (data?.isNotEmpty() == true) {
          val hexString: String = data.joinToString(separator = " ") {
              String.format("%02X", it)
          }
          params.putString("data", hexString)
          sendEvent("reading", params)
      }
    }
  }

  // BASE METHODS OVERRIDE
  /**
   * Its necessary
   */
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
    return true
  }

  /**
   * Its necessary
   */
  override fun getName(): String {
    return "Bt"
  }

  // CONSTRUCTOR AND ATRIBUTES
  private val bluetoothManager: BluetoothManager
  private val bluetoothAdapter: BluetoothAdapter?
  private val bluetoothLEScanner : BluetoothLeScanner?
  private val context: ReactContext
  private var connectedDevice:  BluetoothDevice? = null
  private var mPromise: Promise? = null
  private var bluetoothGatt: BluetoothGatt? = null
  private var leDevices: WritableArray = Arguments.createArray()
  private var scanning = false

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
