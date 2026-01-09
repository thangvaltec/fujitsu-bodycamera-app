package com.bodycamera.ba.activity

import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.tools.NativeLocationController
import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityIrcutBinding
import com.bodycamera.tests.databinding.ActivityLightSensorBinding
import com.bodycamera.tests.databinding.ActivityLocationBinding

class LocationActivity : AppCompatActivity(),View.OnClickListener,
    NativeLocationController.IOnLocationChangedListener {
    companion object{
        const val TAG = "LocationActivity"
    }
    lateinit var binding:ActivityLocationBinding

    private var  nativeLocationController:NativeLocationController?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        binding.btnStartLocation.setOnClickListener(this)
        binding.btnStopLocation.setOnClickListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        KingStone.runBlock {
            if(nativeLocationController?.isRunning() == true) {
                nativeLocationController?.release()
                nativeLocationController = null
                Toast.makeText(this,"stop location successful",Toast.LENGTH_SHORT)
            }
        }
    }

    override fun onClick(view: View?) {
        if(view == null) return
        when(view.id){
            R.id.btn_start_location -> {
                KingStone.runAsync {
                    nativeLocationController = NativeLocationController()
                    nativeLocationController!!.addListener(this)
                    val ret = nativeLocationController!!.create(this,this)
                    if(ret)
                        Toast.makeText(this,"start location successful",Toast.LENGTH_SHORT)
                    else
                        Toast.makeText(this,"start location failed",Toast.LENGTH_SHORT)
                }
            }
            R.id.btn_stop_location -> {
                KingStone.runBlock {
                    if(nativeLocationController?.isRunning() == true) {
                        nativeLocationController?.release()
                        nativeLocationController = null
                        Toast.makeText(this,"stop location successful",Toast.LENGTH_SHORT)
                    }
                }
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        TaskHelper.runOnUiThread{
            binding.tvLocationValue.text = "provider=${location.provider},latitude=${location.latitude},${location.longitude}"
        }
    }
}
