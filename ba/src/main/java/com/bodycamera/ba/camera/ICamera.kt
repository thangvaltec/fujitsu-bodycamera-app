package com.bodycamera.ba.camera

import com.bodycamera.ba.data.MySize


interface ICamera {
    fun getCameraId():Int
    fun openCamera()
    fun stopCamera()
    fun releaseCamera()
    fun getPictureSizeList():List<MySize>
    fun getPreviewSizeList():List<MySize>
    fun getPictureSize():MySize
    fun getPreviewSize():MySize
    fun getFpsRange():IntArray
    fun getFpsRangeList():List<IntArray>
    fun getSensorRotation():Int = 0
    fun takePhoto()
    fun setTorch(status:Boolean) = Unit
    fun getTorch():Boolean =false
    fun getIR():Boolean = false

    fun canIR():Boolean
    fun canFlashlight():Boolean
    fun canFocus():Boolean
    fun canScale():Boolean
    fun getCameraType():CameraManager.CameraType
}
