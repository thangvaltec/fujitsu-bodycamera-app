package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.SurfaceHolder
import android.view.View
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityIrcutBinding
import com.bodycamera.tests.databinding.ActivityLightSensorBinding
import com.bodycamera.tests.databinding.ActivityMassStorageBinding

class MassStorageActivity : AppCompatActivity(),View.OnClickListener{
    companion object{
        const val TAG = "MassStorageActivity"
    }
    lateinit var binding:ActivityMassStorageBinding
    private var mHandler:Handler?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMassStorageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        binding.btnMassStorageStart.setOnClickListener(this)
        binding.btnMassStorageStop.setOnClickListener(this)
        mHandler = Handler(Looper.getMainLooper())
    }

    override fun onDestroy() {
        super.onDestroy()
        mHandler?.removeCallbacksAndMessages(null)
        mHandler = null
    }

    override fun onClick(view: View?) {
        if(view == null) return
        val device = KingStone.getDeviceFeature()?:return
        when(view.id){
            R.id.btn_mass_storage_start -> {
                if(!device.isMassStorageSet()) {
                    device.setMassageStorage(true)
                    /**
                     * setMassStorage is async operation, win10/win11 is ok, but work in [Mac os]
                     * how many time does it take basing on how many files in your sdcard,
                     *  you will receive broadcast when massage storage is ready ,please reference #OnM530DeviceImpl.kt
                     */
                    mHandler?.postDelayed({
                        binding.tvIsMassStorageSet.text = if(device.isMassStorageSet()) "Mass Storage YES" else "Mass Storage No"
                    },1200)
                }
            }
            R.id.btn_mass_storage_stop -> {
                if (device.isMassStorageSet()) {
                    device.setMassageStorage(false)
                    mHandler?.postDelayed({
                        binding.tvIsMassStorageSet.text = if(device.isMassStorageSet()) "Mass Storage YES" else "Mass Storage No"
                    },1200)
                }
            }
        }
    }
}
