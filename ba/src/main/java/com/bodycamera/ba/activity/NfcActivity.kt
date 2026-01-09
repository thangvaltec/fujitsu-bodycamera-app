package com.bodycamera.ba.activity

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import com.bodycamera.ba.data.IOnDeviceFeatureListener
import com.bodycamera.ba.data.KeyAction
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.tools.NFCController
import com.bodycamera.ba.util.TaskHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityKeysBinding
import com.bodycamera.tests.databinding.ActivityLightSensorBinding
import com.bodycamera.tests.databinding.ActivityNfcBinding
import java.security.Key

class NfcActivity : AppCompatActivity(), View.OnClickListener, NFCController.IOnNfcReaderListener {
    companion object{
        const val TAG = "NfcActivity"
    }
    lateinit var binding: ActivityNfcBinding
    private var mNFCController:NFCController?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNfcBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    fun initView() {
        binding.btnNfcReaderStop.setOnClickListener(this)
        binding.btnNfcReaderStart.setOnClickListener(this)

    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onClick(view: View?) {
        when(view?.id){
            R.id.btn_nfc_reader_start -> {
                mNFCController = NFCController(this)
                mNFCController?.create(this)
                mNFCController?.setListener(this)
            }
            R.id.btn_nfc_reader_stop -> {
                mNFCController?.release(this)
                mNFCController = null
            }
        }
    }

    override fun onNfcReaderResult(result: String) {
        TaskHelper.runOnUiThread{
            binding.tvNfcReaderValue.text = result
        }
    }
}
