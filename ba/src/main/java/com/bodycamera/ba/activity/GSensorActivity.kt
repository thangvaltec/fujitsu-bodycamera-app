package com.bodycamera.ba.activity
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.bodycamera.ba.receiver.GSensorReceiver
import com.bodycamera.ba.tools.KingStone

import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityGsensorBinding
import java.text.SimpleDateFormat
import java.util.Date

class GSensorActivity : AppCompatActivity(),View.OnClickListener,
     GSensorReceiver.IOnGSensorChangedListener {
    companion object{
        const val TAG = "GSensorActivity"
    }
    lateinit var binding:ActivityGsensorBinding

    private var  mGSensorReceiver: GSensorReceiver?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGsensorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        binding.btnStartGsensor.setOnClickListener(this)
        binding.btnStopGsensor.setOnClickListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopGSensor()
    }

    override fun onResume() {
        super.onResume()
        KingStone.runAsync {
            mGSensorReceiver?.resume()
        }
    }

    override fun onPause() {
        super.onPause()
        KingStone.runAsync {
            mGSensorReceiver?.pause()
        }
    }

    private fun startGSensor(){
        KingStone.runAsync {
            mGSensorReceiver = GSensorReceiver()
            mGSensorReceiver?.create(this,this)
        }
    }

    private fun stopGSensor(){
        KingStone.runBlock {
            mGSensorReceiver?.release()
            mGSensorReceiver = null
        }
    }

    override fun onClick(view: View?) {
        if(view == null) return
        when(view.id){
            R.id.btn_start_gsensor -> {
               startGSensor()
            }
            R.id.btn_stop_gsensor -> {
                stopGSensor()
            }
        }
    }

    override fun onGSensorChanged(rotate: Float, speed: Float, timeMillis: Long) {
        TaskHelper.runOnUiThread{
            val formatter = SimpleDateFormat("yyyy-MM-dd HH-mm-ss")
            val dateString = formatter.format(Date(timeMillis))
            binding.tvGsensorValue.text = "rotate:$rotate,speed=${speed},timeMillis=${dateString}"
        }
    }
}
