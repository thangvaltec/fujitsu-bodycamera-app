package com.bodycamera.ba.camera

import android.content.Context
import com.bodycamera.ba.data.MySize

class BackCamera(context: Context, cameraId:Int, cameraManager: CameraManager):
    Camera2Api(context,cameraId, cameraManager) {
    override fun afterClose() {
        //close ir-cut / torch ,if it is on
    }



    override fun detect() {
        //detect camera setting then set
    }

    override fun canIR(): Boolean {
       return true
    }

    override fun canFlashlight(): Boolean {
        return true
    }

    override fun canFocus(): Boolean {
        return true
    }

    override fun canScale(): Boolean {
        return true
    }

    override fun getCameraType(): CameraManager.CameraType {
       return CameraManager.CameraType.BACK_CAMERA
    }

    override fun getPreviewSize(): MySize {
        return MySize(1920,1080)
    }

    override fun getPictureSize(): MySize {
        return MySize(1920,1080)
    }

    override fun getPictureSizeList(): List<MySize> {
        return mutableListOf(getPictureSize())
    }

    override fun getPreviewSizeList(): List<MySize> {
        return mutableListOf(getPreviewSize())
    }
    override fun getFpsRangeList(): List<IntArray> {
        return mutableListOf(getFpsRange())
    }

    override fun getFpsRange(): IntArray {
        return intArrayOf(30000,30000)
    }

}