package com.reactnativebt

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent

import com.facebook.react.bridge.*
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

class BtModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private val activity: Activity? = currentActivity
    private val bluetoothManager: BluetoothManager = currentActivity.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    override fun getName(): String {
        return "Bt"
    }

    // Example method
    // See https://reactnative.dev/docs/native-modules-android
    @ReactMethod
    fun multiply(a: Int, b: Int, promise: Promise) {
        promise.resolve(a * b)
    }
    
    @ReactMethod
    fun enableBT(promise: Promise){
      if(bluetoothAdapter != null){
        if(bluetoothAdapter.isEnabled){
          val btIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
          activity?.startActivityForResult(btIntent, REQUEST_ENABLE_BT)
        }
      }
      else{
        promise.reject("Error", "Error")
      }
    }

    companion object {
        const val REQUEST_ENABLE_BT: Int = 1
    }
}
