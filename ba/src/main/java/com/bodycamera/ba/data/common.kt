package com.bodycamera.ba.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.opengl.EGLSurface
import android.view.Surface
import com.bodycamera.ba.codec.filter.VideoFilter
import com.bodycamera.ba.tools.Goldberg
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

enum class KeyAction{
    UP,
    DOWN,
    LONG_CLICK
}

enum class NetworkType{
    NETWORK_4G,
    NETWORK_5G
}

enum class VideoAlign{
    DEFAULT,
    X,
    Y,
    AUTO
}

enum class VideoQuality{
     AVERAGE,
     MEDIUM,
     BEST
}

data class SimpleBuffer(
    val byteArray: ByteArray,
    val offset:Int,
    val length:Int,
)

data class AudioEncodeParam(
    val mimeType:String = MediaFormat.MIMETYPE_AUDIO_AAC,
    val channelCount:Int = 1,
    val sampleRate:Int = 8000,
    val bitFormat :Int = AudioFormat.ENCODING_PCM_16BIT
){
    fun equals(param: AudioEncodeParam) =
         (mimeType == param.mimeType &&
           channelCount == param.channelCount &&
           sampleRate == param.sampleRate &&
           bitFormat == param.bitFormat)
    private var bitRate:Int = 0
    private var bufferSize = 0

    fun getBitRate():Int{
        if(bitRate > 0) return bitRate
        if(bitFormat != AudioFormat.ENCODING_PCM_16BIT) throw RuntimeException("Not support bitFormat")
        val sampleBits = 16
        bitRate = sampleRate * sampleBits / 2 * channelCount * 8
        return bitRate
    }

    fun channelMask():Int{
        return  when(channelCount){
            1 -> AudioFormat.CHANNEL_IN_MONO
            else -> throw RuntimeException("channelConfig() $channelCount not found")
        }
    }


    fun getBufferSize():Int{
        if(bufferSize > 0) return bufferSize
        return bufferSize
    }

    fun getSampleRateIndex():Int{
        return AUDIO_SAMPLING_RATES.indexOf(sampleRate)
    }
    companion object{
        val AUDIO_SAMPLING_RATES = intArrayOf(
            96000,  // 0
            88200,  // 1
            64000,  // 2
            48000,  // 3
            44100,  // 4
            32000,  // 5
            24000,  // 6
            22050,  // 7
            16000,  // 8
            12000,  // 9
            11025,  // 10
            8000,  // 11
            7350,  // 12
            -1,  // 13
            -1,  // 14
            -1
        )
    }
}

data class VideoEncodeParam(
    val width:Int = 1920,
    val height:Int = 1080,
    val frameRate:Int = 25,
    val gop :Int = 2,
    val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    //val mimeType: String = MediaFormat.MIMETYPE_VIDEO_HEVC,
    val quality: VideoQuality = VideoQuality.MEDIUM,

){

    /**
     *The calculation formula for video bit rate is:
     * Bitrate = resolution × frame rate × color depth × compression ratio
     */
    fun getBitRate():Int{

        val compressionRate = if(mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC) 0.68f else 1.0f
        var qualityRate = when(quality){
            VideoQuality.AVERAGE->{
                0.005f
            }
            VideoQuality.MEDIUM->{
                0.025f
            }
            VideoQuality.BEST ->{
                0.06f
            }
        }
        return (qualityRate * width * height * frameRate * 8  * compressionRate).toInt()
    }

    fun equals(param: VideoEncodeParam) =
        (width == param.width &&
         height == param.height &&
         frameRate == param.frameRate &&
         mimeType == param.mimeType &&
         gop == param.gop)

}

data class MySize(
    var width:Int,
    var height:Int
)

enum class TriLightColor{
    OFF,
    RED,
    GREEN,
    YELLOW
}
interface IOnAndroidDevice{
    fun create():Boolean
    fun release()
    fun isMassStorageSet():Boolean
    fun setMassageStorage(flag:Boolean):Boolean

    fun setIRCut(flag:Boolean):Boolean
    fun isIRCutSet():Boolean

    fun setAutoIRCut(flag: Boolean):Boolean
    fun isAutoIRCut():Boolean

    fun setTorch(flag:Boolean):Boolean
    fun isTorchSet():Boolean

    fun setLightSensor(flag:Boolean):Boolean
    fun isLightSensorSet():Boolean
    fun getLightSensorValue():Int

    fun setLaser(flag: Boolean):Boolean
    fun isLaserSet():Boolean

    fun setTriColorLight(color: TriLightColor):Boolean
    fun isTriColorLightSet():TriLightColor

    fun setAlarmLight(flag:Boolean):Boolean
    fun isAlarmLightSet():Boolean

    fun getBackupBatteryVoltage():Int
    fun getBackupBatteryLevel():Int
    fun isBackCoverClosed() :Boolean
    fun switchSystemLanguage(language: String, area:String):Boolean

    fun returnDefaultLauncher(context: Context) = Unit
}

