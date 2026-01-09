package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.bodycamera.ba.data.TriLightColor
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityAlarmLightBinding
import com.bodycamera.tests.databinding.ActivityTorchLaserBinding
import com.bodycamera.tests.databinding.ActivityTriColorBinding

class TorchAndLaserActivity : AppCompatActivity(),View.OnClickListener {
    lateinit var binding:ActivityTorchLaserBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTorchLaserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        binding.btnStartLaser.setOnClickListener(this)
        binding.btnStopLaser.setOnClickListener(this)
        binding.btnStopTorch.setOnClickListener(this)
        binding.btnStartTorch.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        if(view == null) return
        when(view.id){
            R.id.btn_start_torch -> {
                KingStone.getDeviceFeature()?.setTorch(true)
            }
            R.id.btn_stop_torch -> {
                KingStone.getDeviceFeature()?.setTorch(false)
            }
            R.id.btn_start_laser -> {
                KingStone.getDeviceFeature()?.setLaser(true)
            }
            R.id.btn_stop_laser -> {
                KingStone.getDeviceFeature()?.setLaser(false)
            }
        }
    }
}
