package com.bodycamera.ba.codec.filter

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLES30
import android.view.Surface
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.util.GlHelper
import com.bodycamera.tests.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.min

interface IOnFilterCallback{
   fun onFilterCreate(outputSize: MySize,videoAlign: VideoAlign)
   fun onFilterDrawTexture(textureId:Int,textureSize:MySize):Int
   fun onFilterRelease()
   fun onFilterChanged(outputSize: MySize) = Unit
}
abstract class BaseFilter(protected var mContext: Context) {
   protected var mOutputSize = MySize(1,1)
   protected var mVideoAlign = VideoAlign.DEFAULT

   //FBO对象
   protected var mFboId:Int = -1
   private   var mRboId:Int = -1
   protected var mFboTexId:Int = -1
   protected var mTextureId:Int = -1
   protected var mProgramId:Int = -1

   protected var mTextureWidth = 0
   protected var mTextureHeight = 0

   protected var mMVPMatrix = FloatArray(16)
   protected var mSTMatrix = FloatArray(16)

   //vbo fbo
   private var vboId:Int = 0

   protected var mVertexBuffer: FloatBuffer? = null
   protected var mCoordsBuffer: FloatBuffer? = null


   protected var mSurfaceTexture: SurfaceTexture?= null
   protected var mSurface: Surface?= null

   protected var mAPositionHandle = 0
   protected var mATextureCameraHandle = 0
  protected var  mUMVPMatrixHandle = 0
  protected var  mUSTMatrixHandle = 0

   private var mStarted :Boolean = false


   /**PBO***/
   private var mPboIds: IntBuffer? = null
   private var mPboSize = 0

   private val mPixelStride = 4 //RGBA 4字节
   private var mRowStride = 0 //对齐4字节

   private var mPboIndex = 0
   private var mPboNewIndex = 0
   //first time to execute PBO operation ,due to dual PBO switching
   private var mIsFirstPBO = true
   /***END PBO *****/



   fun getOrientation(): Int {
      return 0
   }





   protected var mFragmentShaderID = R.raw.simple_fragment
   protected var mVertexShaderID = R.raw.simple_vertex



protected fun init(){
      //读取顶点坐标
      mVertexBuffer = ByteBuffer.allocateDirect(vertexFloatArray.size * 4).order(ByteOrder.nativeOrder())
         .asFloatBuffer().put(vertexFloatArray)
      mCoordsBuffer?.position(0)

      //从读取纹理坐标
      mCoordsBuffer = ByteBuffer.allocateDirect(coordsFloatArray.size * 4).order(ByteOrder.nativeOrder())
         .asFloatBuffer().put(coordsFloatArray)
      mCoordsBuffer?.position(0)

      val vertexShader: String = GlHelper.getSourceFromRaw(mContext, mVertexShaderID)
      val fragmentShader: String = GlHelper.getSourceFromRaw(mContext, mFragmentShaderID)
      mProgramId = GlHelper.createProgram(vertexShader, fragmentShader)
      mAPositionHandle = GLES20.glGetAttribLocation(mProgramId, "aPosition")
      mATextureCameraHandle = GLES20.glGetAttribLocation(mProgramId, "aTextureCoord")
      //mUMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramId, "uMVPMatrix")
      //mUSTMatrixHandle = GLES20.glGetUniformLocation(mProgramId, "uSTMatrix")

      val textureIds = IntArray(1)
      //normal texture
      GlHelper.createTextures(textureIds.size, textureIds, 0)
      mTextureId = textureIds[0]
   }


   fun initFBO(context: Context?){
      init()
      initFBO(mOutputSize.width,mOutputSize.height)
   }

   fun getSurfaceTexture(): SurfaceTexture? {
      return mSurfaceTexture
   }

   fun getSurface(): Surface? {
      return mSurface
   }

