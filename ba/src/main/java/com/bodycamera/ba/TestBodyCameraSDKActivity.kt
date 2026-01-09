package com.bodycamera.ba

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.tests.databinding.ActivityTestBinding
import com.yuy.api.manager.IBodyCameraService
import java.lang.RuntimeException
import kotlin.math.log


class TestBodyCameraSDKActivity : AppCompatActivity(),View.OnClickListener {
    private lateinit var binding:ActivityTestBinding
    companion object{
        const val TAG = "TestBodyCameraSDKActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(TAG, "onCreate: ")
        bindService()

        binding.btnIsAudio.setOnClickListener(this)
        binding.btnStartAudio.setOnClickListener(this)
        binding.btnStopAudio.setOnClickListener(this)
        binding.btnAudioEnable.setOnClickListener(this)
        binding.btnAudioDisable.setOnClickListener(this)
        binding.btnIsAudioEnable.setOnClickListener(this)

        binding.btnIsVideo.setOnClickListener(this)
        binding.btnStartVideo.setOnClickListener(this)
        binding.btnStopVideo.setOnClickListener(this)
        binding.btnIsVideoEnable.setOnClickListener(this)
        binding.btnVideoEnable.setOnClickListener(this)
        binding.btnVideoDisable.setOnClickListener(this)

    }


    private fun bindService(){
        Log.i(TAG, "bindService: 0")
        val intent = Intent()

        intent.setAction("com.yuy.api.manager.IBodyCameraService")
        intent.setPackage("com.bodycamera.nettysocket")
        val ret =  bindService(intent, mConnection, BIND_AUTO_CREATE)
        Log.i(TAG, "bindService: ret=${ret}")
    }

    override fun onClick(v: View?) {
        try {
            when (v?.id) {
                binding.btnIsAudio.id -> {
                    var ret = mBodyCameraService?.isAudioRecording == true
                    Log.i(TAG, "onClick: isAudioRecording=${ret}")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "isAudioRecording=${ret}", Toast.LENGTH_SHORT).show()
                }
                binding.btnIsVideo.id -> {
                    var ret = mBodyCameraService?.isVideoRecording == true
                    Log.i(TAG, "onClick: isVideoRecording=${ret}")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "isVideoRecording=${ret}", Toast.LENGTH_SHORT).show()
                }
                binding.btnStartAudio.id -> {
                    var ret = mBodyCameraService?.startAudioRecording() == true
                    Log.i(TAG, "onClick: startAudioRecording=${ret}")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "startAudioRecording=${ret}", Toast.LENGTH_SHORT).show()
                }
                binding.btnStopAudio.id -> {
                    mBodyCameraService?.stopAudioRecording()
                    Log.i(TAG, "onClick: stopAudioRecording")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "stopAudioRecording", Toast.LENGTH_SHORT).show()
                }
                binding.btnStartVideo.id -> {
                    val ret= mBodyCameraService?.startVideoRecording() == true
                    Log.i(TAG, "onClick: startVideoRecording=$ret")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "startVideoRecording", Toast.LENGTH_SHORT).show()
                }
                binding.btnStopVideo.id -> {
                    mBodyCameraService?.stopVideoRecording()
                    Log.i(TAG, "onClick: stopVideoRecording")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "stopVideoRecording", Toast.LENGTH_SHORT).show()
                }
                binding.btnIsAudioEnable.id -> {
                    val ret= mBodyCameraService?.isAudioModuleEnabled() == true
                    Log.i(TAG, "onClick: isAudioModuleEnabled=$ret")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "isAudioModuleEnabled", Toast.LENGTH_SHORT).show()
                }
                binding.btnAudioEnable.id -> {
                    mBodyCameraService?.enableAudioModule()
                    Log.i(TAG, "onClick: enableAudioModule")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "enableAudioModule", Toast.LENGTH_SHORT).show()
                }
                binding.btnAudioDisable.id -> {
                    mBodyCameraService?.disableAudioModule()
                    Log.i(TAG, "onClick: disableAudioModule")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "disableAudioModule", Toast.LENGTH_SHORT).show()
                }
                binding.btnIsVideoEnable.id -> {
                    val ret= mBodyCameraService?.isVideoModuleEnable() == true
                    Log.i(TAG, "onClick: isVideoModuleEnable=$ret")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "isVideoModuleEnable", Toast.LENGTH_SHORT).show()
                }
                binding.btnVideoEnable.id -> {
                    mBodyCameraService?.enableVideoModule()
                    Log.i(TAG, "onClick: enableVideoModule")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "enableVideoModule", Toast.LENGTH_SHORT).show()
                }
                binding.btnVideoDisable.id -> {
                    mBodyCameraService?.disableVideoModule()
                    Log.i(TAG, "onClick: disableVideoModule")
                    Toast.makeText(this@TestBodyCameraSDKActivity, "disableVideoModule", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    throw RuntimeException("unknown type")
                }
            }
        }catch (ex:RemoteException){
            Log.e(TAG, "onClick: failed", ex)
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mBodyCameraService = IBodyCameraService.Stub.asInterface(service)
            Log.i(TAG, "onServiceConnected: mBodyCameraService=${mBodyCameraService}")
            Toast.makeText(this@TestBodyCameraSDKActivity, "get connection", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(TAG, "onServiceDisconnected: ")
            mBodyCameraService = null
        }
    }

    private var mBodyCameraService: IBodyCameraService? = null

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mConnection)
    }

}
