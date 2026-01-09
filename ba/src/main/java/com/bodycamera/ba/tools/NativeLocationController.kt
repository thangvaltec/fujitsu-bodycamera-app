package com.bodycamera.ba.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import java.lang.ref.WeakReference


/**
 * Android Native Location SDK
 */
class NativeLocationController {
    interface IOnLocationChangedListener{
        fun onLocationChanged(location: Location)
    }
    companion object{
        const val TAG = "NativeLocationController"
    }
    private var locationManager :LocationManager?=null
    private var locationListener :LocationListener?=null
    private var hasStarted = false
    private var mLastLocation :Location? = null
    private var mListeners  = mutableListOf<IOnLocationChangedListener>()
    fun create(context:Context,listener: IOnLocationChangedListener):Boolean{
        KingStone.checkRunInApiThread()
        if(!hasStarted){
            mListeners.add(listener)
            return startInternal(context)
        }
        return true
    }

    fun release(){
        KingStone.checkRunInApiThread()
       if(hasStarted){
           mListeners.clear()
           stopInternal()
       }
    }

    fun getListeners() = mListeners

    fun setLastLocation(location: Location)  {
        mLastLocation = location
    }
    fun getLastLocation() = mLastLocation

    fun isRunning() = hasStarted

    fun addListener(listener: IOnLocationChangedListener){
        KingStone.checkRunInApiThread()
        if(!mListeners.contains(listener)){
            mListeners.add(listener)
        }
    }
    fun removeListener(listener: IOnLocationChangedListener){
        KingStone.checkRunInApiThread()
        mListeners.remove(listener)
    }

    @SuppressLint("MissingPermission")
    private fun startInternal(context:Context):Boolean{

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providerList = locationManager.getProviders(true)
        val locationProvider = if (providerList.contains(LocationManager.GPS_PROVIDER)) {
            Log.d(TAG, "startInternal: Get GPS_PROVIDER ")
            LocationManager.GPS_PROVIDER
        } else if (providerList.contains(LocationManager.NETWORK_PROVIDER)) { //Google服务不可用
            Log.d(TAG, "startInternal: Get NETWORK_PROVIDER")
            LocationManager.NETWORK_PROVIDER
        } else {
            Log.d(TAG, "startInternal: Get NON_PROVIDER")
            val intent = Intent()
            intent.action = Settings.ACTION_LOCATION_SOURCE_SETTINGS
            context.startActivity(intent)
            return false
        }

        val locationListener: LocationListener = NativeLocationListener(WeakReference(this))
        var sec = 3 //seconds
        val provider = LocationManager.GPS_PROVIDER

        val location = locationManager?.getLastKnownLocation(provider)
        if(location !=null){
            mLastLocation = location
            mListeners.forEach {
                it.onLocationChanged(location)
            }
        }
        locationManager?.requestLocationUpdates(
            provider, sec * 1000L, 30f, locationListener)
        hasStarted = true
        Log.d(TAG, "startInternal: successfully")
        return true
    }
    private fun stopInternal(){
        if(locationListener != null) {
            locationManager?.removeUpdates(locationListener!!)
            locationManager = null
        }
        hasStarted = false
    }


}