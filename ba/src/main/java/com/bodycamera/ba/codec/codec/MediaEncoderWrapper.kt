package com.bodycamera.ba.codec.codec

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.util.Log
import com.bodycamera.ba.data.AudioEncodeParam
import com.bodycamera.ba.data.IOnMediaEncoderListener
import com.bodycamera.ba.data.MediaPipeError
import com.bodycamera.ba.data.VideoEncodeParam
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This is a thread-safe class, but its children class is not ,
 * so children class should handle thread-safe issue, either using thread or lock
 */
abstract class MediaEncoderWrapper : IOnMediaEncoderListener {
    companion object {
        const val TAG = "MediaEncoderWrapper"
    }

    abstract fun runAsync(method:()->Unit)
    private var   mAudioEncoderWrapper: AudioEncoderWrapper? = null
    private var   mVideoEncoderWrapper: VideoEncoderWrapper? = null

    protected var mAudioEncodeParam:AudioEncodeParam?=null
    protected var mVideoEncoderParam:VideoEncodeParam?=null
    protected var mHasStarted = AtomicBoolean(false)
    private var   mIsIDRFound:Boolean = false
    private var   mIsMediaFormatChanged = AtomicBoolean(false)

    protected var mAudioFormat:MediaFormat?=null
    protected var mVideoFormat:MediaFormat?=null
    protected var mFirstReceiveTimeMillis = -1L
    protected var mLastReceiveTimeMillis = -1L




    private fun startVideoInternal(videoEncodeParam: VideoEncodeParam): Boolean {
        if (mVideoEncoderWrapper == null) {
            mVideoEncoderWrapper = VideoEncoderWrapper()
            if(mVideoEncoderWrapper!!.create(videoEncodeParam,this)){
                mVideoEncoderParam = videoEncodeParam
            }else{
                Log.i(TAG, "startVideoInternal: failed")
                return false
            }
        }
        return true
    }

    private fun stopVideoInternal() {
        if (mVideoEncoderWrapper != null) {
            mVideoEncoderWrapper?.release()
            mVideoEncoderWrapper = null
        }
    }

    private fun startAudioInternal(audioEncodeParam: AudioEncodeParam): Boolean {
        if (mAudioEncoderWrapper == null) {
            mAudioEncoderWrapper = AudioEncoderWrapper()
            if(mAudioEncoderWrapper!!.create(audioEncodeParam,this)){
                mAudioEncodeParam = audioEncodeParam
            }else{
                Log.i(TAG, "startAudioInternal: failed")
                return false
            }
        }
        return true
    }

    private fun stopAudioInternal() {
        if (mAudioEncoderWrapper != null) {
            mAudioEncoderWrapper?.release()
            mAudioEncoderWrapper = null

        }
    }

    fun createEncoderWrapper(audioEncodeParam: AudioEncodeParam, videoEncodeParam: VideoEncodeParam?): Boolean {
       return if (videoEncodeParam != null) startVideoInternal(videoEncodeParam) && startAudioInternal(
                audioEncodeParam
            )
            else startAudioInternal(audioEncodeParam)
    }

    fun releaseEncoderWrapper() {
        mHasStarted.set(false)
        stopAudioInternal()
        stopVideoInternal()
        mIsIDRFound = false
        mIsMediaFormatChanged.set(false)
        mAudioFormat = null
        mAudioEncodeParam = null
        mVideoFormat = null
        mVideoEncoderParam = null
    }

    abstract fun onMediaEncoderWrapperStart()
    abstract fun onMediaEncoderWrapperFormatChanged(audioFormat:MediaFormat, videoFormat: MediaFormat?)
    abstract fun onMediaEncoderWrapperDataOutput(isAudio: Boolean,bufferInfo: BufferInfo,byteBuffer: ByteBuffer)
    abstract fun onMediaEncoderWrapperError(isAudio: Boolean, error: MediaPipeError)

    override fun onMediaEncoderStart(isAudio: Boolean) {
        runAsync {
            if(mHasStarted.getAndSet(true)) return@runAsync
            onMediaEncoderWrapperStart()
        }

    }

    override fun onMediaEncoderFormatChanged(isAudio: Boolean, mediaFormat: MediaFormat) {
        runAsync {
            Log.i(TAG, "onFormatOutputChangedEvent: isAudio=$isAudio,format=${mediaFormat}")
            if (!mHasStarted.get()) return@runAsync
            if (isAudio) {
                mAudioFormat = mediaFormat
                if (mVideoEncoderParam == null || mVideoFormat != null) {
                    onMediaEncoderWrapperFormatChanged(mAudioFormat!!, mVideoFormat)
                    mIsMediaFormatChanged.set(true)
                }
            } else {
                mVideoFormat = mediaFormat
                if (mAudioFormat != null) {
                    onMediaEncoderWrapperFormatChanged(mAudioFormat!!, mVideoFormat)
                    mIsMediaFormatChanged.set(true)
                }
            }
        }
    }

    override fun onMediaEncoderDataOutput(
        isAudio: Boolean,
        bufferInfo: MediaCodec.BufferInfo,
        byteBuffer: ByteBuffer
    ) {
        runAsync {
            if (!mHasStarted.get() || !mIsMediaFormatChanged.get()) return@runAsync
            val nowMillis = System.currentTimeMillis()
            if (mFirstReceiveTimeMillis == -1L)
                mFirstReceiveTimeMillis = nowMillis
            mLastReceiveTimeMillis = nowMillis
            if (!isAudio) {
                //video data must has a complete GOP list
                if (!mIsIDRFound) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME == MediaCodec.BUFFER_FLAG_KEY_FRAME)
                        mIsIDRFound = true
                }
                if (mIsIDRFound) onMediaEncoderWrapperDataOutput(
                    isAudio = false,
                    bufferInfo,
                    byteBuffer
                )
            } else {
                onMediaEncoderWrapperDataOutput(isAudio = true, bufferInfo, byteBuffer)
            }
        }
    }

    override fun onMediaEncoderError(isAudio: Boolean, error: MediaPipeError) {
        runAsync {
            if (!mHasStarted.get()) return@runAsync
            onMediaEncoderWrapperError(isAudio, error)
        }
    }
}