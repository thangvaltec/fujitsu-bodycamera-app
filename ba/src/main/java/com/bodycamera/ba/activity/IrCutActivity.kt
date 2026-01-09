package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.SurfaceHolder
import android.view.View
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityIrcutBinding
import com.bodycamera.tests.databinding.ActivityLightSensorBinding

class IrCutActivity : AppCompatActivity(),View.OnClickListener,SurfaceHolder.Callback {
    companion object{
        const val TAG = "IrCutActivity"
        const val MSG_LIGHT_SENSOR_VALUE = 200
    }
    lateinit var binding:ActivityIrcutBinding
    private var mHandler:Handler?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIrcutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        mHandler?.removeMessages(MSG_LIGHT_SENSOR_VALUE)
        mHandler = null
    }

    fun initView(){
        binding.btnIrCutStart.setOnClickListener(this)
        binding.btnIrCutStop.setOnClickListener(this)
        binding.btnAutoIrStart.setOnClickListener(this)
        binding.btnAutoIrStop.setOnClickListener(this)
        binding.surfaceView.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
        binding.surfaceView.holder.addCallback(this)
        mHandler = object :Handler(Looper.getMainLooper()){
            override fun dispatchMessage(msg: Message) {
                super.dispatchMessage(msg)
                if(MSG_LIGHT_SENSOR_VALUE == msg.what){
                    val cc = KingStone.getDeviceFeature()?:return
                    binding.tvLightSensorValue.text = if(cc.isLightSensorSet()) cc.getLightSensorValue().toString() else "0"
                    mHandler?.sendEmptyMessageDelayed(MSG_LIGHT_SENSOR_VALUE,1000L)
                }
            }
        }
        mHandler?.sendEmptyMessage(MSG_LIGHT_SENSOR_VALUE)
    }

    override fun onClick(view: View?) {
        if(view == null) return
        val device = KingStone.getDeviceFeature()?:return
        when(view.id){
            R.id.btn_ir_cut_start -> {
                if(!device.isIRCutSet())
                    device.setIRCut(true)
            }
            R.id.btn_ir_cut_stop -> {
                if (device.isIRCutSet())
                    device.setIRCut(false)
            }
            R.id.btn_auto_ir_start ->{
                if(!device.isAutoIRCut())
                    device.setAutoIRCut(true)
            }
            R.id.btn_auto_ir_stop ->{
                if(device.isAutoIRCut())
                   device.setAutoIRCut(false)
            }
        }
    }

    override fun surfaceCreated(p0: SurfaceHolder) {

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        KingStone.getSurfaceWrapper()?.createSurfaceView(holder.surface, MySize(width,height),
            VideoAlign.DEFAULT)
    }

    override fun surfaceDestroyed(p0: SurfaceHolder) {
        KingStone.getSurfaceWrapper()?.releaseSurfaceView()
    }

}
