package com.bodycamera.ba.camera

import android.content.Context
import com.bodycamera.ba.data.MySize

class FrontCamera(context: Context, cameraId:Int, cameraManager: CameraManager):
    Camera2Api(context,cameraId, cameraManager) {
    override fun afterClose() {
        //nothing
    }


    override fun detect() {
        //here to detect camera fps / previewSize/pictureSize etc.

    }

    override fun getPictureSizeList(): List<MySize> {
        return mutableListOf(getPictureSize())
    }

    override fun getPreviewSizeList(): List<MySize> {
        return mutableListOf(getPreviewSize())
    }

    override fun getPictureSize(): MySize {
        return MySize(1280,960)
    }

    override fun getPreviewSize(): MySize {
        return MySize(1280,960)
    }

    override fun getFpsRange(): IntArray {
        return intArrayOf(15000,30000)
    }

    override fun getFpsRangeList(): List<IntArray> {
        return mutableListOf(getFpsRange())
    }

    override fun canIR(): Boolean {
        return true
    }

    override fun canFlashlight(): Boolean {
        return true
    }

    override fun canFocus(): Boolean {
        return false
    }

    override fun canScale(): Boolean {
       return false
    }

    override fun getCameraType(): CameraManager.CameraType {
        return CameraManager.CameraType.FRONT_CAMERA
    }

}