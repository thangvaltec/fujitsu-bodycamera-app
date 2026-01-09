package com.bodycamera.ba.codec.filter

import android.content.Context
import android.graphics.Bitmap

import android.opengl.GLES20
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.util.GlHelper
import com.bodycamera.ba.util.WaterMarkHelper
import com.bodycamera.tests.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*


class WatermarkFilter(context: Context): BaseFilter(context),IOnFilterCallback {

    override fun onFilterCreate(outputSize: MySize, videoAlign: VideoAlign) {
        mOutputSize = outputSize
        mVideoAlign = videoAlign
        initWaterMark()
    }

    override fun onFilterDrawTexture(textureId: Int, textureSize: MySize): Int {
        drawWatermark(textureSize)
        return 0
    }

    override fun onFilterRelease() {

        releaseBitmapTexture()
        release()
    }

    private var mBitmapUpdateTime = -1L
    //文字水印bitmap
    private var mBitmap: Bitmap? = null
    private var mBitmapTextureId:Int = -1


    private fun initWaterMark(){
        //读取顶点坐标
        mVertexBuffer = ByteBuffer.allocateDirect(vertexFloatArray.size * 4).order(
            ByteOrder.nativeOrder())
            .asFloatBuffer().put(vertexFloatArray)
        mVertexBuffer?.position(0)

        //从读取纹理坐标
        mCoordsBuffer = ByteBuffer.allocateDirect(coordsFloatArray.size * 4).order(
            ByteOrder.nativeOrder())
            .asFloatBuffer().put(coordsFloatArray)
        mCoordsBuffer?.position(0)


        val vertexShader: String = GlHelper.getSourceFromRaw(mContext, R.raw.simple_vertex)
        val fragmentShader: String = GlHelper.getSourceFromRaw(mContext, R.raw.simple_fragment)
        mProgramId = GlHelper.createProgram(vertexShader, fragmentShader)
        mAPositionHandle = GLES20.glGetAttribLocation(mProgramId, "aPosition")
        mATextureCameraHandle = GLES20.glGetAttribLocation(mProgramId, "aTextureCoord")
        //GLES20.glEnable(GLES20.GL_BLEND)
        //GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun releaseBitmapTexture(){
        GlHelper.deleteTextureId(mBitmapTextureId)
        mBitmapTextureId = -1
    }

    /**
     * 水印位置/左上/右上/左下/右下
     */
    private fun createTextWatermark(width: Int,height: Int){
        var currentTime = System.currentTimeMillis()
        if(currentTime - mBitmapUpdateTime < 1000){
            return
        }
        mBitmapUpdateTime = currentTime
        releaseBitmapTexture()
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val dateString = formatter.format(Date(currentTime))
        var fontSize = WaterMarkHelper.FONT_SIZE
        var fontColor = WaterMarkHelper.FONT_COLOR
        var bgColorEnable = false
        var bgColor = ""
        /*var fontSizeRatio = (mOutputWidth.toFloat() / Constants.TEXT_WIDTH_FACTOR * 16)
        fontSize = (fontSize - fontSizeRatio).toInt()*/
        fontSize = (fontSize * mOutputSize.width.toFloat() / 2560 + 1f).toInt()

        if(mBitmapTextureId == -1) mBitmapTextureId = GlHelper.loadBitmapTexture()

        val textMark = WaterMarkHelper.getWaterMarkFormat(dateString)
        mBitmap = GlHelper.createTextImage(textMark,fontSize,fontColor,bgColor,1)
        if(mBitmap !=null) {
            GlHelper.fillBitmapTexture(mBitmapTextureId, mBitmap!!)
            val floatArray = WaterMarkHelper.getWaterMarkLocation(
                mBitmap!!.width,
                mBitmap!!.height,
                mOutputSize.width,
                mOutputSize.height
            )
            mVertexBuffer?.clear()
            mVertexBuffer?.put(floatArray)
        }
        mBitmap?.recycle()
        mBitmap = null
    }

   private fun drawWatermark(cameraSize: MySize) {
        createTextWatermark(cameraSize.width,cameraSize.height)
        GLES20.glUseProgram(mProgramId)
        //GLES20.glClearColor(1.0f, 1.0f, 1.0f, 0.6f)
        //GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        mVertexBuffer?.position(0)
        GLES20.glVertexAttribPointer(
            mAPositionHandle, 2, GLES20.GL_FLOAT, false,
            8, mVertexBuffer
        )
        GLES20.glEnableVertexAttribArray(mAPositionHandle)

        mCoordsBuffer?.position(0)
        GLES20.glVertexAttribPointer(
            mATextureCameraHandle, 2, GLES20.GL_FLOAT, false,
            8, mCoordsBuffer
        )
        GLES20.glEnableVertexAttribArray(mATextureCameraHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBitmapTextureId)
        //draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }



}