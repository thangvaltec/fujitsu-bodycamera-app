package com.bodycamera.ba.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log



object AudioHelper {

    //获取多媒体最大音量
    fun getMediaMaxVolume(context: Context): Int {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return mAudioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
    }

    //获取多媒体音量
    fun getMediaVolume(context: Context): Int {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return mAudioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    }

    //获取通话最大音量
    fun getCallMaxVolume(context: Context): Int {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return mAudioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 0
    }

    //获取系统音量最大值
    fun getSystemMaxVolume(context: Context): Int {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return mAudioManager?.getStreamMaxVolume(AudioManager.STREAM_SYSTEM) ?: 0
    }

    //获取系统音量
    fun getSystemVolume(context: Context): Int {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return mAudioManager?.getStreamVolume(AudioManager.STREAM_SYSTEM) ?: 0
    }

    //获取提示音量最大值
    fun getAlarmMaxVolume(context: Context): Int {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 0
    }

    //默认初始化音量
    fun resetVoiceVolume(context: Context){
        val volume = 70
        val rate = volume.toFloat() / 100
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var value = (mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * rate).toInt()
        mAudioManager?.setStreamVolume(
            AudioManager.STREAM_MUSIC,  //多媒体
            value,
            AudioManager.FLAG_PLAY_SOUND
        )
        value = (mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_ALARM) * rate).toInt()
        mAudioManager?.setStreamVolume(
            AudioManager.STREAM_ALARM,  //警报
            value,
            AudioManager.FLAG_PLAY_SOUND
        )
        value = (mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_SYSTEM) * rate).toInt()
        mAudioManager?.setStreamVolume(
            AudioManager.STREAM_SYSTEM,  //系统提示
            value,
            AudioManager.FLAG_PLAY_SOUND
        )

        value = (mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) * rate).toInt()
        mAudioManager?.setStreamVolume(
            AudioManager.STREAM_NOTIFICATION,  //通知
            value,
            AudioManager.FLAG_PLAY_SOUND
        )
        value = (mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_RING) * rate).toInt()
        mAudioManager?.setStreamVolume(
            AudioManager.STREAM_RING,  //电话通知
            value,
            AudioManager.FLAG_PLAY_SOUND
        )
        value = (mAudioManager!!.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) * rate).toInt()
        mAudioManager?.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,  //通话声音
            value,
            AudioManager.FLAG_PLAY_SOUND
        )
    }

    /**
     * 设置多媒体音量
     */
    fun setMediaVolume(context: Context,volume: Int) {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioManager?.setStreamVolume(
            AudioManager.STREAM_MUSIC,  //音量类型
            volume,
            AudioManager.FLAG_PLAY_SOUND
        ) //  | AudioManager.FLAG_SHOW_UI);
    }

    //
    /**
     * microphone volume
     *  Desired volume level (0 - 100)
     */
    fun setCallVolume(context: Context,volume: Int) {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioManager?.setStreamVolume(
            AudioManager.STREAM_VOICE_CALL,
            volume,
            AudioManager.STREAM_VOICE_CALL
        )
    }

    // 关闭/打开扬声器播放
    fun setSpeakerStatus(context: Context,on: Boolean) {
        if(on)setAudioSpeakerMode(context)
        else setAudioNormalMode(context)
    }

    /**
     * Set blueTooth mode
     */
    fun setAudioBtMode(context:Context){
            val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            mAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            mAudioManager.isBluetoothScoOn = true
            mAudioManager.startBluetoothSco()
            mAudioManager.isSpeakerphoneOn = false
    }

    /**
     * Set headset mode
     */
    fun setAudioHeadsetMode(context:Context){
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioManager.mode = AudioManager.MODE_IN_CALL
        mAudioManager.stopBluetoothSco()
        mAudioManager.isBluetoothScoOn = false
        mAudioManager.isSpeakerphoneOn = false
    }

    fun setAudioNormalMode(context:Context){
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioManager.mode = AudioManager.MODE_NORMAL
        mAudioManager.stopBluetoothSco()
        mAudioManager.isBluetoothScoOn = false
        mAudioManager.isSpeakerphoneOn = false
    }

    /**
     * set Speaker mode
     */
    fun setAudioSpeakerMode(context:Context){
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        mAudioManager.isMicrophoneMute = false
        mAudioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        mAudioManager.stopBluetoothSco()
        mAudioManager.isBluetoothScoOn = false
        mAudioManager.isSpeakerphoneOn = true
    }

    fun isWiredHeadsetOn(context: Context):Boolean{
        return checkHeadset(context,isWireless = false)
    }

    fun isWirelessHeadsetOn(context: Context):Boolean{
        return checkHeadset(context,isWireless = true)
    }

    private fun checkHeadset(context: Context,isWireless:Boolean): Boolean {
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices: Array<AudioDeviceInfo> = mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                Log.e(TAG, "checkHeadset: deviceType=${device.type}")
                when(device.type){
                    AudioDeviceInfo.TYPE_WIRED_HEADSET ,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> {
                        return !isWireless
                    }
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO ->{
                        return isWireless
                    }
                }
            }
        } else {
            if(isWireless ){
                return mAudioManager.isBluetoothScoOn || mAudioManager.isBluetoothA2dpOn
            }else{
                mAudioManager.isWiredHeadsetOn
            }
        }
        return false
    }

    fun test(context:Context){
        val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val devices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mAudioManager.availableCommunicationDevices
        } else {
            Log.e(TAG, "test: NULL", )
           null
        }
        if(devices == null) return
        // User choose one of the devices, for example, TYPE_BLE_HEADSET
        val userSelectedDeviceType = AudioDeviceInfo.TYPE_BLUETOOTH_SCO
// Filter for the device from the list
        var selectedDevice: AudioDeviceInfo? = null
        for (device in devices) {
            if (device.type == userSelectedDeviceType) {
                Log.e(TAG, "test-device:$device  ", )
                selectedDevice = device

                break
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mAudioManager.setCommunicationDevice(selectedDevice!!)
        }
        Log.e(TAG, "test: selectDevice=${selectedDevice?.id}", )

    }

    const val TAG = "audioHelper"

}