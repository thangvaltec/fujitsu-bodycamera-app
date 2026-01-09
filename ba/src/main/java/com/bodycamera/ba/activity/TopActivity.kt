package com.bodycamera.ba.activity

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.ba.activity.Face3Activity.Companion.TAG
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityFace3Binding
import com.yuy.api.manager.IBodyCameraService

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Callback
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
//
import com.bodycamera.ba.activity.DeviceSerialHelper
import com.bodycamera.ba.activity.DeviceApiClient

class TopActivity : AppCompatActivity() {
    // 1) HÀM LẤY SERIAL (ở trên)
    /*@SuppressLint("HardwareIds", "PrivateApi")
    private fun getDeviceSerialNumber(): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)

            val keys = listOf(
                "vendor.gsm.serial",  // BodyCamera向けベンダー独自シリアル
                "ro.serialno",
                "ro.boot.serialno",
                "persist.sys.serialno",
                "gsm.serial",
                "ril.serialnumber"
            )

            for (key in keys) {
                var value = get.invoke(null, key) as String
                if (!value.isNullOrEmpty() && value != "unknown") {
                    value = value.trim()
                    if (value.contains(" ")) {
                        value = value.substringBefore(" ")
                    }
                    Log.d("SERIAL_TEST", "Found serial from $key = $value")
                    return value
                }
            }

            // ここまで来たらハードウェアシリアルは取得できなかった → ANDROID_ID にフォールバック
            val androidId = android.provider.Settings.Secure.getString(
                contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
            Log.d("SERIAL_TEST", "Fallback ANDROID_ID = $androidId")

            androidId ?: "UNKNOWN"

        } catch (e: Exception) {
            Log.e("SERIAL_TEST", "Error getDeviceSerialNumber: $e")
            "UNKNOWN"
        }
    }*/



    /*@SuppressLint("PrivateApi")
    private fun getDeviceSerialNumber(): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)

            // CHỈ LẤY vendor.gsm.serial (thiết bị của bạn cho phép truy cập)
            val value = get.invoke(null, "vendor.gsm.serial") as String

            if (!value.isNullOrEmpty() && value != "unknown") value else "UNKNOWN"

        } catch (e: Exception) {
            "UNKNOWN"
        }
    }
    */
    // 2) HÀM GỬI SERIAL LÊN API (đặt ở ngay dưới getDeviceSerialNumber)
    /*private fun sendSerialToServer(serial: String, callback: (Int?) -> Unit) {

        val client = OkHttpClient()

        val json = JSONObject().apply { put("serialNo", serial) }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("http://10.200.2.29:5000/api/device/getAuthMode")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e("API", "Send serial failed: $e")
                callback(null)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                val result = response.body?.string()
                Log.d("API", "Response: $result")

                try {
                    val jsonObj = JSONObject(result)
                    val authMode = jsonObj.getInt("authMode")
                    callback(authMode)
                } catch (e: Exception) {
                    Log.e("API", "Parse error: $e")
                    callback(null)
                }
            }
        })
    }*/

    // 共通変数を定義
    companion object {
        const val REQUEST_PALMSECURE = 9001
        private const val REQUEST_FACE = 9002
        private const val PREF_FACE_FLOW = "FaceFlowState"
        private const val KEY_FACE_PENDING = "FaceResultPending"
        const val EXTRA_RETRY_FLOW = "RetryFlowMode"
    }

    // 顔認証の要素
    lateinit var binding: ActivityFace3Binding
    // 顔認証画面の接続データ

    private var mBodyCameraService: IBodyCameraService? = null
    // 認証が失敗した場合の再実行なのか判定
    private var pendingFaceAndVeinRetry = false

    // BodyCamera Service 接続管理
    private val mConnection = object : ServiceConnection {
        // 顔認証を開始
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mBodyCameraService = IBodyCameraService.Stub.asInterface(service)
            Log.i(TAG, "onServiceConnected: mBodyCameraService=$mBodyCameraService")
            //  再実行要求が保留中ならばここで処理する

            if (pendingFaceAndVeinRetry) {
                pendingFaceAndVeinRetry = false
                triggerFaceAndVeinRetry()
            }
        }