interface IOnDeviceFeatureListener{
    /**
     * when ir-cut is turn gray, camera image should turn gray manually
     */
    fun onTurnCameraImageGray(flag:Boolean) = Unit
    /**
     * when drop detection is triggered
     */
    fun onDropDetected() = Unit
    /**
     * if flag == true , back cover is opened ,otherwise back cover is closed
     */
    fun onBackCoverOpen(flag:Boolean) = Unit
    /*
     * capacity is an range from 0 to 100,the measure unit is percent (%)
     */
    fun onBackupBatteryChanged(capacity:Int) = Unit

    /**
     * when device is attach with external Collect Station,
     * Collection station will send a broadcast message to notify starting Mass storage mode,
     * then collection station can access the device sdcard filesystem
     */
    fun onCollectStationEnable() = Unit

    /**
     * network type is changed
     */
    fun onNetworkChanged() = Unit
}

object Constants{
    const val ACTION_PTTKEY_DOWN = "android.intent.action.ACTION_PTTKEY_DOWN"
    const val ACTION_PTTKEY_UP = "android.intent.action.ACTION_PTTKEY_UP"
    const val ACTION_PTTKEY_LONG_PRESS = "android.intent.action.ACTION_PTTKEY_LONG_PRESS"
    const val ACTION_VIDEO_DOWN = "android.intent.action.ACTION_VIDEO_DOWN"
    const val ACTION_CAMERA_DOWN = "android.intent.action.ACTION_CAMERA_DOWN"
    const val ACTION_RECORD_DOWN = "android.intent.action.ACTION_RECORD_DOWN"
    const val ACTION_RECORD_UP = "android.intent.action.ACTION_RECORD_UP"
    const val ACTION_RECORD_LONG_PRESS = "android.intent.action.ACTION_RECORD_LONG_PRESS"
    const val ACTION_LASER_DOWN = "android.intent.action.ACTION_LASER_DOWN"
    const val ACTION_LASER_UP = "android.intent.action.ACTION_LASER_UP"
    const val ACTION_LASER_LONGPRESS = "android.intent.action.ACTION_LASER_LONGPRESS"
    const val ACTION_SOS_LONG_PRESS = "android.intent.action.ACTION_SOS_LONG_PRESS"
    const val ACTION_SOS_DOWN = "android.intent.action.ACTION_SOS_DOWN"
    const val ACTION_SOS_UP = "android.intent.action.ACTION_SOS_UP"
    const val ACTION_FILE_IMP =  "android.intent.action.ACTION_FILE_IMP"

    const val TEXT_WRAP_SYMBOL = "%RN%"
}

enum class MediaPipeError{
    AudioSourceError,
    VideoEncoderError,
    AudioEncoderError
}
interface IOnMediaEncoderListener{
    fun onMediaEncoderStart(isAudio:Boolean)
    fun onMediaEncoderFormatChanged(isAudio: Boolean,mediaFormat: MediaFormat)
    fun onMediaEncoderDataOutput(isAudio: Boolean,bufferInfo: MediaCodec.BufferInfo, byteBuffer: ByteBuffer)
    fun onMediaEncoderError(isAudio: Boolean,error: MediaPipeError)
}

data class TextureAvailableMessage(
    val textureID:Int
):Goldberg.GoldBergData(TextureAvailableMessage::class.java.canonicalName)

interface IOnFilterCallback{
    fun onFilterCreate(outputSize:MySize,videoAlign: VideoAlign):Boolean
    fun onFilterDraw(textureID: Int,textureSize:MySize):Int
    fun onFilterRelease()
    fun onFilterChanged(outputSize: MySize) = Unit
    fun onFilterDrawAvailable() = Unit
}

data class WaterMark(
    var fontSize:Int = 46,

)

enum class AudioOutputDevice{
    TYPE_UNKNOWN,
    TYPE_WIRED,
    TYPE_BLUETOOTH,
    TYPE_SPEAKER
}

data class VideoFilterContext(
    val eglSurface: EGLSurface,
    val surface: Surface,
    val videoFilter: VideoFilter,
    val mySize: MySize,
    val callback:IOnVideoFilterCallback
)

interface IOnVideoFilterCallback{
    fun onVideoFilterTextureAvailable()
}


data class StorageF(val type:Int = STORAGE_EXTERNAL,
                    val total: Long,val remain:Long ){
    fun getRemainPercent(): String {
        return if(total == 0L) "0" else String.format("%.2f",remain.toFloat() / total * 100)
    }
    fun getFormatTotal(): String {
        return if(total == 0L) "0" else String.format("%.2f",total.toFloat() / (1024 * 1024 *1024))
    }
    fun getFormatRemain(): String {
        return if(total == 0L) "0" else String.format("%.2f",remain.toFloat() / (1024 * 1024 * 1024))
    }

    fun getFormatUsed(): String {
        return if(total == 0L) "0" else String.format("%.2f",(total.toFloat()- remain.toFloat())/ (1024 * 1024 *1024))
    }

    companion object{
        const val STORAGE_SYSTEM = 1
        const val STORAGE_SDCARD = 2
        const val STORAGE_EXTERNAL = 3
    }
}