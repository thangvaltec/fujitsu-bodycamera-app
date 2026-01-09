package com.bodycamera.ba.tools

import android.location.GnssAntennaInfo.Listener
import com.bodycamera.ba.data.IOnDeviceFeatureListener
import com.bodycamera.ba.data.KeyAction

class OnDeviceFeatureListener: IOnDeviceFeatureListener {

    private val mCallbacks = mutableListOf<IOnDeviceFeatureListener>()
    /**
     * must run in kingstore worker thread to prevent thread-safe issues
     */
    fun registerCallback(callback:IOnDeviceFeatureListener){
        KingStone.checkRunInApiThread()
        if(mCallbacks.contains(callback))
            mCallbacks.add(callback)
    }
    fun unregisterCallback(callback:IOnDeviceFeatureListener){
        KingStone.checkRunInApiThread()
        mCallbacks.remove(callback)
    }

    override fun onTurnCameraImageGray(flag: Boolean) {
        KingStone.checkRunInApiThread()
        mCallbacks.forEach {
            it.onTurnCameraImageGray(flag)
        }
    }

    override fun onDropDetected() {
        KingStone.checkRunInApiThread()
        mCallbacks.forEach {
            it.onDropDetected()
        }
    }

    override fun onBackCoverOpen(flag: Boolean) {
        KingStone.checkRunInApiThread()
        mCallbacks.forEach {
            it.onBackCoverOpen(flag)
        }
    }

    override fun onBackupBatteryChanged(capacity: Int) {
        KingStone.checkRunInApiThread()
        mCallbacks.forEach {
            it.onBackupBatteryChanged(capacity)
        }
    }

    override fun onCollectStationEnable() {
        KingStone.checkRunInApiThread()
        mCallbacks.forEach {
            it.onCollectStationEnable()
        }
    }

    override fun onNetworkChanged() {
        mCallbacks.forEach {
            it.onNetworkChanged()
        }
    }
}