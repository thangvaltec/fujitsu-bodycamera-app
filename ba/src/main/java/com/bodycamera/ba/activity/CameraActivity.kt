package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityCameraBinding
import com.bodycamera.tests.databinding.ActivityIrcutBinding
import com.bodycamera.tests.databinding.ActivityLightSensorBinding

class CameraActivity : AppCompatActivity(),View.OnClickListener,SurfaceHolder.Callback {
    companion object{
        const val TAG = "CameraActivity"
    }
    lateinit var binding:ActivityCameraBinding
    private var mSurface:Surface?=null
    private var mSurfaceSize :MySize?=null
    private var  mVideoAlign = VideoAlign.DEFAULT
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        binding.btnCameraSwitch.setOnClickListener{
            KingStone.getSurfaceWrapper()?.switchCamera()
        }
        binding.btnCameraTakePhoto.setOnClickListener {
            KingStone.getSurfaceWrapper()?.takePhoto()
        }
        binding.btnCameraVideoAlign.setOnClickListener {
            mVideoAlign = if(it.tag as String == "default"){
                binding.btnCameraVideoAlign.tag = "auto"
                binding.btnCameraVideoAlign.text = "auto"
                VideoAlign.AUTO
            } else {
                binding.btnCameraVideoAlign.tag = "default"
                binding.btnCameraVideoAlign.text = "default"
                VideoAlign.DEFAULT
            }

            if(mSurface == null) return@setOnClickListener
            KingStone.getSurfaceWrapper()?.releaseSurfaceView()
            Thread.sleep(200)
            KingStone.getSurfaceWrapper()?.createSurfaceView(mSurface!!, mSurfaceSize!!,mVideoAlign)
        }
        binding.btnCameraAntiShake.setOnClickListener {
            val flag = it.tag as String == "off"
            if(flag){
                binding.btnCameraAntiShake.text = "Anti_shake(On)"
                binding.btnCameraAntiShake.tag = "on"
            }else{
                binding.btnCameraAntiShake.text = "Anti_shake(Off)"
                binding.btnCameraAntiShake.tag = "off"
            }
            KingStone.runAsync {
                KingStone.getSurfaceWrapper()?.setCameraAntiShakeFlag(flag)
                KingStone.getSurfaceWrapper()?.resetCamera()
            }
        }
        binding.surfaceView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        binding.surfaceView.holder.addCallback(this)
    }

    override fun onClick(view: View?) {
        if(view == null) return
        when(view.id){
            R.id.btn_camera_switch -> {
                KingStone.getSurfaceWrapper()?.switchCamera()
            }
        }
    }

    override fun surfaceCreated(p0: SurfaceHolder) {
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {

        //VideoAlign.DEFAULT // fit video width/height
        //VideoAlign.AUTO    // full screen ,crop some from video's width or height
        mSurface = holder.surface
        mSurfaceSize = MySize(width,height)
        KingStone.getSurfaceWrapper()?.createSurfaceView(mSurface!!, mSurfaceSize!!,mVideoAlign)
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        mSurface = null
        mSurfaceSize = null
        KingStone.getSurfaceWrapper()?.releaseSurfaceView()
    }



}
