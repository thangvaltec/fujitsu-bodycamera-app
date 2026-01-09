package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityLightSensorBinding

class LightSensorActivity : AppCompatActivity(),View.OnClickListener {
    companion object{
        const val TAG = "LightSensorActivity"
        const val MESSAGE_LIGHT_SENSOR_VALUE = 100
    }
    lateinit var binding: ActivityLightSensorBinding
    private var mHandler: Handler?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLightSensorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView() {
        binding.btnLightSensorStart.setOnClickListener(this)
        binding.btnLightSensorStop.setOnClickListener(this)
        mHandler = object:Handler(Looper.getMainLooper()){
            override fun dispatchMessage(msg: Message) {
                super.dispatchMessage(msg)
                if(msg.what == MESSAGE_LIGHT_SENSOR_VALUE){
                    val cc = KingStone.getDeviceFeature()?:return
                    binding.tvLightSensorValue.text = cc.getLightSensorValue().toString()
                }
                mHandler?.sendEmptyMessageDelayed(MESSAGE_LIGHT_SENSOR_VALUE,1000)
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        mHandler?.removeMessages(MESSAGE_LIGHT_SENSOR_VALUE)
        mHandler = null
    }

    override fun onClick(view: View?) {
        if (view == null) return
        val device = KingStone.getDeviceFeature() ?: return
        when (view.id) {
            R.id.btn_light_sensor_start -> {
                Log.i(TAG, "onClick: isAlarmLightSet0 - ${if(device.isAlarmLightSet()) "opened" else "close"}")
                if (!device.isAlarmLightSet()) {
                    device.setLightSensor(true)
                    mHandler?.removeMessages(MESSAGE_LIGHT_SENSOR_VALUE)
                    mHandler?.sendEmptyMessage(MESSAGE_LIGHT_SENSOR_VALUE)
                }
            }

            R.id.btn_light_sensor_stop -> {
                Log.i(TAG, "onClick: isAlarmLightSet1 - ${if(device.isAlarmLightSet()) "opened" else "close"}")
                device.setLightSensor(false)
            }

        }
    }
}
