package com.bodycamera.ba.activity

import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaScannerConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.TextureView
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Button
import android.widget.Toast
import androidx.core.content.FileProvider
import com.bodycamera.ba.codec.codec.Mp4Muxer
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.BuildConfig
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityCameraBinding
import com.bodycamera.tests.databinding.ActivityIrcutBinding
import com.bodycamera.tests.databinding.ActivityLightSensorBinding
import com.bodycamera.tests.databinding.ActivityRecordingBinding
import java.io.File

class RecordingActivity : AppCompatActivity(), SurfaceHolder.Callback {
    companion object {
        const val TAG = "RecordingActivity"
    }

    lateinit var binding: ActivityRecordingBinding
    private var mMp4Muxer: Mp4Muxer? = null
    private var mHandler: Handler? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private fun getDurationMillis() {
        if (mMp4Muxer == null) return
        var duration = mMp4Muxer!!.getDurationMillis() / 1000
        Log.i(TAG, "getDurationMillis: $duration")
        var hours = (duration / 3600).toInt()
        val hourStr = if (hours < 10) "0$hours" else hours
        var minutes = (duration % 3600).toInt() / 60
        val minuteStr = if (minutes < 10) "0$minutes" else minutes
        var seconds = (duration % 60).toInt()
        val secondStr = if (seconds < 10) "0$seconds" else seconds
        binding.tvDuration.text = "$hourStr:$minuteStr:$secondStr"
        mHandler?.postDelayed({
            getDurationMillis()
        }, 1000)
    }

    private fun startDurationMillis() {
        mHandler = Handler(Looper.getMainLooper())
        mHandler?.postDelayed({
            getDurationMillis()
        }, 1000)
    }

    private fun cancelDurationMillis() {
        mHandler = null
    }

    fun initView() {
        binding.btnCameraSwitch.setOnClickListener {
            KingStone.getSurfaceWrapper()?.switchCamera()
        }
        binding.btnVideoStart.setOnClickListener {
            val button = it as Button
            val tag = button.tag.toString()
            if (tag == "start") {
                if (mMp4Muxer != null) {
                    if (!mMp4Muxer!!.hasVideo()) {
                        Toast.makeText(
                            this,
                            "Stop audio recording first",
                            Toast.LENGTH_SHORT
                        )
                        return@setOnClickListener
                    }
                } else {
                    mMp4Muxer = Mp4Muxer()
                    mMp4Muxer?.startVideo()
                    startDurationMillis()
                    button.text = "Stop video recording"
                    button.tag = "stop"
                    binding.tvFilePath.text = ""
                    Toast.makeText(this, "Video recording has started", Toast.LENGTH_SHORT)
                }
            } else {
                if (mMp4Muxer != null) {
                    val destFile = mMp4Muxer?.getDestFile()
                    mMp4Muxer?.stop()
                    mMp4Muxer = null
                    cancelDurationMillis()
                    button.text = "Start video recording"
                    button.tag = "start"

                    binding.tvDuration.text = "00:00:00"
                    binding.tvFilePath.text = destFile?.absolutePath
                    Toast.makeText(this, "Video recording has stopped", Toast.LENGTH_SHORT)
                    if(destFile!=null) {
                        startPlay(destFile)
                    }
                }
            }
        }
        binding.btnAudioStart.setOnClickListener {
            val button = it as Button
            val tag = button.tag.toString()
            if (tag == "start") {
                if (mMp4Muxer != null) {
                    if (mMp4Muxer?.hasVideo() == true) {
                        Toast.makeText(
                            this,
                            "please stop Video recording first",
                            Toast.LENGTH_SHORT
                        )
                        return@setOnClickListener
                    }
                } else {
                    mMp4Muxer = Mp4Muxer()
                    mMp4Muxer?.startAudio()
                    startDurationMillis()
                    button.text = "Stop audio recording"
                    button.tag = "stop"
                    binding.tvFilePath.text = ""
                    Toast.makeText(this, "Audio recording has started", Toast.LENGTH_SHORT)
                }
            } else {
                if (mMp4Muxer != null) {
                    val destFile = mMp4Muxer?.getDestFile()
                    mMp4Muxer?.stop()
                    mMp4Muxer = null
                    cancelDurationMillis()
                    button.text = "Start audio recording"
                    button.tag = "start"
                    binding.tvDuration.text = "00:00:00"
                    binding.tvFilePath.text = destFile?.absolutePath
                    Toast.makeText(this, "Audio recording has stopped", Toast.LENGTH_SHORT)
                }
            }
        }
        //binding.surfaceView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        //binding.surfaceView.holder.addCallback(this)
        binding.textureView.surfaceTextureListener = textureViewListener
    }

    override fun surfaceCreated(p0: SurfaceHolder) {

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        KingStone.getSurfaceWrapper()
            ?.createSurfaceView(holder.surface, MySize(width, height), VideoAlign.DEFAULT)
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        KingStone.getSurfaceWrapper()?.releaseSurfaceView()
    }

    private fun startPlay(outputFile:File){
        MediaScannerConnection.scanFile(
            this, arrayOf(outputFile.absolutePath), null, null)

        if (outputFile.exists()) {
            // Launch external activity via intent to play video recorded using our provider
           /*startActivity(Intent().apply {
                action = Intent.ACTION_VIEW
                type = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(outputFile.extension)
                //val authority = "${BuildConfig.APPLICATION_ID}.provider"
                val authority = "com.bodycamera.tests.provider"
                data = FileProvider.getUriForFile(this@RecordingActivity, authority, outputFile)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            })*/
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "error file not found",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    var surface1 :Surface?=null
    private val textureViewListener = object : TextureView.SurfaceTextureListener{
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureAvailable: surface=${surface},width=$width,height=$height")
            surface1 = Surface(surface)
            KingStone.getSurfaceWrapper()
                ?.createSurfaceView(surface1!!, MySize(width, height), VideoAlign.DEFAULT)
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            Log.i(TAG, "onSurfaceTextureSizeChanged: surface=${surface},width=$width,height=$height")
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            Log.i(TAG, "onSurfaceTextureDestroyed: surface=${surface}")
            KingStone.getSurfaceWrapper()?.releaseSurfaceView()
            surface1 = null
            return true
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            // Log.i(TAG, "onSurfaceTextureUpdated: surface=${surface}")
        }
    }
}
