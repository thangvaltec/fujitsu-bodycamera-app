package com.bodycamera.ba.codec.codec

import android.media.MediaCodec
import android.util.Log
import com.bodycamera.ba.codec.filter.VideoFilter
import com.bodycamera.ba.data.IOnMediaEncoderListener
import com.bodycamera.ba.data.IOnVideoFilterCallback
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.TextureAvailableMessage
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.data.VideoEncodeParam
import com.bodycamera.ba.tools.Goldberg
import com.bodycamera.ba.tools.IOnGoldBergListener
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.tools.SThread
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class VideoEncoderWrapper : BaseEncoder(), IOnVideoFilterCallback {

    companion object{
        const val TAG = "VideoEncoderWrapper"
    }
    private var mWorkerThread:SThread?=null
    private var mListener : IOnMediaEncoderListener?=null
    private var mFirstTimeStamp = -1L
    override fun processOutput() {
        val bufferInfo = mBufferInfo?:return
        if(bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
            return
        }
        var outputLength = bufferInfo.size
        val outputBytes = ByteArray(outputLength)
        mOutputByteBuffer?.get(outputBytes, 0, outputLength)
        val bufferInfoCopy = MediaCodec.BufferInfo()
        bufferInfo?.apply {
            if(mFirstTimeStamp == -1L) mFirstTimeStamp = presentationTimeUs
            bufferInfoCopy.flags = flags
            bufferInfoCopy.size = size
            bufferInfoCopy.offset = offset
            bufferInfoCopy.presentationTimeUs = presentationTimeUs - mFirstTimeStamp
        }
        mListener?.onMediaEncoderDataOutput(isAudio = false,bufferInfoCopy, ByteBuffer.wrap(outputBytes))
    }

    override fun onFormatOutputChangedEvent() {
        Log.i(TAG, "onFormatOutputChangedEvent format=$mOutputMediaFormat")
        mListener?.onMediaEncoderFormatChanged(isAudio = false,mOutputMediaFormat!!)
    }

    private fun createVideoEncoder(param: VideoEncodeParam):Boolean{
        return startVideoEncoder(param)
    }

    private fun releaseVideoEncoder(){
        stopEncoder()
    }

    private fun createVideoFilter(outputSize:MySize,videoAlign: VideoAlign):Boolean{
        if(mInputSurface == null) return false
        KingStone.getSurfaceWrapper()?.createVideoFilter(this,mInputSurface!!,outputSize,videoAlign)
        return true
    }

    private fun releaseVideoFilter(){
        if(mInputSurface!= null) {
            KingStone.getSurfaceWrapper()?.releaseVideoFilter(this)
        }
    }

    private fun runBoolean(method:()->Boolean):Boolean{
        return mWorkerThread?.invokeBoolean (method) == true
    }
    private fun runAsync(method: () -> Unit){
        mWorkerThread?.invokeAsync(method)
    }
    private fun runBlock(method:()->Unit){
        mWorkerThread?.invoke (method)
    }

    fun create(videoEncodeParam: VideoEncodeParam,listener: IOnMediaEncoderListener):Boolean{
        if(mWorkerThread == null){
            mWorkerThread = SThread()
            mWorkerThread?.start()
        }
        return runBoolean {
            val outputSize = MySize(videoEncodeParam.width,videoEncodeParam.height)
            mListener = listener
            listener.onMediaEncoderStart(isAudio = true)
            return@runBoolean if(createVideoEncoder(videoEncodeParam) && createVideoFilter(outputSize,VideoAlign.DEFAULT)){
                mHasStarted.set(true)
                Log.i(TAG, "create: VideoEncoderWrapper ok")
                true
            }else {
                Log.i(TAG, "create: VideoEncoderWrapper failed")
                mHasStarted.set(false)
                false
            }
        }
    }

    fun release(){
        mHasStarted.set(false)
        runBlock {
            releaseVideoFilter()
            releaseVideoEncoder()
            mWorkerThread?.stop()
            mWorkerThread = null
        }
    }

    override fun onVideoFilterTextureAvailable() {
        runAsync {
            if(mHasStarted.get()){
                drainFromSurface()
            }
        }
    }
}