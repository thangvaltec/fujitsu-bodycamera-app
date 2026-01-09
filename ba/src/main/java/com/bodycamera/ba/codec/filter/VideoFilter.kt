package com.bodycamera.ba.codec.filter

import android.content.Context
import android.opengl.GLES20
import com.bodycamera.ba.data.IOnFilterCallback
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.util.GlHelper
import com.bodycamera.tests.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoFilter(context: Context): BaseFilter(context), com.bodycamera.ba.codec.filter.IOnFilterCallback {
    override fun onFilterCreate(outputSize: MySize, videoAlign: VideoAlign) {
        mOutputSize = outputSize
        mVideoAlign = videoAlign
        mWatermarkFilter = WatermarkFilter(mContext)
        mWatermarkFilter?.onFilterCreate( outputSize, videoAlign)
        initVideo()
    }

    override fun onFilterDrawTexture(textureId: Int, textureSize: MySize): Int {
        drawWatermark(textureId,textureSize)
        return 0
    }

    override fun onFilterRelease() {
        release()
        mWatermarkFilter?.release()
        mWatermarkFilter= null
    }

    private var mWatermarkFilter:WatermarkFilter?=null

    private fun initVideo(){
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
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    fun drawWatermark(textureId:Int,cameraSize: MySize) {
        processRenderMode(cameraSize)
        //GLES20.glClearColor(1.0f, 1.0f, 1.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(mProgramId)
        GLES20.glViewport(0, 0, mOutputSize.width, mOutputSize.height)

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

        //draw
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
        mWatermarkFilter?.onFilterDrawTexture(textureId,cameraSize)

    }

}