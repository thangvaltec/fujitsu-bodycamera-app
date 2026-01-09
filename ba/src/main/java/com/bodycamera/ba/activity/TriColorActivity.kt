package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.bodycamera.ba.data.TriLightColor
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityTriColorBinding

class TriColorActivity : AppCompatActivity(),View.OnClickListener {
    lateinit var binding:ActivityTriColorBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTriColorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        binding.btnBlankLight.setOnClickListener(this)
        binding.btnYellowLight.setOnClickListener(this)
        binding.btnRedLight.setOnClickListener(this)
        binding.btnGreenLight.setOnClickListener(this)
        binding.btnBlankLight.setOnClickListener(this)
    }

    override fun onClick(view: View?) {
        if(view == null) return
        when(view.id){
            R.id.btn_green_light -> {
                KingStone.getDeviceFeature()?.setTriColorLight(TriLightColor.GREEN)
            }
            R.id.btn_blank_light -> {
                KingStone.getDeviceFeature()?.setTriColorLight(TriLightColor.OFF)
            }
            R.id.btn_yellow_light -> {
                KingStone.getDeviceFeature()?.setTriColorLight(TriLightColor.YELLOW)
            }
            R.id.btn_red_light -> {
                KingStone.getDeviceFeature()?.setTriColorLight(TriLightColor.RED)
            }
        }
    }
}
