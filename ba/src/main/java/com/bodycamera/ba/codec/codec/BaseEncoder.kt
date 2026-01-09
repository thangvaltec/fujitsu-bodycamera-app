package com.bodycamera.ba.codec.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.bodycamera.ba.data.AudioEncodeParam
import com.bodycamera.ba.data.VideoEncodeParam
import java.io.IOException
import java.lang.Exception
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

abstract class BaseEncoder() {
    private   var   mMediaCodec:MediaCodec?=null
    protected var   mOutputMediaFormat: MediaFormat?=null
    protected var   mMediaFormat: MediaFormat?=null
    protected var   mAudioParam:AudioEncodeParam?=null
    protected var   mVideoParam:VideoEncodeParam?=null
    protected var   mInputSurface:Surface?=null

    protected var   mHasStarted = AtomicBoolean(false)

    protected var   mBufferInfo:MediaCodec.BufferInfo?=null
    private var     mInputBufferIndex:Int = -1
    private var     mInputByteBuffer: ByteBuffer? =null
    protected var   mOutputBufferIndex:Int = -1
    protected var   mOutputByteBuffer: ByteBuffer? =null

    abstract fun processOutput()

    abstract fun onFormatOutputChangedEvent()


    protected fun startAudioEncoder(param: AudioEncodeParam):Boolean{
        val bitRate = param.getBitRate()
        val sampleRate = param.sampleRate
        val channelCount = param.channelCount
        val maxInputSize = 0
        val mimeType = param.mimeType
        val channelMask = param.channelMask()
        mMediaFormat = MediaFormat.createAudioFormat(mimeType, sampleRate, channelCount)
        mMediaFormat!!.apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxInputSize)
            setInteger(MediaFormat.KEY_CHANNEL_MASK, channelMask)
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
        }
        try {
            mMediaCodec = MediaCodec.createEncoderByType(mimeType)
            mMediaCodec?.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mMediaCodec?.start()
        } catch (ex: Exception) {
            Log.e(TAG, "startAudioEncoder: failed", ex)
            return false
        } 
        return true
    }

    protected fun startVideoEncoder(param: VideoEncodeParam):Boolean{
        mMediaFormat = MediaFormat.createVideoFormat(param.mimeType, param.width, param.height)
        mMediaFormat!!.apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )

            setInteger(
                MediaFormat.KEY_BITRATE_MODE,
                //MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
            )
            //20 - 40 - 70
            setInteger(
                MediaFormat.KEY_COMPLEXITY,
                50
            )
            val bitRate = param.getBitRate()
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, param.frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, param.gop)
        }
        mMediaCodec = MediaCodec.createEncoderByType(param.mimeType)
        try {
            mMediaCodec?.configure(mMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mInputSurface = mMediaCodec!!.createInputSurface()
            mMediaCodec?.start()
            Log.d(TAG, "startVideoEncoder successfully param =$param")
            return true
        }catch (ex:java.lang.Exception){
            Log.e(TAG, "startVideoCodec: failed",ex)
            return false
        }
    }

    protected fun stopEncoder(){
        mHasStarted.set(false)
        if(mMediaCodec!=null) {
            try {
                mMediaCodec?.stop()
                mMediaCodec?.release()
            } catch (ex: java.lang.Exception) {
                Log.e(TAG, "stopEncoder failed ", ex)
            }
            try {
                mInputSurface?.release()
            }catch (ex:Exception){
                Log.e(TAG, "stopEncoder: failed, mInputSurface.release() failed", )
            }
            mInputSurface = null
            mOutputMediaFormat = null
            mMediaFormat = null
            mAudioParam = null
            mVideoParam = null
            mBufferInfo = null
            mInputBufferIndex = -1
            mInputByteBuffer = null
            mOutputBufferIndex = -1
            mOutputByteBuffer = null
            mMediaCodec = null
        }
    }




    protected fun onOutputAvailable(){
        try {
            mOutputByteBuffer = mMediaCodec!!.getOutputBuffer(mOutputBufferIndex)
            if(mOutputBufferIndex >= 0){
                processOutput()
                mMediaCodec?.releaseOutputBuffer(mOutputBufferIndex, false)
            }
        }catch(ex:java.lang.IllegalStateException){
            Log.e(TAG, " onOutputAvailable failed", ex)
            return
        }
    }

    protected fun drainFromSurface() {
        while (mHasStarted.get()) {
            mBufferInfo = MediaCodec.BufferInfo()
            mOutputBufferIndex = mMediaCodec!!.dequeueOutputBuffer(mBufferInfo!!, 50)
            if (mOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mOutputMediaFormat = mMediaCodec!!.outputFormat
                onFormatOutputChangedEvent()
            } else if (mOutputBufferIndex >= 0) {
                onOutputAvailable()
            } else {
                break
            }
        }

    }

    private fun onInputAvailable(data:AudioSource.AudioSourceData) {
        try {
            mInputByteBuffer = mMediaCodec!!.getInputBuffer(mInputBufferIndex)
        }catch (ex:java.lang.IllegalStateException){
            Log.e(TAG, " onInputAvailable failed", ex)
        }
        processInput(data)
    }

    private fun processInput(data: AudioSource.AudioSourceData) {
        try {
            mInputByteBuffer?.clear()
            mInputByteBuffer?.put(data.byteBuffer.byteArray, data.byteBuffer.offset, data.byteBuffer.length)
            //val ptsUs: Long = System.nanoTime() / 1000
            val ptsUs: Long = data.collectUs
            mMediaCodec?.queueInputBuffer(mInputBufferIndex, 0, data.byteBuffer.length, ptsUs, 0)
        } catch ( e:java.lang.IllegalStateException){
            Log.e(TAG, "processInput failed", e)
        }
    }

    fun drain(data: AudioSource.AudioSourceData){
        if(!mHasStarted.get()) return
        mInputBufferIndex = mMediaCodec!!.dequeueInputBuffer(0)
        if (mInputBufferIndex >= 0) {
            onInputAvailable(data)
        }
        while (mHasStarted.get()) {
            mBufferInfo = MediaCodec.BufferInfo()
            try {
                mOutputBufferIndex = mMediaCodec!!.dequeueOutputBuffer(mBufferInfo!!, 30)
            }catch (ex: Exception) {
                Log.i(TAG, "drain: failed",ex)
                return
            }
            if (mOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                mOutputMediaFormat = mMediaCodec!!.outputFormat
                onFormatOutputChangedEvent()
            } else if (mOutputBufferIndex >= 0) {
                onOutputAvailable()
            } else {
                break
            }
        }
    }

    companion object{
        const val TAG = "BaseEncoder"
    }

}