   fun deleteTexture(textureID:Int){
      val textureIds = intArrayOf(textureID)
      GLES20.glDeleteTextures(textureIds.size,textureIds,0)
   }


   fun release() {
      if(mProgramId != -1) {
         deleteTexture(mTextureId)
         deleteFBO()
         GLES20.glDeleteProgram(mProgramId)
         mSurfaceTexture?.release()
         mSurfaceTexture = null
         mSurface?.release()
         mSurface = null
         mProgramId = -1
         mFixOutput = false
      }
   }



   fun initFBO(width: Int, height: Int) {
      deleteFBO()
      val fboId = intArrayOf(1)
      val rboId = intArrayOf(1)
      val texId = intArrayOf(1)

      GLES20.glGenFramebuffers(1, fboId, 0)
      GLES20.glGenRenderbuffers(1, rboId, 0)
      GLES20.glGenTextures(1, texId, 0)
      GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rboId[0])
      GLES20.glRenderbufferStorage(
         GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height
      )
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId[0])
      GLES20.glFramebufferRenderbuffer(
         GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
         GLES20.GL_RENDERBUFFER, rboId[0]
      )
      GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId[0])
      GLES20.glTexParameteri(
         GLES20.GL_TEXTURE_2D,
         GLES20.GL_TEXTURE_MIN_FILTER,
         GLES20.GL_LINEAR
      )
      GLES20.glTexParameteri(
         GLES20.GL_TEXTURE_2D,
         GLES20.GL_TEXTURE_MAG_FILTER,
         GLES20.GL_LINEAR
      )
      GLES20.glTexParameteri(
         GLES20.GL_TEXTURE_2D,
         GLES20.GL_TEXTURE_WRAP_S,
         GLES20.GL_CLAMP_TO_EDGE
      )
      GLES20.glTexParameteri(
         GLES20.GL_TEXTURE_2D,
         GLES20.GL_TEXTURE_WRAP_T,
         GLES20.GL_CLAMP_TO_EDGE
      )
      GLES20.glTexImage2D(
         GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA,
         GLES20.GL_UNSIGNED_BYTE, null
      )
      GLES20.glFramebufferTexture2D(
         GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
         GLES20.GL_TEXTURE_2D, texId[0], 0
      )
      val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
      if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
         throw RuntimeException("FrameBuffer uncompleted code: $status")
      }
      mFboId = fboId[0]
      mRboId = rboId[0]
      mFboTexId = texId[0]
   }

   fun deleteFBO(){
      val fboArray = intArrayOf(mFboId)
      val rboArray = intArrayOf(mRboId)
      val fboTextureArray = intArrayOf(mFboTexId)
      GLES20.glDeleteTextures(fboTextureArray.size,fboTextureArray,0)
      GLES20.glDeleteRenderbuffers(rboArray.size,rboArray,0)
      GLES20.glDeleteFramebuffers(fboArray.size,fboArray,0)
      mFboId = -1
      mRboId = -1
      mFboTexId = -1
   }


   /*
   * 1.如果摄像头分辨率和原型分辨率不对的话，需要重置下opengl坐标
   */
   private var mFixOutput = false
   private fun fixOutput(textureSize: MySize){
      mFixOutput = true
      val textureWidth = textureSize.width
      val textureHeight = textureSize.height
      val outputWidth =mOutputSize.width
      val outputHeight = mOutputSize.height
      val outputRate = outputWidth.toFloat() / outputHeight
      val textureRate = textureWidth.toFloat() / textureHeight

      //Log.d(TAG, "fixOutput: .....${outputRate} == ${textureRate}")
      mVertexBuffer?.clear()
      mCoordsBuffer?.clear()
      mCoordsBuffer?.put(coordsFloatArray)
      if(outputRate == textureRate){
         mVertexBuffer?.put(floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f))
         return
      }
      var tpVertexFloatArray : FloatArray
      //首先铺满屏幕宽度为目标
      var nRate = outputWidth.toFloat()/textureWidth
      var nHeight = (textureHeight * nRate).toInt()
      if(nHeight > outputHeight){
         nRate = outputHeight.toFloat()/textureHeight
         var nWidth = (textureWidth * nRate).toInt()
         nRate = nWidth.toFloat() / outputWidth
         nRate = min(nRate,1f)
         tpVertexFloatArray= floatArrayOf(
            -nRate, -1f,
            nRate, -1f,
            -nRate, 1f,
            nRate, 1f)
         //Log.d("8888", "fixOutput1 output($outputWidth,$outputHeight),camera(${textureWidth},${textureHeight}), nRate = $nRate ")
      }else{
         nRate = nHeight.toFloat()/ outputHeight
         nRate = min(nRate,1f)
         //Log.d("8888", "fixOutput2 output($outputWidth,$outputHeight),camera(${textureWidth},${textureHeight}), nRate = $nRate ")
         tpVertexFloatArray= floatArrayOf(
            -1f, -nRate,
            1f, -nRate,
            -1f, nRate,
            1f, nRate)
      }
      mVertexBuffer?.put(tpVertexFloatArray)
   }

   fun drawFBO(textureId:Int,previewWidth:Int,previewHeight:Int):Int{

      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFboId)
      GLES20.glViewport(0, 0, previewWidth, previewHeight)
      GLES20.glUseProgram(mProgramId)
      GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
      GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

      GLES20.glEnableVertexAttribArray(mAPositionHandle)
      mVertexBuffer?.position(0)
      GLES20.glVertexAttribPointer(
         mAPositionHandle, 2, GLES20.GL_FLOAT, false,
         8, mVertexBuffer
      )

      GLES20.glEnableVertexAttribArray(mATextureCameraHandle)
      mCoordsBuffer?.position(0)
      GLES20.glVertexAttribPointer(
         mATextureCameraHandle, 2, GLES20.GL_FLOAT, false,
         8, mCoordsBuffer
      )

      //camera
      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
      //draw
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
      return mFboTexId
   }


   private fun handleDisplayFitWidthMode(textureSize: MySize) {
      val textureWidth = textureSize.width
      val textureHeight = textureSize.height
      val outputWidth = mOutputSize.width
      val outputHeight = mOutputSize.height
      val outputRate = outputWidth.toFloat() / outputHeight
      val textureRate = textureWidth.toFloat() / textureHeight
      //如果摄像头给的分辨率和输出分辨率比率是一样的，那就没有必要修改opengl顶点坐标
      mCoordsBuffer?.clear()
      mVertexBuffer?.clear()
      if (outputRate == textureRate) {
         mVertexBuffer?.put(vertexFloatArray)
         mCoordsBuffer?.put(coordsFloatArray)
         return
      }
      var nRate = outputRate / textureRate
      if(nRate < 1) {
         //It shrinks to nRate of its original size in the Y axis
         mCoordsBuffer?.put(coordsFloatArray)
         val tpVertexFloatArray = floatArrayOf(
            -1f, -nRate,
            1f, -nRate,
            -1f, nRate,
            1f, nRate)
         mVertexBuffer?.put(tpVertexFloatArray)
      }else{
         //It expands by a factor of nRate on the Y-axis
         mVertexBuffer?.put(vertexFloatArray)
         nRate = 0.5f / nRate
         val tpCoordsFloatArray = floatArrayOf(
            0f, 0.5f + nRate,
            1f, 0.5f + nRate,
            0f, 0.5f - nRate,
            1f, 0.5f - nRate
         )
         mCoordsBuffer?.put(tpCoordsFloatArray)
      }
   }

   private  fun handleDisplayFitHeightMode(textureSize: MySize) {
      val textureWidth = textureSize.width
      val textureHeight = textureSize.height
      val outputWidth = mOutputSize.width
      val outputHeight = mOutputSize.height
      val outputRate = outputWidth.toFloat() / outputHeight
      val textureRate = textureWidth.toFloat() / textureHeight
      //If the resolution given by the camera is the same as the output resolution ratio,
      //there is no need to modify the opengl vertex coordinates
      //Log.d(TAG, "fixOutput: .....${outputRate} == ${textureRate}")
      mCoordsBuffer?.clear()
      mVertexBuffer?.clear()
      if (outputRate == textureRate) {
         mVertexBuffer?.put(vertexFloatArray)
         mCoordsBuffer?.put(coordsFloatArray)
         return
      }
      //铺满屏幕高度为目标
      var nRate =  textureRate / outputRate
      if(nRate < 1) {
         //It shrinks to nRate of its original size in the Y axis
         mCoordsBuffer?.put(coordsFloatArray)
         val tpVertexFloatArray = floatArrayOf(
            -nRate, -1f,
            nRate, -1f,
            -nRate, 1f,
            nRate, 1f)
         mVertexBuffer?.put(tpVertexFloatArray)
      }else{
         //It expands by a factor of nRate on the Y-axis
         mVertexBuffer?.put(vertexFloatArray)
         nRate = 0.5f / nRate
         val tpCoordsFloatArray = floatArrayOf(
            0.5f-nRate, 1f,
            0.5f+nRate, 1f,
            0.5f-nRate, 0f,
            0.5f+nRate, 0f
         )
         mCoordsBuffer?.put(tpCoordsFloatArray)
      }
   }

   private fun handleDisplayFitFullScreenMode(textureSize: MySize){
      val textureWidth = textureSize.width
      val textureHeight = textureSize.height
      val outputWidth = mOutputSize.width
      val outputHeight = mOutputSize.height
      val outputRate = outputWidth.toFloat() / outputHeight
      val textureRate = textureWidth.toFloat() / textureHeight
      if(outputHeight > textureRate){
         handleDisplayFitHeightMode(textureSize)
      }else{
         handleDisplayFitWidthMode(textureSize)
      }
   }

   fun draw(textureId:Int,textureSize: MySize){
      processRenderMode(textureSize)
      GLES20.glViewport(0, 0, mOutputSize.width, mOutputSize.height)
      GLES20.glUseProgram(mProgramId)
      GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
      GLES20.glClearColor(0f, 0f, 0f, 0f)



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

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
      //draw
      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
      GLES20.glUseProgram(0)
   }

   fun setCoordMatrix(matrix16F:FloatArray){
      mSTMatrix = matrix16F
   }

   fun initTextureMatrix(context: Context?){
      mStarted = true
      init()
      mUSTMatrixHandle = GLES20.glGetUniformLocation(mProgramId, "textureTransform")
   }

   protected fun processRenderMode(textureSize: MySize){
      if(mTextureWidth == textureSize.width && mTextureHeight == textureSize.height)
         return
      else {
         mTextureWidth = textureSize.width
         mTextureHeight = textureSize.height
         when (mVideoAlign) {
            VideoAlign.DEFAULT -> {
               fixOutput(textureSize)
            }
            VideoAlign.Y -> {
               handleDisplayFitHeightMode(textureSize)
            }
            VideoAlign.X -> {
               handleDisplayFitWidthMode(textureSize)
            }
            VideoAlign.AUTO -> {
               handleDisplayFitFullScreenMode(textureSize)
            }
            else -> {
               throw IllegalArgumentException("COMMON_SURFACE_SHOW_MODE($mVideoAlign) is not supported")
            }
         }
      }
   }

   companion object{
      const val TAG = "BaseFilter"
      val vertexFloatArray = floatArrayOf(
         -1f, -1f,
         1f, -1f,
         -1f, 1f,
         1f, 1f)
      val coordsFloatArray = floatArrayOf(
         0f, 1f,
         1f, 1f,
         0f, 0f,
         1f, 0f
      )
   }

}