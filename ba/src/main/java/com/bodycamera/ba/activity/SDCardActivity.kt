package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import com.bodycamera.ba.data.TriLightColor
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.SDCardHelper
import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityAlarmLightBinding
import com.bodycamera.tests.databinding.ActivitySdcardBinding
import com.bodycamera.tests.databinding.ActivityTriColorBinding

class SDCardActivity : AppCompatActivity() {
    companion object{
        const val TAG = "SDCardActivity"
    }
    lateinit var binding:ActivitySdcardBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySdcardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView(){
        TaskHelper.runDelayTask({
            TaskHelper.runOnUiThread {
                val file = SDCardHelper.getSDCardStorageRoot(this)
                binding.btnSdcardPath.text =
                    if (file == null) "SDCard Not found" else file.absolutePath
                if (file != null) {
                    val storageF = SDCardHelper.getSDCardMemorySize(this)
                    binding.pbCapacity.progress = (storageF.remain * 100 / storageF.total).toInt()
                    binding.btnSdcardInfo.text =
                        "TotalSize:${storageF.getFormatTotal()}G, RemainSize:${storageF.getFormatRemain()}G , ${storageF.getRemainPercent()}%"
                }
            }
        },200)
    }
}