        // 顔認証の終了
        override fun onServiceDisconnected(name: ComponentName) {
            Log.i(TAG, "onServiceDisconnected")
            mBodyCameraService = null
        }
    }

    // 本画面の初期表示
    /*override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("DEBUG_CHECK", "onCreate STARTED")

        binding = ActivityFace3Binding.inflate(layoutInflater)
        setContentView(R.layout.activity_top)
        Log.e("DEBUG_CHECK", "Layout is set")

        // 1. serial取得処理
        val serial = getDeviceSerialNumber()
        Log.d("SERIAL_TEST", "Serial = $serial")
        //Serial表示/非表示
        //Toast.makeText(this, "Serial: $serial", Toast.LENGTH_LONG).show()

        // 2. Gửi serial lên server
        sendSerialToServer(serial) { authMode ->
            runOnUiThread {
                Log.d("API", "authMode from server = $authMode")

                if (authMode == null) {
                    Toast.makeText(
                        this@TopActivity,
                        "サーバーから認証モード（authMode）を取得できませんでした。",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }

                // メッセージだけ作成
                val msg = when (authMode) {
                    0 -> "顔認証モード"
                    1 -> "静脈認証モード"
                    2 -> "顔＋静脈認証モード"
                    else -> "authMode が不正のため、暫定的に『顔認証』を使用します。"
                }

                //メッセージ表示時間と位置
                Log.d("API", "Toast message = $msg")
                //Toast.makeText(this@TopActivity, msg, Toast.LENGTH_LONG).show()
                Toast.makeText(this@TopActivity, msg, Toast.LENGTH_SHORT).show()
                // ★ Flow3 対策：
                // 顔認証結果(ResultName/ResultID)を持って TopActivity に戻ってきた場合は、
                // ここで再度 顔認証アプリ を起動しない。initView() に任せて Palm に進ませる。
                if (authMode == 2 && hasFaceResultExtra()) {
                    // すでに「顔＋静脈」の途中（顔認証は終わっている）なので、
                    // AuthMode だけ念のため保存して、あとは initView() に任せる。
                    saveAuthMode("FaceAndVein")
                    Log.d("FLOW3", "Face result already exists → skip auto start for Flow3 in onCreate()")
                } else {
                    // それ以外（初回起動 or Flow1/2）は今まで通り自動フロー開始
                    startAuthFlowByMode(authMode)
                }
            }
        }

        // 3. Giữ nguyên phần này

        // Kết nối BodyCamera Service
        bindService()
        initView()
        handleRetryIntent(intent)
        handleFaceResultIntent(intent)
        setClickListeners()
    }*/
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("DEBUG_CHECK", "onCreate STARTED")

        binding = ActivityFace3Binding.inflate(layoutInflater)
        setContentView(R.layout.activity_top)
        Log.e("DEBUG_CHECK", "Layout is set")

        // 1. 端末シリアル取得（共通 helper を使用）
        val serial = DeviceSerialHelper.getDeviceSerial(this)
        Log.d("SERIAL_TEST", "Serial = $serial")
        // Serial の Toast を出したくない場合はコメントアウトでOK
        // Toast.makeText(this, "Serial: $serial", Toast.LENGTH_LONG).show()

        // 2. サーバーへシリアル送信 → authMode 取得
        val apiClient = DeviceApiClient()
        apiClient.getAuthMode(serial) { authMode ->
            runOnUiThread {
                Log.d("API", "authMode from server = $authMode")

                if (authMode == null) {
                    Toast.makeText(
                        this@TopActivity,
                        "サーバーから認証モード（authMode）を取得できませんでした。",
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }

                // 表示メッセージ
                val msg = when (authMode) {
                    0 -> "顔認証モード"
                    1 -> "静脈認証モード"
                    2 -> "顔＋静脈認証モード"
                    else -> "authMode が不正のため、暫定的に『顔認証』を使用します。"
                }

                Log.d("API", "Toast message = $msg")
                Toast.makeText(this@TopActivity, msg, Toast.LENGTH_SHORT).show()

                // ★ Flow3 対策を今使っているなら、ここをそのまま残す:
                if (authMode == 2 && hasFaceResultExtra()) {
                    saveAuthMode("FaceAndVein")
                    Log.d("FLOW3", "Face result already exists → skip auto start for Flow3 in onCreate()")
                } else {
                    startAuthFlowByMode(authMode)
                }
            }
        }

        // 3. 既存処理はそのまま
        bindService()
        initView()
        handleRetryIntent(intent)
        handleFaceResultIntent(intent)
        setClickListeners()
    }


    // アプリを終了した際に実行
    override fun onDestroy() {
        super.onDestroy()
        try { unbindService(mConnection) } catch (_: Exception) {}
    }

    // 別の画面から戻った後の処理
    override fun onResume() {
        super.onResume()
        // showFaceResultDialog() 顔認証のみの旧仕様は未使用
    }

    // Intentオブジェクトを編集できるように設定
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        handleFaceResultIntent(intent) // 顔のみフローの結果が Intent で返ってきた場合に対応
        handleRetryIntent(intent)
    }

    // PalmSecure を含む各フローの結果を受け取る（Flow1 / Flow2 / Flow3 共通）
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // 顔認証（Flow1）の結果受信 → 共通結果画面へ転送
        if (requestCode == REQUEST_FACE) {
            if (resultCode != RESULT_OK || data == null) return
            val resultName = data.getStringExtra("ResultName")
            val resultID = data.getStringExtra("ResultID")
            forwardFaceResultToVeinResult(resultName, resultID)
            return
        }

        // PalmSecure 以外は無視
        if (requestCode != REQUEST_PALMSECURE) return
        if (resultCode != RESULT_OK || data == null) return

        // PalmSecure 結果
        val veinResult = data.getStringExtra("vein_result")
        val veinId = data.getStringExtra("vein_id")

        val resultName = data.getStringExtra("ResultName")
        val resultID = data.getStringExtra("ResultID")

        val mode = currentAuthMode()

        Log.i(TAG, "PalmSecure result: mode=$mode veinResult=$veinResult veinId=$veinId")

        // Flow1（顔のみ）: 直接結果画面へ
        if (mode == "Face") {
            val intent = CreateIntent(
                veinResult = if (!resultID.isNullOrEmpty()) "OK" else "NG",
                veinId = resultID,
                resultName = resultName
            )
            startActivity(intent)
            finish()
            return
        }

        // Flow2（静脈のみ）/ Flow3（顔＋静脈）: 直接結果画面へ
        val intent = CreateIntent(
            veinResult = veinResult,
            veinId = veinId,
            resultName = null
        )
        startActivity(intent)
        finish()
    }

    private fun CreateIntent(
        veinResult: String?,
        veinId: String?,
        resultName: String?
    ): Intent {

        return Intent(this, VeinResultActivity::class.java).apply {
            putExtra(VeinResultActivity.EXTRA_VEIN_RESULT, veinResult)
            putExtra(VeinResultActivity.EXTRA_VEIN_ID, veinId)
            putExtra("ResultName", resultName)
            putExtra(
                VeinResultActivity.EXTRA_AUTH_MODE,
                getSharedPreferences("AuthMode", MODE_PRIVATE)
                    .getString("AuthName", "")
            )
        }
    }

    // ボディカメラService 接続先設定
    private fun bindService() {
        val intent = Intent().apply {
            action = "com.yuy.api.manager.IBodyCameraService"
            `package` = "com.bodycamera.nettysocket"
        }
        bindService(intent, mConnection, BIND_AUTO_CREATE)
    }

    // フロー3: 顔認証結果受信後の自動処理
    fun initView() {
        val authName = getSharedPreferences("AuthMode", MODE_PRIVATE)
            .getString("AuthName", "") ?: ""

        val resultName = intent.getStringExtra("ResultName")
        val resultID = intent.getStringExtra("ResultID")

        // 顔認証結果が渡された場合 → 全画面メッセージ + PalmSecure自動起動
        if ((!resultName.isNullOrEmpty() || !resultID.isNullOrEmpty())
            && authName == "FaceAndVein"
        ) {
            showFullScreenMessageAndLaunchPalmSecure(
                "顔認証成功\n次は静脈認証",
                resultID
            )
        }
    }

    private fun handleRetryIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return

        val retryFlow = incomingIntent.getStringExtra(EXTRA_RETRY_FLOW) ?: return
        incomingIntent.removeExtra(EXTRA_RETRY_FLOW)

        when (retryFlow) {
            "Vein" -> {
                saveAuthMode("Vein")
                saveFaceResultPending(false)
                //  静脈のみ再実行は即座にPalmSecureを再起動
                launchPalmSecure(
                    mode = "identify",
                    faceId = null,
                    autoStart = true,
                    returnResult = true,
                    fromExternal = true
                )
            }

            "FaceAndVein" -> {
                saveAuthMode("FaceAndVein")
                saveFaceResultPending(false)
                //  顔＋静脈の再実行は顔認証からやり直す
                triggerFaceAndVeinRetry()
            }
        }
    }

    /**
     * 顔認証（Flow1）が別アプリから Intent で結果を返すケースをハンドリング
     * - currentAuthMode() == "Face" のときのみ結果画面へ転送
     */
    private fun handleFaceResultIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) return

        val resultName = incomingIntent.getStringExtra("ResultName")
        val resultId = incomingIntent.getStringExtra("ResultID")

        if (resultName.isNullOrEmpty() && resultId.isNullOrEmpty()) return
        if (currentAuthMode() != "Face") return

        // ループ防止のため処理後は extra を削除
        incomingIntent.removeExtra("ResultName")
        incomingIntent.removeExtra("ResultID")

        forwardFaceResultToVeinResult(resultName, resultId)
    }

    private fun setClickListeners() {
        // フロー1~3のボタンを指定
        val btnFace = findViewById<Button>(R.id.btnFaceAuth)
        val btnVein = findViewById<Button>(R.id.btnVeinAuth)
        val btnFaceAndVein = findViewById<Button>(R.id.btnFaceAndVeinAuth)

        // フロー1: 顔認証のみ
        btnFace.setOnClickListener {
            saveAuthMode("Face")
            saveFaceResultPending(true)
            launchFaceRecognitionForFaceOnly()
        }

        // フロー2: 静脈認証のみ
        btnVein.setOnClickListener {
            saveAuthMode("Vein")
            saveFaceResultPending(false)

            launchPalmSecure(
                mode = "identify",
                faceId = null,
                autoStart = true,
                returnResult = true,
                fromExternal = true
            )
        }

        // フロー3: 顔＋静脈認証
        btnFaceAndVein.setOnClickListener {
            saveAuthMode("FaceAndVein")
            saveFaceResultPending(false)
            launchFaceRecognition()
            finish()
        }
    }

    // 顔認証アプリ起動（フロー1 & フロー3）
    private fun launchFaceRecognition() {
        val list = mBodyCameraService?.getFaceRecognitionPackageAndClass()
        Log.i(TAG, "launchFaceRecognition: size=${list?.size}")

        if (list != null && list.size == 2) {
            val intent = Intent().apply {
                component = ComponentName(list[0], list[1])
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } else {
            Toast.makeText(this, "Face recognition app not available", Toast.LENGTH_SHORT).show()
            saveFaceResultPending(false)
        }
    }

    /**
     * 顔認証（Flow1専用）を結果待ちで起動する
     * - app側が setResult で ResultName / ResultID を返す前提
     * - TopActivity を finish せず onActivityResult で受け取る
     */
    private fun launchFaceRecognitionForFaceOnly() {
        val list = mBodyCameraService?.getFaceRecognitionPackageAndClass()
        Log.i(TAG, "launchFaceRecognitionForFaceOnly: size=${list?.size}")

        if (list != null && list.size == 2) {
            val intent = Intent().apply {
                component = ComponentName(list[0], list[1])
            }
            try {
                startActivityForResult(intent, REQUEST_FACE)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Face recognition app not available", Toast.LENGTH_SHORT).show()
                saveFaceResultPending(false)
            }
        } else {
            Toast.makeText(this, "Face recognition app not available", Toast.LENGTH_SHORT).show()
            saveFaceResultPending(false)
        }
    }

    /**
     * 顔認証（Flow1）の結果を共通結果画面へ転送
     */
    private fun forwardFaceResultToVeinResult(resultName: String?, resultId: String?) {
        val intent = Intent(this, VeinResultActivity::class.java).apply {
            putExtra(VeinResultActivity.EXTRA_VEIN_RESULT, if (!resultId.isNullOrEmpty()) "OK" else "NG")
            putExtra(VeinResultActivity.EXTRA_VEIN_ID, resultId)
            putExtra("ResultName", resultName)
            putExtra("ResultID", resultId)
            putExtra(VeinResultActivity.EXTRA_AUTH_MODE, "Face")
        }
        startActivity(intent)
        finish()
    }

    // PalmSecure 起動（フロー2 & フロー3）
    private fun launchPalmSecure(
        mode: String,
        faceId: String?,
        autoStart: Boolean,
        returnResult: Boolean,
        fromExternal: Boolean
    ) {
        val intent = Intent().apply {
            setClassName(
                "com.fujitsu.frontech.palmsecure_gui_sample",
                "com.fujitsu.frontech.palmsecure_gui_sample.MainActivity"
            )
            putExtra("mode", mode)
            if (!faceId.isNullOrEmpty()) putExtra("face_id", faceId)
            putExtra("auto_start", autoStart)
            putExtra("return_result", returnResult)
            putExtra("from_external", fromExternal)
        }

        try {
            startActivityForResult(intent, REQUEST_PALMSECURE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "PalmSecure app not found", Toast.LENGTH_SHORT).show()
        }
    }

    // 全画面メッセージ + 自動でPalmSecure起動（TopActivity非表示）
    private fun showFullScreenMessageAndLaunchPalmSecure(message: String, faceId: String?) {
        val dialog = AlertDialog.Builder(this)
            .setCancelable(false)
            .create()

        val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)

        view.findViewById<TextView>(android.R.id.text1).apply {
            // 顔認証成功メッセージ
            text = message
            // テキストの属性を定義
            textSize = 26f
            setTextColor(resources.getColor(R.color.white))
            gravity = android.view.Gravity.CENTER
            setPadding(60, 100, 60, 100)
        }

        // ダイアログの背景を設定

        view.setBackgroundColor(0xFF000000.toInt()) // 完全な黒背景

        dialog.setView(view)
        dialog.show()

        // 3.5秒後にPalmSecure起動
        Handler(Looper.getMainLooper()).postDelayed({
            dialog.dismiss()
            launchPalmSecure(
                mode = "verify",
                faceId = faceId,
                autoStart = true,
                returnResult = true,
                fromExternal = true
            )
        }, 3500)
    }

    // 認証失敗した場合、認証を再実行
    private fun triggerFaceAndVeinRetry() {
        if (mBodyCameraService != null) {
            pendingFaceAndVeinRetry = false
            launchFaceRecognition()
            finish()
        } else {
            //  サービス未接続なら接続完了まで保留
            pendingFaceAndVeinRetry = true
        }
    }
    // サーバーから受け取った authMode に応じて、自動的にフローを開始する
    private fun startAuthFlowByMode(authMode: Int) {
        when (authMode) {
            0 -> {
                // Flow1: 顔認証のみ
                saveAuthMode("Face")
                saveFaceResultPending(true)
                launchFaceRecognitionForFaceOnly()
            }
            1 -> {
                // Flow2: 静脈認証のみ
                saveAuthMode("Vein")
                saveFaceResultPending(false)
                launchPalmSecure(
                    mode = "identify",
                    faceId = null,
                    autoStart = true,
                    returnResult = true,
                    fromExternal = true
                )
            }
            2 -> {
                // Flow3: 顔＋静脈認証
                saveAuthMode("FaceAndVein")
                saveFaceResultPending(false)
                launchFaceRecognition()
                // 顔認証アプリに遷移するので、この画面は閉じる
                finish()
            }
            else -> {
                // 不正値の場合は暫定的に「顔認証」を使用
                saveAuthMode("Face")
                saveFaceResultPending(true)
                launchFaceRecognitionForFaceOnly()
            }
        }
    }
    // 顔認証アプリから戻ってきているかどうかを判定するヘルパー
    private fun hasFaceResultExtra(): Boolean {
        val resultName = intent.getStringExtra("ResultName")
        val resultID = intent.getStringExtra("ResultID")
        return !resultName.isNullOrEmpty() || !resultID.isNullOrEmpty()
    }



    private fun currentAuthMode(): String {
        return getSharedPreferences("AuthMode", MODE_PRIVATE)
            .getString("AuthName", "") ?: ""
    }

    private fun saveAuthMode(mode: String) {
        getSharedPreferences("AuthMode", MODE_PRIVATE)
            .edit()
            .putString("AuthName", mode)
            .apply()
    }

    // SharedPreferencesに顔認証結果表示を判断させる値(True, 又はFalse)を保存
    private fun saveFaceResultPending(pending: Boolean) {
        getSharedPreferences(PREF_FACE_FLOW, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FACE_PENDING, pending)
            .apply()
    }
}