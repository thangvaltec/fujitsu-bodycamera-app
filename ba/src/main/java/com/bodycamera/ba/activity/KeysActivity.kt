package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import com.bodycamera.ba.data.IOnDeviceFeatureListener
import com.bodycamera.ba.data.KeyAction
import com.bodycamera.ba.receiver.DeviceKeyReceiver
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityKeysBinding
import com.bodycamera.tests.databinding.ActivityLightSensorBinding
import java.security.Key

class KeysActivity : AppCompatActivity(),
    DeviceKeyReceiver.IOnKeyActionListener {
    companion object{
        const val TAG = "KeysActivity"
    }
    lateinit var binding: ActivityKeysBinding
    private var mDeviceKeyReceiver :DeviceKeyReceiver?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKeysBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView() {
        KingStone.runAsync {
            mDeviceKeyReceiver = DeviceKeyReceiver()
            mDeviceKeyReceiver?.create(this,this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        KingStone.runBlock {
            mDeviceKeyReceiver?.release(this)
            mDeviceKeyReceiver = null
        }
    }



    override fun onKeyAction(keyName: String, keyAction: KeyAction) {
        TaskHelper.runOnUiThread{
            val action = when(keyAction){
                KeyAction.UP -> "Press Up"
                KeyAction.DOWN -> "Press Down"
                KeyAction.LONG_CLICK -> "Long press"
            }
            binding.tvKey.text = "${keyName} - ${action}"
        }
    }
}
