package com.bodycamera.ba.codec.codec

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.bodycamera.ba.data.SimpleBuffer
import java.lang.Exception
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class AudioSource {
    data class AudioSourceParam(
        val sampleRate: Int = 8000,
        val channelCount: Int = 1,
        val bitFormat:Int = AudioFormat.ENCODING_PCM_16BIT
    )
    data class AudioSourceData(
        val audioParam:AudioSourceParam,
        val byteBuffer: SimpleBuffer,
        val collectUs :Long
    )

    companion object{
        const val TAG = "AudioSource"
    }

    private var mHasStarted  = AtomicBoolean(false)
    private var mAudioRecord: AudioRecord?=null
    private var mAudioSourceParam :AudioSourceParam?=null
    private var pcmBuffer = byteArrayOf()

    fun create(param: AudioSourceParam):Boolean{
        mAudioSourceParam = param
        return if(createMicrophone() && startRecording()){
            mHasStarted.set(true)
            true
        }else{
            mHasStarted.set(false)
            false
        }
    }

    fun release(){
        stopInternal()
    }

    private fun stopInternal(){
        mHasStarted.set(false)
        try {
            mAudioRecord?.stop()
            mAudioRecord?.release()
        }catch (ex:Exception){
            Log.e(TAG, "stopInternal: failed", ex)
        }
        mAudioRecord = null
    }

    private fun startRecording():Boolean{
        try {
            mAudioRecord?.startRecording()
        }catch (ex:java.lang.IllegalStateException){
            Log.e(TAG, "startRecording failed", ex)
            return false
        }
        Log.i(TAG, "startRecording successfully")
        return true
    }

    /*
    private fun getCollectionTimeUs():Long{
        if(mAudioRecord == null) throw RuntimeException("AudioRecord is null")
        val audioTimestamp = AudioTimestamp()
        mAudioRecord!!.getTimestamp(audioTimestamp,)
    }*/

    fun readAudio(): AudioSourceData?{
        if(!mHasStarted.get() || mAudioRecord == null) return null
        val size = mAudioRecord!!.read(pcmBuffer,0,pcmBuffer.size)
        if(size > 0){
            return AudioSourceData(
                audioParam = mAudioSourceParam!!,
                SimpleBuffer(
                    pcmBuffer,
                    0,
                    size
                ),
                System.nanoTime() / 1000
            )
        }
        return null
    }

    private fun createMicrophone():Boolean{
        //val enableAcousticEchoCanceler = false
        //val enableNoiseSuppressor = false
        return createInternalMicrophone0ld(MediaRecorder.AudioSource.MIC)
    }

    private fun getChannelConfig(channelCount:Int):Int{
        val channelConfig  = when(channelCount){
            1 -> AudioFormat.CHANNEL_IN_MONO
            2 -> AudioFormat.CHANNEL_IN_STEREO
            else->{
                throw java.lang.IllegalArgumentException("channelCount =$channelCount is not supported")
            }
        }
        return channelConfig
    }

    @SuppressLint("MissingPermission")
    private fun createInternalMicrophone0ld(audioSource:Int):Boolean{
        if(mAudioSourceParam == null) throw RuntimeException("AudioSourceParam is not set")
        val sampleRate = mAudioSourceParam!!.sampleRate
        val channelCount = mAudioSourceParam!!.channelCount
        val audioFormat = mAudioSourceParam!!.bitFormat
        val channelConfig = getChannelConfig(channelCount)
        val miniBufferSize = max(getPcmBufferSize(sampleRate,audioFormat,channelCount),1024)
        pcmBuffer = ByteArray(miniBufferSize)
        try {
            mAudioRecord = AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, miniBufferSize)
            if (mAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalArgumentException("some parameters of AudioRecord are  not valid")
            }
        }catch (ex:java.lang.IllegalArgumentException){
            Log.e(TAG, "createInternalMicrophone failed ", ex)
            return false
        }
        return true
    }

    private fun getPcmBufferSize(sampleRate: Int,audioFormat: Int,channelCount: Int): Int {
        val channelConfig = getChannelConfig(channelCount)
        return AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    }





}