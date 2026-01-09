package com.bodycamera.ba.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * android.hardware.usb.action.ENABLE_UMS
 */
class CollectStationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when(intent.action){
            "android.hardware.usb.action.ENABLE_UMS"->{
                Log.d(TAG, "onReceive: android.hardware.usb.action.ENABLE_UMS" )
                /*val list = BodyCamera.getInstance().getCollectionStationListeners()
                if(list.isNotEmpty()){
                    list.last().onEnable()
                }*/
            }
            else->{}
        }
    }
    companion object{
        const val TAG = "CollectStationReceiver"
    }
}