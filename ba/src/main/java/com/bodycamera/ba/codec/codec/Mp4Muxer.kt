package com.bodycamera.ba.codec.codec

import android.media.MediaCodec.BufferInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.bodycamera.ba.data.AudioEncodeParam
import com.bodycamera.ba.data.MediaPipeError
import com.bodycamera.ba.data.SimpleBuffer
import com.bodycamera.ba.data.VideoEncodeParam
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.tools.SThread
import com.bodycamera.ba.util.SDCardHelper
import java.io.File
import java.lang.Exception
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
class Mp4Muxer : MediaEncoderWrapper() {
    companion object {
        const val TAG = "Mp4Muxer"
    }

    private var mMediaMuxer: MediaMuxer? = null
    private var mAudioTrackIndex: Int = -1
    private var mVideoTrackIndex: Int = -1
    private var mDestFile: File? = null
    private var mWorkerThread: SThread? = null

    fun getDestFile() = mDestFile
    override fun onMediaEncoderWrapperStart() {
        Log.i(TAG, "onMediaEncoderWrapperStart: ")
    }

    override fun onMediaEncoderWrapperFormatChanged(
        audioFormat: MediaFormat,
        videoFormat: MediaFormat?
    ) {
        Log.i(TAG, "onMediaEncoderWrapperFormatChanged: $audioFormat,$videoFormat")
        if (!createMediaMuxer(audioFormat, videoFormat)) {
            stopInternal()
        }
    }

    override fun onMediaEncoderWrapperDataOutput(
        isAudio: Boolean,
        bufferInfo: BufferInfo,
        byteBuffer: ByteBuffer
    ) {
        if (!writeTrackData(isAudio, bufferInfo, byteBuffer)) {
            stopInternal()
        }
    }

    override fun onMediaEncoderWrapperError(isAudio: Boolean, error: MediaPipeError) {
        Log.i(TAG, "onMediaEncoderWrapperError: failed ,${error.name},${error.ordinal}")
        stopInternal()
    }

    override fun runAsync(method: () -> Unit) {
        mWorkerThread?.invokeAsync(method)
    }

    private fun runBlock(method: () -> Unit) {
        mWorkerThread?.invoke(method)
    }

    private fun runBoolean(method: () -> Boolean): Boolean {
        return mWorkerThread?.invokeBoolean(method) == true
    }

    private fun runFront(method: () -> Unit) {
        mWorkerThread?.invokeFrontAsync(method)
    }


    fun getDurationMillis() = mLastReceiveTimeMillis - mFirstReceiveTimeMillis

    private fun getRecordFilePath(timeMillis: Long): File {
        val deviceID = "1000088"
        val policeID = "1111123"
        val formatter = SimpleDateFormat("yyyyMMdd_HHmmss")
        val dateStr = formatter.format(Date(timeMillis))
        val context = KingStone.getContext()
        var baseFile = SDCardHelper.getSDCardStorageDirectory(context = context)
        if(baseFile == null) baseFile = SDCardHelper.getInternalStorageDirectory()
        val subDirectory = if(mVideoEncoderParam == null) "audio" else "video"

        val destFile = File(baseFile, "/${subDirectory}/DSJ_${deviceID}_${policeID}_${dateStr}.mp4")
        if(!destFile.parentFile.exists()) destFile.parentFile.mkdirs()
        mDestFile = destFile
        Log.i(TAG, "getRecordFilePath: destFile=${destFile.absolutePath}")
        return destFile
    }


    private fun createMediaMuxer(
        audioFormat: MediaFormat,
        videoFormat: MediaFormat? = null
    ): Boolean {
        if (mMediaMuxer != null) throw RuntimeException("should release mMediaMuxer first")
        val timeMillis = System.currentTimeMillis()
        val file = getRecordFilePath(timeMillis).absolutePath
        try {
            mMediaMuxer = MediaMuxer(file, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            mAudioTrackIndex = mMediaMuxer!!.addTrack(audioFormat)
            if (videoFormat != null)
                mVideoTrackIndex = mMediaMuxer!!.addTrack(videoFormat)
            mMediaMuxer?.start()
        } catch (ex: Exception) {
            Log.e(TAG, "createMediaMuxer: failed", ex)
            return false
        }
        return true
    }

    private fun writeTrackData(
        isAudio: Boolean,
        bufferInfo: BufferInfo,
        byteBuffer: ByteBuffer
    ): Boolean {
        try {
            if (isAudio) {
                if (mAudioTrackIndex == -1) throw RuntimeException("mAudioTrackIndex is not added")
                mMediaMuxer?.writeSampleData(mAudioTrackIndex, byteBuffer, bufferInfo)
            } else {
                if (mVideoTrackIndex == -1) throw RuntimeException("mVideoTrackIndex is not added")
                mMediaMuxer?.writeSampleData(mVideoTrackIndex, byteBuffer, bufferInfo)
            }
        } catch (ex: Exception) {
            Log.e(TAG, "writeTrackData: failed", ex)
            return false
        }
        return true
    }

    private fun releaseMediaMuxer() {
        if (mMediaMuxer != null) {
            try {
                mMediaMuxer?.stop()
                mMediaMuxer?.release()
            } catch (ex: Exception) {
                Log.e(TAG, "releaseMediaMuxer: failed", ex)
            }
            Log.i(TAG, "releaseMediaMuxer: destFile=${mDestFile?.absolutePath}")
            mMediaMuxer = null
            mDestFile = null
            mVideoTrackIndex = -1
            mAudioTrackIndex = -1
        }
    }

    fun startVideo(): Boolean {
        startWorkerThread()
        return createEncoderWrapper(AudioEncodeParam(), VideoEncodeParam())
    }

    fun startAudio(): Boolean {
        startWorkerThread()
        return createEncoderWrapper(AudioEncodeParam(), null)
    }

    fun hasVideo() = mVideoEncoderParam!=null
    fun isRunning() = mHasStarted.get()

    fun stop() {
        runBlock {
            stopInternal()
        }
    }

    private fun startWorkerThread() {
        if (mWorkerThread == null) {
            mWorkerThread = SThread()
            mWorkerThread?.start()
        }
    }

    private fun stopInternal() {
        releaseEncoderWrapper()
        releaseMediaMuxer()
        mWorkerThread?.stop()
        mWorkerThread = null
    }
}