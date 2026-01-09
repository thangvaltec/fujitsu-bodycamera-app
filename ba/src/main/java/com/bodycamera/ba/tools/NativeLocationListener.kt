package com.bodycamera.ba.tools

import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.util.Log
import com.bodycamera.ba.util.LocationHelper
import java.lang.ref.WeakReference
import kotlin.math.abs

class NativeLocationListener(
    private var mNativeLocationController :WeakReference<NativeLocationController>)
        :LocationListener{

    private var mLastUpdateTime = 0L
    private var prevLat = 0.0
    private var prevLng = 0.0
    //30米的距离判断为静止状态
    private val lowDistance = 24

    //判断静止状态，如果lowDistanceCount连续3次以上，上传频率减少为1/3(10秒变为30秒)
    private var lowDistanceCount = 0
    private val MaxLowDistanceCount = 3

    /**
     * Simple filtering of GPS positioning jitter
     */
    private fun checkLocation(location: Location):Boolean{
        var skipForLowDistance = false
        var latlon = DoubleArray(2)
        //native location use wgs84 type coordinate
       //wgs84
        latlon[0] = location.latitude
        latlon[1] = location.longitude
        //目的是模拟判断静止状态，减少上传频率
        //检查上次跟这次的经纬度距离，如果如果距离大于lowDistance，设备不是在静止状态中
        if (prevLat == 0.0 && prevLng == 0.0) {
            //第一次进来不用做任何判断逻辑
        } else {
            val nDistance = abs(LocationHelper.getDistance1(prevLat, prevLng, latlon[0],latlon[1]) * 1000)
            if (nDistance > lowDistance) {
                //如果大于100，抛弃掉，大概率是定位弄错了
                if(nDistance > 100){
                    skipForLowDistance = true
                    //如果是静止状态转运动状态，第一次坐标需要丢掉
                }else if (lowDistanceCount >= MaxLowDistanceCount) {
                    //不要向服务器更新gprs信息
                    skipForLowDistance = true
                    lowDistanceCount = 0
                }else{
                    prevLat = latlon[0]
                    prevLng = latlon[1]
                }

            } else {
                lowDistanceCount = Integer.min(++lowDistanceCount, 100000)
                prevLat = latlon[0]
                prevLng = latlon[1]
            }
            //判断是否要skipForLowDistance
            if (!skipForLowDistance && lowDistanceCount > MaxLowDistanceCount) {
                skipForLowDistance =
                    lowDistanceCount % MaxLowDistanceCount != MaxLowDistanceCount - 1
            }
        }
        val shouldSend = !skipForLowDistance
        return shouldSend
    }
    override fun onLocationChanged(location: Location) {

        if(checkLocation(location)) {
            Log.i(
                TAG,
                "onLocationChanged:  longitude=${location.longitude},latitude=${location.latitude}",
            )
            KingStone.runAsync {
                val cc = mNativeLocationController.get()?:return@runAsync
                cc.setLastLocation(location)
                val listeners =cc.getListeners()
                listeners.forEach {
                    it.onLocationChanged(location)
                }
            }
        }
    }
    override fun onStatusChanged( provider:String,  status:Int,  extras: Bundle){
        Log.d(TAG, "onStatusChanged: provider=$provider,status=$status,extras=$extras")
    }

    override fun onProviderEnabled( provider:String){
        super.onProviderEnabled(provider)
        Log.d(TAG, "onProviderEnabled:provider = $provider ")
    }

    override fun onProviderDisabled(provider: String) {
        super.onProviderDisabled(provider)
        Log.d(TAG, "onProviderDisabled:provider = $provider ")
    }

    companion object{
        const val TAG = "NativeLocationListener"
    }

}