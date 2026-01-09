package com.bodycamera.ba.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bodycamera.ba.data.Constants
import com.bodycamera.ba.tools.Goldberg
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.tools.OnM530DeviceImpl
import com.bodycamera.ba.util.BeepHelper


/**
 * only used for m350
 * android.intent.action.EINT_SWITCH
 * Intent.ACTION_BATTERY_CHANGED
 *
 */
class DeviceEINTReceiver: BroadcastReceiver() {
    interface IOnDeviceEINTChangedListener{
        fun onDropDetection()
        fun onBackupBatteryChanged(volt:Int,level:Int)
        fun onBackCoverClosed(flag:Boolean)
    }
    private var lastDropDetectionTime = 0L
    private var lastBackupBatteryAlarmTime = 0L
    private var mListener:IOnDeviceEINTChangedListener?=null

    companion object{
        const val TAG = "DeviceEINTReceiver"
    }

    fun create(context: Context,listener: IOnDeviceEINTChangedListener){
        KingStone.checkRunInApiThread()
        val intent = IntentFilter()
        intent.addAction(Intent.ACTION_BATTERY_CHANGED)
        intent.addAction(OnM530DeviceImpl.EINT_FILTER_ACTION)
        mListener = listener
        context.registerReceiver(this, intent)
    }
    fun release(context:Context){
        KingStone.checkRunInApiThread()
        mListener = null
        context.unregisterReceiver(this)
        KingStone.checkRunInApiThread()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action == Intent.ACTION_BATTERY_CHANGED){
            val deviceFeature = KingStone.getDeviceFeature()
            deviceFeature?.apply {
               val voltValue = getBackupBatteryVoltage()
               val level = getBackupBatteryLevel()
                Log.d(TAG, "backupBattery onReceive: level=$level,voltValue=$voltValue")
                KingStone.runAsync {
                    mListener?.onBackupBatteryChanged(voltValue, level)
                }
            }

        }else if(intent.action == "android.intent.action.EINT_SWITCH") {
            //status:0-fall 1-free
            //name:eint_fall_free_switch
            val status = intent.getIntExtra("status", -1)
            val name = intent.getStringExtra("name")
            Log.e(TAG, "onReceive: status=$status,name=$name", )
            if (name == "eint_fall_free_switch" && status == 1) {
                val now = System.currentTimeMillis()
                if (now - lastDropDetectionTime > 10000L) {
                    BeepHelper.fall(context)
                    KingStone.runAsync {
                        mListener?.onDropDetection()
                    }
                    lastDropDetectionTime = now
                }
            } else if (name == "eint_back_cover_switch") {
                //0-合上 1-打开
                val backCover = intent.getIntExtra("status", 0)
                Log.d(TAG, "backupBattery:$backCover,opened = ${backCover == 1}")
                KingStone.runAsync {
                    mListener?.onBackCoverClosed(backCover == 0)
                }
            }
        }
    }
}