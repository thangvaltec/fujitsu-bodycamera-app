package com.bodycamera.ba.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.util.Log
import com.bodycamera.ba.data.AudioOutputDevice
import com.bodycamera.ba.tools.KingStone


/**
 * IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
 * IntentFilter(Intent.ACTION_HEADSET_PLUG);
 * AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED
 *
 */
class AudioOutputDeviceReceiver: BroadcastReceiver() {

    interface IOnAudioOutputDeviceChangedListener{
        fun onAudioOutputDeviceChanged(audioOutputDevice: AudioOutputDevice)
    }

    private var mLastAudioOutputDevice: AudioOutputDevice = AudioOutputDevice.TYPE_SPEAKER
    private var mListener: IOnAudioOutputDeviceChangedListener?=null

    fun create(context: Context,listener: IOnAudioOutputDeviceChangedListener){
        KingStone.checkRunInApiThread()
        mListener = listener
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG)
        intentFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        context.registerReceiver(this,intentFilter)
        getState(context)
    }

    fun release(context: Context){
        KingStone.checkRunInApiThread()
        context.unregisterReceiver(this)
        mListener = null
    }
    @SuppressLint("MissingPermission")
    private fun getState(context:Context){
        val blueManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = blueManager.adapter
        val state = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
        if (BluetoothProfile.STATE_CONNECTED == state) {
            Log.d(TAG, "onReceive: bluetooth headset connected")
            KingStone.runAsync {
                mLastAudioOutputDevice = AudioOutputDevice.TYPE_BLUETOOTH
                mListener?.onAudioOutputDeviceChanged(mLastAudioOutputDevice)
            }
        }
        else if (BluetoothProfile.STATE_DISCONNECTED == state) {
            Log.d(TAG, "onReceive: bluetooth headset disconnected")
            KingStone.runAsync {
                mLastAudioOutputDevice = AudioOutputDevice.TYPE_SPEAKER
                mListener?.onAudioOutputDeviceChanged(mLastAudioOutputDevice)
            }
        }
    }

   // @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {

        when(intent.action){
            /*AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED ->{
                val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                Log.d(TAG, "onReceive: ACTION_SCO_AUDIO_STATE_UPDATED state = $state", )
                when(state){
                    AudioManager.SCO_AUDIO_STATE_CONNECTED ->{
                        mAudioManager.isBluetoothScoOn = true
                    }
                    AudioManager.SCO_AUDIO_STATE_DISCONNECTED ->{
                        mAudioManager.isBluetoothScoOn = false
                    }
                    AudioManager.SCO_AUDIO_STATE_ERROR ->{
                        mAudioManager.isBluetoothScoOn = false
                    }
                }
            }*/
            BluetoothAdapter.ACTION_STATE_CHANGED ->{
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
               // val state2 = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF){
                    Log.d(
                        TAG,
                        "onReceive: wireless headset in STATE_OFF,STATE_TURNING_OFF"
                    )
                    KingStone.runAsync {
                        mLastAudioOutputDevice = AudioOutputDevice.TYPE_SPEAKER
                        mListener?.onAudioOutputDeviceChanged(mLastAudioOutputDevice)
                    }
                }
            }
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED ->{
                getState(context)
            }

            Intent.ACTION_HEADSET_PLUG ->{
                //Broadcast Action: Wired Headset plugged in or unplugged.
                val plugged = intent.getIntExtra("state", 0) == 1
                val audioName = intent.getStringExtra("name")
                val hasMicrophone = intent.getIntExtra("microphone", 0) == 1
                if(plugged) {
                    Log.d(
                        TAG,
                        "onReceive: wired headset connected ${audioName},hasMicrophone=${hasMicrophone}"
                    )
                    KingStone.runAsync {
                        mLastAudioOutputDevice = AudioOutputDevice.TYPE_WIRED
                        mListener?.onAudioOutputDeviceChanged(mLastAudioOutputDevice)
                    }
                }else {
                    Log.d(
                        TAG,
                        "onReceive: wired headset disconnected ${audioName},hasMicrophone=${hasMicrophone}"
                    )
                    KingStone.runAsync {
                        mLastAudioOutputDevice = AudioOutputDevice.TYPE_SPEAKER
                        mListener?.onAudioOutputDeviceChanged(mLastAudioOutputDevice)
                    }
                }
            }
        }
    }
    companion object {
        const val TAG = "HeadsetReceiver"
    }


}

































