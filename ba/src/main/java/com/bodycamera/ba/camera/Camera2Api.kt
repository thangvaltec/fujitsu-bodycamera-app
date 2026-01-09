package com.bodycamera.ba.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Range
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.BeepHelper
import com.bodycamera.ba.util.ImageHelper
import com.bodycamera.ba.util.SDCardHelper
import com.bodycamera.ba.util.WaterMarkHelper
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.lang.RuntimeException
import java.util.concurrent.CountDownLatch

abstract class Camera2Api(private val context:Context,cameraId:Int, cameraManager: com.bodycamera.ba.camera.CameraManager): ICamera,
    CameraDevice.StateCallback() {
    protected var mCameraID:Int = cameraId
    protected var mCameraManager: com.bodycamera.ba.camera.CameraManager = cameraManager
    protected var mCameraService: CameraManager? = null
    protected var mSurfaceView: SurfaceView? = null
    protected var mTextureView: TextureView? = null
    protected var mCameraDevice: CameraDevice? = null
    protected var mCameraCaptureSession: CameraCaptureSession? = null
    protected var mImageReader: ImageReader?=null
    protected var mDetectImageReader: ImageReader?=null

    protected var mPictureScaledRatio :Int = 1
    protected var mPreviewRequestBuilder: CaptureRequest.Builder? = null
    protected var mCameraCharacteristics: CameraCharacteristics? = null
    protected var mIsCameraPrepared: Boolean = false
    protected var mHandleThread: HandlerThread? = null
    protected var mHandler: Handler? = null


    protected var mCanFlashLight: Boolean = false
    protected var mCanIR: Boolean = false
    protected var mCanFocus = false
    protected var mFocusMode: String = ""
    protected var mFlashMode: String = ""
    protected var mCanScale = false
    protected var mFpsRange: IntArray? = null


    protected var mPreviewSize: MySize? = null
    protected var mPictureSize: MySize? = null
    protected var mPreviewSizeList = listOf<MySize>()
    protected var mPictureSizeList = listOf<MySize>()
    protected var mFpsRangeList = listOf<IntArray>()

    protected var mIrOpen: Boolean = false
    protected var mTorchOpen: Boolean = false

    private val mPictureFormat = ImageFormat.JPEG

    protected var mZoomLevel = 0f

    abstract fun afterClose()

    override fun getTorch(): Boolean {
        return mTorchOpen
    }

    override fun getIR(): Boolean {
        return mIrOpen
    }

    abstract fun detect()

    override fun getSensorRotation(): Int {
        return mCameraCharacteristics?.get(
            CameraCharacteristics.SENSOR_ORIENTATION
        )?:0
    }

    private fun getRecordSurface(): Surface {
        mIsCameraPrepared = true
        return mCameraManager.getOutputSurface()
    }


    private fun getImageReader(): ImageReader?{
        if(mImageReader == null) {
            val pictureSize = getPictureSize()
            Log.d(TAG, "getImageReader2: pictureSize = (${pictureSize.width},${pictureSize.height})")
            mImageReader = ImageReader.newInstance(
                pictureSize.width,
                pictureSize.height,
                mPictureFormat,
                2
            )
            mImageReader?.setOnImageAvailableListener(mOnImageAvailableListener, mHandler)
        }
        return mImageReader
    }


    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener {

        val image = it.acquireNextImage()
        val buffer = image.planes[0].buffer
        val byteArray = ByteArray(buffer.remaining())

        buffer.get(byteArray)
        var bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

        if(mPictureScaledRatio > 1) {
            val tmpBitmap = Bitmap.createScaledBitmap(
                bitmap,
                image.width * mPictureScaledRatio,
                image.height * mPictureScaledRatio,
                false
            )
            bitmap.recycle()
            bitmap = tmpBitmap
        }
        image.close()


        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height
        val waterMarkText =
            WaterMarkHelper.getWaterMarkText(System.currentTimeMillis(), bitmapWidth)
        val x = 80f
        val textSize = (waterMarkText.mFontSize * if(mPictureScaledRatio > 1) mPictureScaledRatio * 0.42f else 1f).toInt()
        val textColor = waterMarkText.mFontColor
        val content = waterMarkText.mText
        val y = bitmapHeight + 5f - textSize
        val turnGray = KingStone.getSurfaceWrapper()?.getTurnGrayFlag() ?: false
        bitmap = ImageHelper.addTextWatermark(
            bitmap,
            content,
            textSize,
            Color.parseColor(textColor),
            bitmapWidth,
            bitmapHeight,
            true,
            turnGray = turnGray
        )

        val baseFile = SDCardHelper.getSDCardStorageDirectory(context) ?: SDCardHelper.getInternalStorageDirectory()
        val file = File(baseFile,"/images/${System.currentTimeMillis()}.jpg")
        if(!file.parentFile.exists()) file.parentFile.mkdirs()
        var outputStream: BufferedOutputStream? = null
        try {
            if (file == null) {
                Log.e(TAG, "take photo failed ", )
                return@OnImageAvailableListener
            }
            outputStream = BufferedOutputStream(FileOutputStream(file!!))
            val ret = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            BeepHelper.shot(KingStone.getContext())
            Log.i(TAG, "take picture callback success file =${file!!.absolutePath}")
        } catch (ex: FileNotFoundException) {
            BeepHelper.failed(KingStone.getContext())
            Log.e(TAG, "take photo failed", ex)
        } catch (ex: IOException) {
            BeepHelper.failed(KingStone.getContext())
            Log.e(TAG, "take photo failed", ex)
        } finally {
            bitmap.recycle()
            outputStream?.close()
        }
    }


    private fun getPhotoCaptureRequestBuilder(): CaptureRequest.Builder? {
        try {
            val sensorOrientation = mCameraCharacteristics?.get(
                CameraCharacteristics.SENSOR_ORIENTATION
            )!!

            val jpegRotation = getJpgRotation(sensorOrientation)

            val photoRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            val imageReader = getImageReader() ?: return null
            photoRequestBuilder.addTarget(imageReader.surface)
            //photoRequestBuilder.addTarget(getRecordSurface())

            val aeFlash: Int = mPreviewRequestBuilder?.get(CaptureRequest.CONTROL_AE_MODE)!!
            val afMode: Int = mPreviewRequestBuilder?.get(CaptureRequest.CONTROL_AF_MODE)!!
            //val flashMode: Int = mPreviewRequestBuilder?.get(CaptureRequest.FLASH_MODE)!!
            photoRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, aeFlash)
            photoRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, afMode)
            //photoRequestBuilder.set(CaptureRequest.FLASH_MODE, flashMode)
            photoRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegRotation)

            return photoRequestBuilder
        } catch (e: CameraAccessException) {
            Log.e(TAG, "getPhotoCaptureRequest failed ", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "getPhotoCaptureRequest failed ", e)
        }
        return null
    }



    override fun takePhoto() {
        if(mCameraCaptureSession == null) return
        try {
            val photoRequestBuilder = getPhotoCaptureRequestBuilder()
            if(photoRequestBuilder == null){
                Log.e(TAG, "camera2 takePhoto: failed")
                return
            }
            mCameraCaptureSession?.stopRepeating()
            mCameraCaptureSession?.abortCaptures()

            mCameraCaptureSession?.capture(photoRequestBuilder!!.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    startRepeatingRequest()
                }
            }, null)
        }catch (e: CameraAccessException){
            Log.e(TAG, "takePhoto: failed", e)
        }
    }

    protected fun getCameraOutputSizes(clz:Class<*>): List<MySize> {
        val result = mutableListOf<MySize>()
        try {
            if(mCameraCharacteristics == null){
                mCameraCharacteristics = mCameraService?.getCameraCharacteristics(mCameraID.toString())
            }
            val configs = mCameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizeList = mutableListOf(*configs!!.getOutputSizes(clz))
            sizeList.sortWith(Comparator { o1, o2 -> o1.width  - o2.width  })
            //sizeList.reverse()
            sizeList.forEach {
                result.add(MySize(it.width,it.height))
            }
            return result
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        return result
    }

    protected  fun getCameraFpsRanges(): List<IntArray> {
        val result = mutableListOf<IntArray>()
        try {
            if(mCameraCharacteristics == null){
                mCameraCharacteristics = mCameraService?.getCameraCharacteristics(mCameraID.toString())
            }
            val intRanges = mCameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
            if(intRanges?.isNotEmpty() == true){
                intRanges.forEach {
                    val intArray = IntArray(2)
                    intArray[0] = it.lower
                    intArray[1] = it.upper
                    result.add(intArray)
                    Log.d(TAG, "getCameraFpsRanges: (${intArray[0]},${intArray[1]})")
                }
            }
            result.sortWith(Comparator { o1, o2 -> o1[0]  - o2[0]  })
            Log.d(TAG, "getCameraFpsRanges: $result")
            //sizeList.reverse()
            return result
        } catch (e: CameraAccessException) {
            Log.e(TAG, "getCameraFpsRanges: failed",e )
        }
        return result
    }

    protected  fun getCameraOutputSizes(format:Int): List<MySize> {
        val result = mutableListOf<MySize>()
        try {
            if(mCameraCharacteristics == null){
                Log.d(TAG, "Camera2Api: mCameraID=$mCameraID")
                mCameraCharacteristics = mCameraService?.getCameraCharacteristics(mCameraID.toString())
            }
            val configs = mCameraCharacteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizeList = mutableListOf(*configs!!.getOutputSizes(format))
            sizeList.sortWith(Comparator { o1, o2 -> o1.width  - o2.width  })
            //sizeList.reverse()
            sizeList.forEach {
                result.add(MySize(it.width,it.height))
            }
            return result
        } catch (e: CameraAccessException) {
            Log.e(TAG, "getCameraOutputSizes: failed", e)
        }
        return result
    }


    protected fun getPreviewSurface(): Surface? {
        var surface: Surface? = null
        if (mSurfaceView != null) {
            surface = mSurfaceView!!.holder.surface
        } else if (mTextureView != null) {
            val texture: SurfaceTexture = mTextureView?.surfaceTexture!!
            surface = Surface(texture)
        }
        return surface
    }

    private fun startRepeatingRequest(){
        try {
            val captureRequest = getPreviewRequest()
            if (captureRequest != null) {
                mCameraCaptureSession?.setRepeatingRequest(
                    captureRequest,
                    null,
                    mHandler
                )
                Log.i(TAG, "Camera2 onConfigured successfully ")
            } else {
                Log.e(TAG, "Camera2 captureRequest is null ")
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Camera2 getCaptureRequest failed ", e)
        }
    }

    protected fun startPreview() {
        var surfaceList = mutableListOf<Surface>()
        val previewSurface = getPreviewSurface()
        if (previewSurface != null) surfaceList.add(previewSurface)
        surfaceList.add(getRecordSurface())
        if(mImageReader == null) {
            val imageReader = getImageReader()
            if(imageReader!=null) surfaceList?.add(imageReader.surface)
        }
        mCameraDevice?.createCaptureSession(
            surfaceList,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                    this@Camera2Api.mCameraCaptureSession = cameraCaptureSession
                    startRepeatingRequest()
                }
                override fun onConfigureFailed(p0: CameraCaptureSession) {
                    stopInternal()
                    Log.e(TAG, "Camera2 onConfigured failed ,cameraID = $mCameraID")
                }
            },
            mHandler
        )
    }

    fun setTorchInternal(state:Boolean){
        val flashAvailable =
            mCameraCharacteristics?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        if(flashAvailable == true) {
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            if (state) {
                mPreviewRequestBuilder?.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_TORCH
                )
                mCameraCaptureSession?.setRepeatingRequest(mPreviewRequestBuilder!!.build(), null, mHandler)
            } else {
                mPreviewRequestBuilder?.set(
                    CaptureRequest.FLASH_MODE,
                    CaptureRequest.FLASH_MODE_OFF
                )
                mCameraCaptureSession?.setRepeatingRequest(mPreviewRequestBuilder!!.build(), null, mHandler)
            }
            mTorchOpen = state
            Log.d(TAG, "setTorchInternal: state = $state")
        }
    }


    private fun getPreviewRequest(): CaptureRequest? {
        try {
            /**
             * notice : anti-shake only work in with TEMPLATE_RECORD , using TEMPLATE_PREVIEW is not working
             */
            val antiShake = mCameraManager.getAntiShakeFlag()
            mPreviewRequestBuilder = if(antiShake) mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            else mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val fpsRange = getFpsRange()
            val intRange = Range.create(fpsRange[0],fpsRange[1])

            val afMode = getAFMode(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            val aeMode = getAEMode(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0)
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, afMode)
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            mPreviewRequestBuilder?.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, aeMode)
            mPreviewRequestBuilder?.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_IDLE
            )
            deviceSupportedAntiShake()
            //Log.d(TAG, "getPreviewRequest: fpsRange = $intRange")
            mPreviewRequestBuilder?.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,intRange)
            mPreviewRequestBuilder?.addTarget(getRecordSurface())
            if(mDetectImageReader!=null)
                mPreviewRequestBuilder?.addTarget(mDetectImageReader!!.surface)
            return mPreviewRequestBuilder!!.build()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "getCaptureRequest failed ", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "getCaptureRequest failed ", e)
        }
        return null
    }

    private fun deviceSupportedAntiShake() {
        val antiShake = mCameraManager.getAntiShakeFlag()
        if (getCameraType() == com.bodycamera.ba.camera.CameraManager.CameraType.BACK_CAMERA) {
            if (checkVideoStabilizationModel()) {
                if (antiShake) {
                    mPreviewRequestBuilder?.set(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON
                    )
                } else {
                    mPreviewRequestBuilder?.set(
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF
                    )
                }
                Log.i(
                    TAG,
                    "deviceSupportedAntiShake: success,supported Video stabilization mode ,enable=${antiShake}"
                )
            } else {
                Log.i(
                    TAG,
                    "deviceSupportedAntiShake: failed,not supported Video stabilization mode"
                )
            }
        }
    }


    private fun checkVideoStabilizationModel():Boolean{
        var isEISSupported = false
        try {
            mCameraCharacteristics = mCameraService?.getCameraCharacteristics("0")
            val videoStabilizationModes =
                mCameraCharacteristics?.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
            if (videoStabilizationModes != null && videoStabilizationModes.isNotEmpty()) {
                for (mode in videoStabilizationModes) {
                    if (mode == CameraCharacteristics.CONTROL_VIDEO_STABILIZATION_MODE_ON) {
                        isEISSupported = true
                        break
                    }
                }
            }
        }catch (e: CameraAccessException) {
            Log.e(TAG, "checkVideoStabilizationModel failed ", e)
        }catch (e: IllegalStateException) {
            Log.e(TAG, "checkVideoStabilizationModel failed ", e)
        }
        return isEISSupported
    }


    fun getLevelSupported(): Int {
        if (mCameraService == null) return -1
        try {
            mCameraCharacteristics = mCameraService?.getCameraCharacteristics("0")
            return mCameraCharacteristics!!.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        } catch (e: CameraAccessException) {
            Log.e(TAG, "getLevelSupported failed ", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "getLevelSupported failed ", e)
        }
        return -1
    }


    override fun getCameraId():Int {
        return mCameraID
    }


    override fun stopCamera() {
        KingStone.checkRunInApiThread()
        mHandler?.post {
            stopInternal()
        }
    }


    override fun releaseCamera() {
        KingStone.checkRunInApiThread()
        mHandler?.post {
            stopInternal()
        }
    }

    override fun openCamera() {
        KingStone.checkRunInApiThread()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mCameraService =
                context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        }else{
            throw java.lang.RuntimeException("Build.VERSION.SDK_INT(${Build.VERSION.SDK_INT}) < Build.VERSION_CODES.LOLLIPOP(${Build.VERSION_CODES.LOLLIPOP})")
        }
        mHandleThread = HandlerThread(TAG)
        mHandleThread?.start()
        mHandler = Handler(mHandleThread!!.looper)
        detect()
        val ids = mCameraService?.cameraIdList
        Log.d(TAG, "openCamera: ids=${ids?.joinToString(",")},mCameraID=$mCameraID")
        try {
            mCameraService?.openCamera(mCameraID.toString(), this, mHandler)
            mCameraCharacteristics = mCameraService?.getCameraCharacteristics(mCameraID.toString())
        } catch (e: CameraAccessException) {
            Log.e(TAG, "openCamera2  fail_1,cameraID is $mCameraID", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "openCamera2  fail_2,cameraID is $mCameraID", e)
        }
    }

    private fun resetCameraParameters() {
        mTorchOpen = false
        mZoomLevel = 1.0f
    }

    protected fun stopInternal() {
        try {
            resetCameraParameters()
            mCameraCharacteristics = null
            mImageReader?.close()
            mImageReader = null
            mDetectImageReader?.close()
            mDetectImageReader = null
            mCameraCaptureSession?.close()
            mCameraCaptureSession = null
            mCameraDevice?.close()
            mCameraDevice = null
            //mSurface?.release()
        }catch (ex: java.lang.Exception) {
            Log.e(TAG, "stop: failed", ex)
        }
        //mSurface
        afterClose()
        mHandler?.removeCallbacksAndMessages(null)
        mHandleThread?.quitSafely()
        mHandler = null
        mHandleThread = null
        mIsCameraPrepared = false

    }

    override fun onOpened(cameraDevice: CameraDevice) {
        Log.i(TAG, "Camera2 cameraID={$mCameraID} opened")
        mCameraDevice = cameraDevice
        startPreview()
    }

    override fun onDisconnected(cameraDevice: CameraDevice) {
        Log.i(TAG, "Camera2 cameraID={$mCameraID} disconnected")
        stopInternal()
    }

    override fun onError(cameraDevice: CameraDevice, i: Int) {
        Log.e(TAG, "Open camera ,cameraID=${mCameraID} failed_3 ,errno is $i")
        stopInternal()
    }

    private fun getMaxZoom(): Float {
        return mCameraCharacteristics?.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
            ?: 1f
    }

    fun getZoom(): Float {
        return mZoomLevel
    }

    /* fun setZoom(level: Float) {
        try {
            val maxZoom = getMaxZoom()
            val m: Rect =
                mCameraCharacteristics?.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!!
            if (level in 1.0..maxZoom) {
                mZoomLevel = level
                val minW = (m.width() / (maxZoom * 10)).toInt()
                val minH = (m.height() / (maxZoom * 10)).toInt()
                val difW = m.width() - minW
                val difH = m.height() - minH
                var cropW = (difW / 10 * level).toInt()
                var cropH = (difH / 10 * level).toInt()
                cropW -= cropW and 3
                cropH -= cropH and 3
                val zoom = Rect(cropW, cropH, m.width() - cropW, m.height() - cropH)
                mCaptureRequestBuilder?.set<Rect>(CaptureRequest.SCALER_CROP_REGION, zoom)
                mCameraCaptureSession?.setRepeatingRequest(
                    mCaptureRequestBuilder!!.build(),
                    null, null
                )
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error", e)
        }
    }

     fun setZoom(event: MotionEvent) {
        val currentFingerSpacing: Float
        if (event.pointerCount > 1) {
            // Multi touch logic
            currentFingerSpacing = CameraHelper.getFingerSpacing(event)
            if (fingerSpacing != 0f) {
                if (currentFingerSpacing > fingerSpacing && getMaxZoom() > mZoomLevel) {
                    mZoomLevel += 0.1f
                } else if (currentFingerSpacing < fingerSpacing && mZoomLevel > 1) {
                    mZoomLevel -= 0.1f
                }
                setZoom(mZoomLevel)
            }
            fingerSpacing = currentFingerSpacing
        }
    }*/


    fun canFlashLight(): Boolean {
        return mCameraCharacteristics?.get(
            CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
    }

    open fun getJpgRotation( deviceRotation: Int): Int {
        if(mCameraCharacteristics == null) return  0
        val result: Int
        val sensorRotation = mCameraCharacteristics!![CameraCharacteristics.SENSOR_ORIENTATION]
        val lensFace = mCameraCharacteristics!![CameraCharacteristics.LENS_FACING]
        if (sensorRotation == null || lensFace == null) {
            Log.e(TAG, "can not get sensor rotation or lens face")
            return deviceRotation
        }
        result = sensorRotation % 360
        return result
    }

    private fun getAFMode( targetMode:Int):Int{
        val allAFMode = mCameraCharacteristics?.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)!!
        allAFMode.forEach {
            if(targetMode == it){
                return it
            }
        }
        Log.d(TAG, "getAFMode: not support AF mode: ${targetMode},use default mode:${allAFMode[0]}")
        return allAFMode[0]
    }

    private fun getAEMode(targetMode:Int):Int{
        val allAEMode = mCameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)!!
        allAEMode.forEach {
            if(it == targetMode){
                return it
            }
        }
        Log.d(TAG, "getAEMode: not support mode: $targetMode, use default mode: ${allAEMode[0]}")
        return allAEMode[0]
    }


    companion object {
        const val TAG = "Camera2Api"

    }
}
