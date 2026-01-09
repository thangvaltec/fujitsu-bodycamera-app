package com.bodycamera.ba.camera

import android.content.Context
import android.graphics.Camera
import android.view.Surface
import com.bodycamera.ba.tools.KingStone
import java.lang.RuntimeException

class CameraManager {
    enum class CameraType{
        BACK_CAMERA,
        FRONT_CAMERA,
        USB_CAMERA
    }

    private var mOutputSurface:Surface?=null
    private var mCamera:ICamera?=null
    private var mCameraType: CameraType = CameraType.BACK_CAMERA
    private var mAntiShakeFlag:Boolean = false
    private var mTurnGrayFlag:Boolean = false

    fun setAntiShakeFlag(flag:Boolean){
        if(mAntiShakeFlag == flag) return
        mAntiShakeFlag = flag
    }

    fun getAntiShakeFlag() = mAntiShakeFlag

    fun getCamera():ICamera?=mCamera

    fun getNextCameraType(): CameraType {
        return when (mCameraType ){
            CameraType.BACK_CAMERA -> CameraType.FRONT_CAMERA
            CameraType.FRONT_CAMERA -> CameraType.BACK_CAMERA
            else -> CameraType.BACK_CAMERA
        }
    }

    fun openCamera(context: Context, cameraType:CameraType):Boolean{
        if(mCamera != null) throw RuntimeException("should stop camera before openCamera")
        mCamera = when(cameraType){
            CameraType.BACK_CAMERA -> {
                BackCamera(context,0,this)
            }
            CameraType.FRONT_CAMERA -> {
                FrontCamera(context,1,this)
            }
            CameraType.USB_CAMERA -> {
                throw RuntimeException("not implemented")
            }
        }
        KingStone.getSurfaceWrapper()?.createCameraFilter(mCamera!!.getPreviewSize())
        mCamera?.openCamera()
        mCameraType = cameraType
        return true
    }

    fun closeCamera():Boolean{
        if(mCamera!= null){
            mCamera!!.stopCamera()
            Thread.sleep(200)
            KingStone.getSurfaceWrapper()?.releaseCameraFilter()
            mCamera = null
        }
        return true
    }

    fun setOutputSurface(surface: Surface){
        mOutputSurface = surface
    }
    fun releaseOutputSurface(){
        mOutputSurface = null
    }

    fun getOutputSurface():Surface{
        if(mOutputSurface == null) throw RuntimeException("please set outputSurface first")
        return mOutputSurface!!
    }

    fun setTurnGrayFlag(flag:Boolean){
        mTurnGrayFlag = flag
    }
    fun getTurnGrayFlag() = mTurnGrayFlag

    fun takePhoto(){
        mCamera?.takePhoto()
    }


}