package com.bodycamera.ba.activity

import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.TriLightColor
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityAgingTestBinding
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.util.AudioHelper
import com.bodycamera.ba.util.TaskHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AgingTestActivity : AppCompatActivity(),
        View.OnClickListener,
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener,
        SurfaceHolder.Callback
{
    lateinit var binding:ActivityAgingTestBinding
    private var mHandlerThread:HandlerThread?=null
    private var mHandler:Handler?=null
    private var files = mutableListOf<File>()
    private var offset = 1
    private var lastUpdateTimeSeconds = 0
    private var switchCameraStart = false
    private var switchTriColorLight = false

    companion object{
        const val TAG = "AgingTestActivity"
        const val MSG_CALC_TIME = 300
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgingTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        val files = listOf("h264.mp4")
        files.forEach {
            copyMp4file(it)
        }

    }

    private fun startThread(){
        mHandlerThread = HandlerThread(TAG)
        mHandlerThread?.start()
        mHandler = object :Handler(mHandlerThread!!.looper){
            override fun dispatchMessage(msg: Message) {
                when(msg.what){
                    MSG_CALC_TIME -> {
                        lastUpdateTimeSeconds ++
                        val day = lastUpdateTimeSeconds / (24 * 60 * 60)
                        val dayLeft  = lastUpdateTimeSeconds % (24 * 60 * 60)
                        val hour = dayLeft / (60 * 60)
                        val hourLeft =  dayLeft % (60 * 60)
                        val minute = hourLeft / 60
                        val seconds = hourLeft % 60
                        TaskHelper.runOnUiThread {
                            binding.tvTime.text =
                                "$day Day(s) $hour hour(s),$minute minute(s),$seconds second(s)"
                        }
                        val df = KingStone.getDeviceFeature() ?: return
                        if(switchCameraStart && lastUpdateTimeSeconds % 5 == 0){
                            KingStone.getSurfaceWrapper()?.switchCamera()
                            df.setIRCut(!df.isIRCutSet())
                            df.setLaser(!df.isLaserSet())
                            df.setTorch(!df.isTorchSet())
                        }

                        if(switchTriColorLight) {
                            when (offset) {
                                0 -> df.setTriColorLight(TriLightColor.OFF)
                                1 -> df.setTriColorLight(TriLightColor.RED)
                                2 -> df.setTriColorLight(TriLightColor.YELLOW)
                                3 -> df.setTriColorLight(TriLightColor.GREEN)
                            }
                            offset = 1 + (offset++) % 3
                        }else{
                            df.setTriColorLight(TriLightColor.OFF)
                        }
                        mHandler?.sendEmptyMessageDelayed(MSG_CALC_TIME,1000)
                    }
                }
            }
        }
    }

    private fun stopThread(){
        mHandlerThread?.quitSafely()
        mHandlerThread?.join()
        mHandler = null
        mHandlerThread = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopThread()
        stop()
        release()
    }

    override fun onPause() {
        super.onPause()
        stop()
    }

    override fun onResume() {
        super.onResume()
        start()
    }

    fun initView() {
        binding.btnStart.setOnClickListener(this)
        supportActionBar?.hide()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        startThread()
        binding.svVideo.setOnPreparedListener(this)
        binding.svVideo.setOnErrorListener(this)
        binding.svVideo.setOnCompletionListener (this)
        binding.svVideo.setMediaController(null)
        binding.svCamera.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        binding.svCamera.holder.addCallback(this)
    }

    private val videoList = mutableListOf<String>()
    private fun doHareWareTest(flag:Boolean) {
        val df = KingStone.getDeviceFeature() ?: return
        df.setAlarmLight(flag)
        df.setIRCut(flag)
        df.setTorch(flag)
        df.setLaser(flag)

        offset = if (flag) {
            switchCameraStart = true
            switchTriColorLight = true
            1
        } else {
            switchCameraStart = false
            switchTriColorLight = false
            0
        }
    }

    override fun onClick(view: View?) {
        if(view == null) return
        when(view.id){
            R.id.btn_start -> {
                if(view.tag == "start"){
                    start()
                }else{
                    stop()
                }
            }
        }
    }

    private fun playVideo(flag:Boolean){
        if(flag){
            if(files.isNotEmpty()){
                AudioHelper.setAudioSpeakerMode(this)
                AudioHelper.setMediaVolume(this,AudioHelper.getMediaMaxVolume(this))
                val rand = (0 until files.size).random()
                binding.svVideo.setVideoPath(files[rand].absolutePath)
                binding.svVideo.start()
            }
        }else{
            AudioHelper.setAudioNormalMode(this)
            if(binding.svVideo.isPlaying){
                binding.svVideo.stopPlayback()
            }
        }
    }

    fun release(){
        if(binding.svVideo.isPlaying)
            binding.svVideo.stopPlayback()
        files.forEach {
            it.deleteOnExit()
        }
    }

    fun start(){
        binding.btnStart.tag = "stop"
        binding.btnStart.text = "stop"
        doHareWareTest(true)
        mHandler?.sendEmptyMessageDelayed(MSG_CALC_TIME,1000)
        playVideo(true)
    }

    fun stop(){
        binding.btnStart.tag = "start"
        binding.btnStart.text = "start"
        doHareWareTest(false)
        mHandler?.removeMessages(MSG_CALC_TIME)
        playVideo(false)
    }

    override fun onPrepared(mediaPlayer: MediaPlayer?) {
       // mediaPlayer?.setVolume(1f,1f)
        mediaPlayer?.start()
    }

    override fun onCompletion(mp: MediaPlayer) {
        mp.stop()
        playVideo(true)
    }

    override fun onError(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
        finish()
        return false
    }

    fun copyMp4file(assetFileName:String):Boolean{
        val inputStream = assets.open(assetFileName)
        val outFile = File(cacheDir, assetFileName)

        try {
            inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            files.add(outFile)
            Log.i(TAG, "copyMp4file: Copied to: ${outFile.absolutePath}")
            return true
        } catch (ex: IOException) {
            Log.e(TAG, "copyMp4file: failed", ex)
        }
        return false
    }
    override fun surfaceCreated(p0: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val surface = holder.surface
        val surfaceSize = MySize(width,height)
        Log.i(TAG, "surfaceChanged: width=$width,height=$height")
        KingStone.getSurfaceWrapper()?.createSurfaceView(surface!!, surfaceSize!!,VideoAlign.DEFAULT)
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        KingStone.getSurfaceWrapper()?.releaseSurfaceView()
    }


}
