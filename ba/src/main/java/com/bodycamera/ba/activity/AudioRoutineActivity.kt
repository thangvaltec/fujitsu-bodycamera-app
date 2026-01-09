package com.bodycamera.ba.activity

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.ba.data.AudioOutputDevice
import com.bodycamera.ba.receiver.AudioOutputDeviceReceiver
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.AudioHelper
import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityAudioRoutineBinding


class AudioRoutineActivity : AppCompatActivity(),
    AudioOutputDeviceReceiver.IOnAudioOutputDeviceChangedListener,View.OnClickListener {
    companion object{
        const val TAG = "AudioRoutineActivity"
    }
    lateinit var binding: ActivityAudioRoutineBinding
    private var mAudioOutputDeviceReceiver:AudioOutputDeviceReceiver?=null
    private var mAudioTrack:AudioTrack?=null
    private fun startAudioTrack(){

        // 配置 AudioTrack 参数
        val sampleRateInHz = 8000
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val bufferSizeInBytes =
            AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)
        val assetManager = assets
        val file = "output_8000Hz_mono.pcm"
        val inputStream = assetManager.open(file)
        Log.i(TAG, "writeAudioData: fileLength=${inputStream.available()}")
        val pcmData = ByteArray(inputStream.available())
        inputStream.read(pcmData)
        inputStream.close()

        mAudioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRateInHz,
            channelConfig,
            audioFormat,
            pcmData.size,
            AudioTrack.MODE_STATIC
        )

        mAudioTrack?.write(pcmData,0,pcmData.size)
        mAudioTrack?.play()

    }
    private fun stopAudioTrack(){
        mAudioTrack?.stop()
        mAudioTrack?.release()
        mAudioTrack = null
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioRoutineBinding.inflate(layoutInflater)
        setContentView(binding.root)
        KingStone.runAsync { startAudioDeviceListener() }
        initView()
    }

    fun initView() {
        binding.btnAudioStart.setOnClickListener (this)
        binding.btnAudioStop.setOnClickListener (this)
    }
    private fun startAudioDeviceListener() {
        mAudioOutputDeviceReceiver = AudioOutputDeviceReceiver()
        mAudioOutputDeviceReceiver!!.create(this, this)
    }
    private fun stopAudioDeviceListener() {
        mAudioOutputDeviceReceiver?.release(this)
        mAudioOutputDeviceReceiver = null
    }

    override fun onDestroy() {
        super.onDestroy()
        KingStone.runBlock {
            AudioHelper.setAudioNormalMode(this)
            stopAudioDeviceListener()
            stopAudioTrack()
        }
    }

    override fun onAudioOutputDeviceChanged(audioOutputDevice: AudioOutputDevice) {
        TaskHelper.runOnUiThread{
            when(audioOutputDevice){
                AudioOutputDevice.TYPE_UNKNOWN,
                AudioOutputDevice.TYPE_SPEAKER -> {
                    //AudioHelper.setAudioNormalMode(this)
                    AudioHelper.setAudioSpeakerMode(this)
                    binding.tvAudioRoutineInfo.text = "TYPE_SPEAKER"
                }
                AudioOutputDevice.TYPE_WIRED -> {
                    AudioHelper.setAudioHeadsetMode(this)
                    binding.tvAudioRoutineInfo.text = "TYPE_WIRED"
                }
                AudioOutputDevice.TYPE_BLUETOOTH -> {
                    AudioHelper.setAudioBtMode(this)
                    binding.tvAudioRoutineInfo.text = "TYPE_BLUETOOTH"
                }
            }

        }
    }

    override fun onClick(view: View?) {
       when(view?.id){
           R.id.btn_audio_start ->{
                KingStone.runAsync {
                    stopAudioTrack()
                    startAudioTrack()
                }
           }
           R.id.btn_audio_stop -> {
               KingStone.runAsync {
                   stopAudioTrack()
               }
           }
       }
    }

}
