package com.bodycamera.ba.codec.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import com.bodycamera.ba.data.AudioEncodeParam
import com.bodycamera.ba.data.IOnMediaEncoderListener
import com.bodycamera.ba.tools.SThread
import java.nio.ByteBuffer

class AudioEncoderWrapper: BaseEncoder() {
    companion object{
        const val TAG = "AudioEncoderWrapper"
    }
    private var mWorkThread:SThread?=null
    private var mAudioSource:AudioSource?=null
    private var mFirstTimeStamp = -1L
    override fun processOutput() {
        val bufferInfo = mBufferInfo?:return
        if(bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
            return
        }
        // add ADTS-header
        var outputLength = bufferInfo.size + 7
        val outputBytes = ByteArray(outputLength)

        ////////WARNING
        //should modify ADTS-header base on your audio param
        addADTStoPacket(outputBytes)
        mOutputByteBuffer!!.get(outputBytes, 7, bufferInfo.size)

        val bufferInfoCopy = MediaCodec.BufferInfo()
        bufferInfo.apply {
            if(mFirstTimeStamp == -1L) mFirstTimeStamp = presentationTimeUs
            bufferInfoCopy.flags = flags
            bufferInfoCopy.size = outputLength
            bufferInfoCopy.offset = offset
            bufferInfoCopy.presentationTimeUs = presentationTimeUs - mFirstTimeStamp
        }
        mListener?.onMediaEncoderDataOutput(isAudio = true,bufferInfoCopy, ByteBuffer.wrap(outputBytes))
    }

    override fun onFormatOutputChangedEvent() {
        Log.i(TAG, "onFormatOutputChangedEvent: format=${mOutputMediaFormat}")
        mListener?.onMediaEncoderFormatChanged(isAudio = true,mOutputMediaFormat!!)
    }

    private var mListener :IOnMediaEncoderListener?=null
    private fun createAudioEncoder(param: AudioEncodeParam):Boolean{
        return startAudioEncoder(param)
    }
    private fun releaseAudioEncoder(){
        stopEncoder()
    }
    private fun createAudioSource(param: AudioSource.AudioSourceParam):Boolean{
        if(mAudioSource == null){
            mAudioSource = AudioSource()
            return mAudioSource!!.create(param)
        }
        return true
    }
    private fun releaseAudioSource(){
        if(mAudioSource !=null){
            mAudioSource?.release()
            mAudioSource = null
        }
    }
    private fun runAsync(method: () -> Unit){
        mWorkThread?.invokeAsync(method)
    }
    private fun runBlock(method:()->Unit){
        mWorkThread?.invoke (method)
    }

    private fun runRoutineTask(){
        runAsync {
            while(mHasStarted.get()){
                val data = mAudioSource?.readAudio()

                if(mHasStarted.get() && data != null){
                    drain(data)
                }
            }
        }
    }

    fun create(audioEncodeParam: AudioEncodeParam,listener: IOnMediaEncoderListener):Boolean{
        if(mWorkThread == null){
            mWorkThread = SThread()
            mWorkThread?.start()
        }
        return if(createAudioEncoder(audioEncodeParam) &&
            createAudioSource(
                AudioSource.AudioSourceParam(
                    audioEncodeParam.sampleRate,
                    audioEncodeParam.channelCount,
                    audioEncodeParam.bitFormat
                ))){
            mHasStarted.set(true)
            mAudioParam = audioEncodeParam
            listener.onMediaEncoderStart(isAudio = true)
            mListener = listener
            runRoutineTask()
            Log.i(TAG, "create: AudioEncoderWrapper ok")
            true
        }else{
            mHasStarted.set(false)
            Log.i(TAG, "create: AudioEncoderWrapper failed")
            false
        }
    }

    fun release(){
        mHasStarted.set(false)
        runBlock{
            mListener = null
            releaseAudioSource()
            releaseAudioEncoder()
            mWorkThread?.stop()
            mWorkThread = null
        }
    }

    private fun addADTStoPacket(packet: ByteArray) {
        val packetLen = packet.size
        val channelCount = mAudioParam!!.channelCount
        val sampleRateIndex = mAudioParam!!.getSampleRateIndex()
        var profile = 2 // AAC LC
        var channelConfig = channelCount //channelCount
        packet[0] = 0xFF.toByte()
        packet[1] = 0xF1.toByte() // 0xF9
        packet[2] = ((profile - 1 shl 6) + (sampleRateIndex shl 2) + (channelConfig shr 2)).toByte()
        packet[3] = ((channelConfig and 3 shl 6) + (packetLen shr 11)).toByte()
        packet[4] = (packetLen and 0x7FF shr 3).toByte()
        packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }
}