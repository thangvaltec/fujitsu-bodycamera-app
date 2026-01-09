package com.bodycamera.ba.activity

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityFace3Binding
import com.yuy.api.manager.IBodyCameraService

// 顔認証結果を受け取り、TopActivity へ渡すための画面
class Face3Activity : AppCompatActivity(), View.OnClickListener {

    companion object {
        const val TAG = "Face3Activity"
    }

    lateinit var binding: ActivityFace3Binding

    // 顔認証の結果保持（TopActivity に返すため）
    private var lastFaceResult: String? = null
    private var lastFaceId: String? = null
    private var lastFaceName: String? = null

    // BodyCameraService 接続
    private var mBodyCameraService: IBodyCameraService? = null

    // BodyCamera Service の接続コールバック
    private val mConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mBodyCameraService = IBodyCameraService.Stub.asInterface(service)
            Log.i(TAG, "onServiceConnected: mBodyCameraService=$mBodyCameraService")
            Toast.makeText(this@Face3Activity, "get connection", Toast.LENGTH_SHORT).show()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(TAG, "onServiceDisconnected")
            mBodyCameraService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFace3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        bindService()   // BodyCameraService 接続
        initView()      // Intent から結果取得
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(mConnection)
    }

    override fun onClick(view: View?) {
        if (view == null) return

        when (view.id) {

            // 顔認証開始ボタン
            R.id.btn_start_face -> {
                val list = mBodyCameraService?.getFaceRecognitionPackageAndClass()
                if (list != null && list.size == 2) {
                    val intent = Intent()
                    intent.component = ComponentName(list[0], list[1])
                    startActivity(intent)
                }

                // 顔認証結果を TopActivity に返す
                val resultIntent = Intent().apply {
                    putExtra("ResultName", lastFaceName)
                    putExtra("ResultID", lastFaceId)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    fun initView() {

        val resultName = intent.getStringExtra("ResultName")
        val resultID = intent.getStringExtra("ResultID")
        val deviceID = intent.getStringExtra("DeviceID")
        val policeID = intent.getStringExtra("PoliceID")

        binding.tvInfo.text =
            "resultName:$resultName\r\nresultID:$resultID\r\nDeviceID:$deviceID\r\nPoliceID:$policeID\r\n"

        binding.btnStartFace.setOnClickListener(this)

        val hasFaceResult = !resultName.isNullOrEmpty() || !resultID.isNullOrEmpty()

        if (hasFaceResult) {

            lastFaceResult = if (!resultID.isNullOrEmpty()) "OK" else "NG"
            lastFaceId = resultID
            lastFaceName = resultName

            val authMode = currentAuthMode()
            Log.i(TAG, "initView: authMode=$authMode faceId=$resultID faceName=$resultName")

            when (authMode) {

                // 顔＋静脈フロー → TopActivity に返して 静脈へ遷移
                "FaceAndVein" -> {
                    forwardResultToTopActivity(
                        resultName = resultName ?: lastFaceName,
                        resultID = resultID ?: lastFaceId,
                        deviceID = deviceID,
                        policeID = policeID
                    )
                    return
                }

                // 顔のみ（Flow1）→ VeinResultActivity を直接開く
                "Face" -> {
                    val intent = Intent(this, VeinResultActivity::class.java).apply {
                        putExtra(
                            VeinResultActivity.EXTRA_VEIN_RESULT,
                            if (!resultID.isNullOrEmpty()) "OK" else "NG"
                        )
                        putExtra(VeinResultActivity.EXTRA_VEIN_ID, resultID)
                        putExtra("ResultName", resultName)
                        putExtra("ResultID", resultID)
                        putExtra(VeinResultActivity.EXTRA_AUTH_MODE, "Face")
                    }
                    startActivity(intent)
                    finish()
                }
            }
        }
    }

    private fun bindService() {
        val intent = Intent()
        intent.setAction("com.yuy.api.manager.IBodyCameraService")
        intent.setPackage("com.bodycamera.nettysocket")
        bindService(intent, mConnection, BIND_AUTO_CREATE)
    }

    private fun forwardResultToTopActivity(
        resultName: String?,
        resultID: String?,
        deviceID: String?,
        policeID: String?
    ) {

        val intent = Intent(this, TopActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("ResultName", resultName)
            putExtra("ResultID", resultID)
            putExtra("DeviceID", deviceID)
            putExtra("PoliceID", policeID)
        }

        startActivity(intent)
        finish()
    }

    private fun showFaceResultDialog() {
        val title = if (lastFaceResult == "OK") getString(R.string.auth_success)
        else getString(R.string.auth_failed)

        val message = if (lastFaceResult == "OK") {
            "ID: ${lastFaceId ?: "-"}\nName: ${lastFaceName ?: "-"}"
        } else {
            "顔: ${lastFaceResult ?: "-"}"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { d, _ ->
                d.dismiss()
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun currentAuthMode(): String {
        return getSharedPreferences("AuthMode", MODE_PRIVATE)
            .getString("AuthName", "") ?: ""
    }

    private fun clearAuthMode() {
        getSharedPreferences("AuthMode", MODE_PRIVATE)
            .edit()
            .remove("AuthName")
            .apply()
    }
}
