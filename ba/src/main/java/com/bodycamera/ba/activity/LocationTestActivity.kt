package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.bodycamera.ba.data.TriLightColor
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityTriColorBinding

class LocationTestActivity : AppCompatActivity() {
    lateinit var binding:ActivityTriColorBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTriColorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){

    }
}
