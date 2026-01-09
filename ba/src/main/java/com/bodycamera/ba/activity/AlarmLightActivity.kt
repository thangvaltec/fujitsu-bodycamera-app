package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.bodycamera.ba.data.TriLightColor
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityAlarmLightBinding
import com.bodycamera.tests.databinding.ActivityTriColorBinding

class AlarmLightActivity : AppCompatActivity(),View.OnClickListener {
    lateinit var binding:ActivityAlarmLightBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlarmLightBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        binding.btnStartAlarm.setOnClickListener(this)
        binding.btnStopAlarm.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        if(view == null) return
        when(view.id){
            R.id.btn_start_alarm -> {
                KingStone.getDeviceFeature()?.setAlarmLight(true)
            }
            R.id.btn_stop_alarm -> {
                KingStone.getDeviceFeature()?.setAlarmLight(false)
            }
        }
    }

}
