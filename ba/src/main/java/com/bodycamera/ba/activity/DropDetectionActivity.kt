package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.SurfaceHolder
import android.view.View
import android.widget.Toast
import com.bodycamera.ba.receiver.DeviceEINTReceiver
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityDropDetectionBinding
import com.bodycamera.tests.databinding.ActivityIrcutBinding
import com.bodycamera.tests.databinding.ActivityLightSensorBinding

class DropDetectionActivity : AppCompatActivity(),View.OnClickListener,
    DeviceEINTReceiver.IOnDeviceEINTChangedListener {
    companion object{
        const val TAG = "DropDetectionActivity"

    }
    lateinit var binding:ActivityDropDetectionBinding
    private var mDeviceEINTReceiver:DeviceEINTReceiver?= null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDropDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        binding.btnDropStop.setOnClickListener(this)
        binding.btnDropStart.setOnClickListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        KingStone.runBlock {
            stopDropDetection()
        }
    }

    private fun startDropDetection(){
        mDeviceEINTReceiver = DeviceEINTReceiver()
        mDeviceEINTReceiver?.create(this,this)
    }
    private fun stopDropDetection(){
        mDeviceEINTReceiver?.release(this)
        mDeviceEINTReceiver = null
    }

    override fun onClick(view: View?) {
        if(view == null) return
        val device = KingStone.getDeviceFeature()?:return
        when(view.id){
            R.id.btn_drop_start -> {
               KingStone.runAsync {
                   startDropDetection()
               }
            }
            R.id.btn_drop_stop -> {
                KingStone.runAsync {
                    stopDropDetection()
                }
            }
        }
    }

    override fun onDropDetection() {
        Toast.makeText(this,"drop detection found",Toast.LENGTH_SHORT)
    }

    override fun onBackupBatteryChanged(volt: Int, level: Int) {
        TaskHelper.runOnUiThread{
            binding.tvDeviceInfo.text = "BackupBattery volt:$volt,level=${level}%"
        }
    }

    override fun onBackCoverClosed(flag: Boolean) {
        TaskHelper.runOnUiThread{
            binding.tvDeviceInfo.text = "Back Cover is ${if(flag)"closed" else "opened"}"
        }
    }


}
