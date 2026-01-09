package com.bodycamera.ba.tools

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.opengl.EGLSurface
import android.util.Log
import android.view.Surface
import com.bodycamera.ba.camera.CameraManager
import com.bodycamera.ba.codec.filter.Camera2Filter
import com.bodycamera.ba.codec.filter.SurfaceViewFilter
import com.bodycamera.ba.codec.filter.VideoFilter
import com.bodycamera.ba.data.IOnFilterCallback
import com.bodycamera.ba.data.IOnVideoFilterCallback
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.data.VideoFilterContext
import com.bodycamera.ba.egl.EglCore
import com.bodycamera.ba.egl.EglCore2
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicInteger

class SurfaceWrapper:SurfaceTexture.OnFrameAvailableListener {
    companion object{
        const val TAG = "SurfaceWrapper"
    }

    /**
     * if mCameraRefCount == 1 ,should start camera,
     *    mCameraRefCount == 0 ,should release camera
     *    mCameraRefCount > 1  ,ignore ,means 1+ features request for Camera
     */
    private var mCameraRefCount = AtomicInteger(0)
    private var mEglCore:EglCore2?=null
    private var mCamera2Filter:Camera2Filter?=null
    private var mCameraEGLSurface:EGLSurface?=null
    private var mSurfaceFilter:SurfaceViewFilter?=null
    private var mVideoFilters = mutableListOf<VideoFilterContext>()
    private var mSurfaceEglSurface:EGLSurface?=null
    private var mWorkThread:SThread?=null
    private var mCameraType = CameraManager.CameraType.BACK_CAMERA

    private val mCameraManager  by lazy {
        CameraManager()
    }

    fun setCameraAntiShakeFlag(flag:Boolean){
        KingStone.checkRunInApiThread()
        mCameraManager.setAntiShakeFlag(flag)
    }

    private fun startWorkerThread(){
        if(mWorkThread == null){
            mWorkThread = SThread()
            mWorkThread?.start()
        }
    }

    fun getTurnGrayFlag():Boolean = mCameraManager.getTurnGrayFlag()

    fun createCameraFilter(cameraSize: MySize){
        KingStone.checkRunInApiThread()
        runBlock {
            val context = KingStone.getContext()
            mCameraEGLSurface = mEglCore!!.createOffWindowSurface()
            mCamera2Filter = Camera2Filter(context)
            mCamera2Filter?.onFilterCreate(cameraSize,VideoAlign.DEFAULT)
            mCamera2Filter?.getSurfaceTexture()?.setOnFrameAvailableListener {
                onFrameAvailable(it)
            }
            val surface = mCamera2Filter!!.getSurface()?:throw RuntimeException("outputSurface is null")
            mCameraManager.setOutputSurface(surface)
        }
    }

    fun releaseCameraFilter(){
        KingStone.checkRunInApiThread()
        runBlock {
            if(mCameraEGLSurface!= null){
                mEglCore?.releaseWindowSurface(mCameraEGLSurface!!)
                mCameraEGLSurface = null
            }
            if(mCamera2Filter!= null){
                mCamera2Filter?.onFilterRelease()
                mCamera2Filter = null
            }
        }
    }

    private fun getNextCameraType():CameraManager.CameraType {
        return when(mCameraType){
            CameraManager.CameraType.BACK_CAMERA -> CameraManager.CameraType.FRONT_CAMERA
            CameraManager.CameraType.FRONT_CAMERA -> CameraManager.CameraType.BACK_CAMERA
            CameraManager.CameraType.USB_CAMERA -> CameraManager.CameraType.BACK_CAMERA
        }
    }

    /**
     * For surfaceView
     */
    private fun createSurfaceFilter(surface: Surface,outputSize: MySize,videoAlign: VideoAlign){
       val context = KingStone.getContext()
       if(mEglCore == null) throw RuntimeException("mEglCore is null")
       mSurfaceEglSurface = mEglCore!!.createWindowSurface(surface)
       mSurfaceFilter = SurfaceViewFilter(context)
       mSurfaceFilter?.onFilterCreate(outputSize,videoAlign)
    }

    private fun releaseSurfaceViewFilter() {
        if(mEglCore == null) throw RuntimeException("mEglCore is null")
        mEglCore?.releaseWindowSurface(mSurfaceEglSurface!!)
        mSurfaceFilter?.onFilterRelease()
        mSurfaceEglSurface = null
        mSurfaceFilter = null
        //val count = mCameraRefCount.decrementAndGet()
    }

    private fun drawTexture(surfaceTexture: SurfaceTexture){
        surfaceTexture.updateTexImage()
        if(mCamera2Filter == null || mEglCore == null) {
            Log.e(TAG, "drawTexture: mCamera2Filter is null or mEglCore == null", )
            return
        }
        //scale camera size ,1 - 10
        //mCamera2Filter?.scale()
        val mySize = mCameraManager.getCamera()?.getPreviewSize()?:return
        mEglCore?.makeCurrent(mCameraEGLSurface!!)
        mCamera2Filter?.enableGrayFilter(mCameraManager.getTurnGrayFlag())
        var textureID = mCamera2Filter!!.onFilterDrawTexture(0,mySize)
        if(mSurfaceFilter!= null){
            mEglCore?.makeCurrent(mSurfaceEglSurface!!)
            mSurfaceFilter?.onFilterDrawTexture(textureID,mySize)
            mEglCore?.swapBuffer(mSurfaceEglSurface!!)
        }
        if(mVideoFilters.isNotEmpty()){
            mVideoFilters.forEach {
                mEglCore?.makeCurrent(it.eglSurface)
                it.videoFilter.onFilterDrawTexture(textureID,mySize)
                mEglCore?.setPresentationTime(it.eglSurface,System.nanoTime())
                mEglCore?.swapBuffer(it.eglSurface)
                it.callback.onVideoFilterTextureAvailable()
            }
        }
        //mEglCore?.swapBuffer(mCameraEGLSurface!!)
    }

