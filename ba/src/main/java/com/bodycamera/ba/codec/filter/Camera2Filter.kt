package com.bodycamera.ba.codec.filter

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.view.Surface
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.util.GlHelper
import com.bodycamera.tests.R
import java.nio.ByteBuffer
import java.nio.ByteOrder


class Camera2Filter(context: Context) : BaseFilter(context),IOnFilterCallback {

    private var mFilterFlag :Int = 0
    private var grayValue :Int = 0
    private var mScale = 1.0f

    private fun updateMatrix() {
        mMVPMatrix = floatArrayOf(
            1f,0f,0f,0f,
            0f,1f,0f,0f,
            0f,0f,1f,0f,
            0f,0f,0f,1f
        )
        mSTMatrix = floatArrayOf(
            1f,0f,0f,0f,
            0f,1f,0f,0f,
            0f,0f,1f,0f,
            0f,0f,0f,1f
        )
    }

    fun scale(ratio:Float){
        var x = 1 * ratio
        var y= 1 * ratio
        var z= 1 * ratio
        mMVPMatrix = floatArrayOf(
            1f,0f,0f,0f,
            0f,1f,0f,0f,
            0f,0f,1f,0f,
            0f,0f,0f,1f
        )
        Matrix.scaleM(mMVPMatrix,0,x,y,z)
    }

    private fun initCamera(){
        mVertexBuffer = ByteBuffer.allocateDirect(vertexFloatArray.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(vertexFloatArray)
        mVertexBuffer?.position(0)
        mCoordsBuffer = ByteBuffer.allocateDirect(coordsFloatArray.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(coordsFloatArray)
        mCoordsBuffer?.position(0)

        val vertexShader: String = GlHelper.getSourceFromRaw(mContext, R.raw.camera_vertex)
        val fragmentShader: String = GlHelper.getSourceFromRaw(mContext, R.raw.camera_fragment)
        mProgramId = GlHelper.createProgram(vertexShader, fragmentShader)

        mAPositionHandle = GLES20.glGetAttribLocation(mProgramId, "aPosition")
        mATextureCameraHandle = GLES20.glGetAttribLocation(mProgramId, "aTextureCoord")
        mUMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramId, "uMVPMatrix")
        mUSTMatrixHandle = GLES20.glGetUniformLocation(mProgramId, "uSTMatrix")


        mFilterFlag = GLES20.glGetUniformLocation(
            mProgramId,
            "filterFlag"
        )
        val textureIds = IntArray(1)
        //camera texture
        GlHelper.createExternalTextures(textureIds.size, textureIds, 0)
        mSurfaceTexture = SurfaceTexture(textureIds[0])
        mTextureId = textureIds[0]
        mSurfaceTexture?.setDefaultBufferSize(mOutputSize.width, mOutputSize.height)
        mSurface = Surface(mSurfaceTexture)
        initFBO(mOutputSize.width, mOutputSize.height)
    }




    private  fun drawCamera(textureSize: MySize):Int {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId)
        mSurfaceTexture?.getTransformMatrix(mSTMatrix)
        GLES20.glViewport(0, 0, textureSize.width, textureSize.height)
        GLES20.glUseProgram(mProgramId)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

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

        GLES20.glUniformMatrix4fv(mUMVPMatrixHandle, 1, false, mMVPMatrix, 0)
        GLES20.glUniformMatrix4fv(mUSTMatrixHandle, 1, false, mSTMatrix, 0)
        GLES20.glUniform1i(mFilterFlag, grayValue)

        //camera
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId)
        //draw
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(mAPositionHandle)
        GLES20.glDisableVertexAttribArray(mATextureCameraHandle)

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        return mFboTexId
    }


    fun enableGrayFilter(gray:Boolean){
        grayValue = if(gray) 100 else 0
    }

    override fun onFilterCreate(outputSize: MySize, videoAlign: VideoAlign) {
        mOutputSize = outputSize
        mVideoAlign = videoAlign
        updateMatrix()
        initCamera()
    }

    override fun onFilterDrawTexture(textureId: Int, textureSize: MySize): Int {
        return drawCamera(textureSize)
    }

    override fun onFilterRelease() {
        release()
    }
}