    private fun runBlock(method:()->Unit){
        mWorkThread?.invoke (method)
    }

    private fun runAsync(method:()->Unit){
        mWorkThread?.invokeAsync(method)
    }

    private fun runBoolean(method:()->Boolean){
        mWorkThread?.invokeBoolean(method)
    }

    fun getShareContext() = mEglCore?.getEglContext()

    fun create(){
        KingStone.checkRunInApiThread()
        startInternal()
    }

    private fun startInternal(){
      startWorkerThread()
      runBlock{
          if(mEglCore == null) mEglCore = EglCore2(null)
      }
    }

    /**
     * only using for VideoEncoderWrapper
     */
    fun createVideoFilter(callback: IOnVideoFilterCallback,surface: Surface,mySize: MySize,videoAlign: VideoAlign) {
        runBlock {
            if (mEglCore == null) return@runBlock
            val context = KingStone.getContext()
            val eglSurface = mEglCore!!.createWindowSurface(surface)
            val videoFilter = VideoFilter(context)
            videoFilter.onFilterCreate(mySize, videoAlign)
            mVideoFilters.add(
                VideoFilterContext(
                    eglSurface,
                    surface,
                    videoFilter,
                    mySize,
                    callback
                )
            )
        }
        if (mCameraRefCount.incrementAndGet() == 1) {
            KingStone.runAsync {
                mCameraManager.openCamera(KingStone.getContext(), mCameraType)
            }
        }
    }
    /**
     * Only using for VideoEncoderWrapper
     */
    fun releaseVideoFilter(callback: IOnVideoFilterCallback) {
        runBlock {
            if (mEglCore == null) return@runBlock
            val iterator = mVideoFilters.iterator()
            while (iterator.hasNext()) {
                val videoFilterContext = iterator.next()
                if (videoFilterContext.callback == callback) {
                    mEglCore?.releaseWindowSurface(videoFilterContext.eglSurface)
                    videoFilterContext.videoFilter.onFilterRelease()
                    iterator.remove()
                    break
                }
            }
        }
        if (mCameraRefCount.decrementAndGet() == 0) {
            KingStone.runAsync {
                mCameraManager.closeCamera()
            }
        }
    }

    fun createSurfaceView(surface: Surface,mySize: MySize,videoAlign: VideoAlign) {
        runBlock {
            createSurfaceFilter(surface, mySize, videoAlign)
        }
        KingStone.runAsync {
            if (mCameraRefCount.incrementAndGet() == 1) {
                mCameraManager.openCamera(KingStone.getContext(), mCameraType)
            }
        }
    }

    fun releaseSurfaceView() {
        runBlock {
            releaseSurfaceViewFilter()
        }
        KingStone.runAsync {
            if (mCameraRefCount.decrementAndGet() == 0) {
                mCameraManager.closeCamera()
            }
        }
    }
    fun resetCamera(){
        KingStone.runBlock {
            mCameraManager.closeCamera()
            mCameraManager.openCamera(KingStone.getContext(),mCameraType)
        }
    }
    fun switchCamera(){
        KingStone.runAsync {
            mCameraType = getNextCameraType()
            mCameraManager.closeCamera()
            mCameraManager.openCamera(KingStone.getContext(),mCameraType)
        }
    }

    fun setCameraGrayFlag(flag: Boolean){
        KingStone.runAsync {
            mCameraManager?.setTurnGrayFlag(flag)
        }
    }

    fun takePhoto(){
        KingStone.runAsync {
           mCameraManager.takePhoto()
        }
    }

    fun release() {
        runBlock {
            if (mEglCore == null) return@runBlock
            if (mSurfaceEglSurface != null) {
                mEglCore?.releaseWindowSurface(mSurfaceEglSurface!!)
                mSurfaceEglSurface = null
            }
            if (mSurfaceFilter != null) {
                mSurfaceFilter?.onFilterRelease()
                mSurfaceFilter = null
            }
            if (mCameraEGLSurface != null) {
                mEglCore?.releaseWindowSurface(mCameraEGLSurface!!)
                mCameraEGLSurface = null
            }
            if (mCamera2Filter != null) {
                mCamera2Filter?.onFilterRelease()
                mCameraManager.releaseOutputSurface()
                mCamera2Filter = null
            }
            mVideoFilters.forEach {
                val eglSurface = it.eglSurface
                val videoFilter = it.videoFilter
                mEglCore?.releaseWindowSurface(eglSurface)
                videoFilter.onFilterRelease()
            }
            mVideoFilters.clear()
            mEglCore?.release()
            mEglCore = null
            mWorkThread?.stop()
            mWorkThread = null
            mCameraRefCount.set(0)
        }
    }

    override fun onFrameAvailable(surfaceTexture:  SurfaceTexture?) {
        if(surfaceTexture == null) return
        runAsync{
            drawTexture(surfaceTexture)
        }
    }
}