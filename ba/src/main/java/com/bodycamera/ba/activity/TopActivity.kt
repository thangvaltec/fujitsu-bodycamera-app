package com.bodycamera.ba.activity

//

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

    // 認証ボタンの定義
    lateinit var binding: ActivityFace3Binding

    // BodyCameraサービスへのインターフェース
    private var mBodyCameraService: IBodyCameraService? = null

    // 認証失敗後の再試行フラグ
    private var pendingFaceAndVeinRetry = false

    // BodyCamera Service 接続管理
    private val mConnection =
        object : ServiceConnection {
            // サービス接続時
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                mBodyCameraService = IBodyCameraService.Stub.asInterface(service)
                Log.i(TAG, "onServiceConnected: mBodyCameraService=$mBodyCameraService")

                // 再試行が保留中の場合はここで実行
                if (pendingFaceAndVeinRetry) {
                    pendingFaceAndVeinRetry = false
                    triggerFaceAndVeinRetry()
                }
            }

            // サービス切断時
            override fun onServiceDisconnected(name: ComponentName) {
                Log.i(TAG, "onServiceDisconnected")
                mBodyCameraService = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("DEBUG_CHECK", "onCreate STARTED")

        binding = ActivityFace3Binding.inflate(layoutInflater)
        setContentView(R.layout.activity_top)
        Log.e("DEBUG_CHECK", "Layout is set")

        // 1. 設定画面(SettingsActivity)から保存されたデバイスIDを取得
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val serial = prefs.getString(SettingsActivity.KEY_DEVICE_ID, "") ?: ""

        if (serial.isEmpty()) {
            Toast.makeText(this, "設定画面でデバイスIDを設定してください", Toast.LENGTH_LONG).show()
        }

        Log.d("SERIAL_TEST", "Serial (Manual) = $serial")

        // 2. サーバーへシリアル番号を送信し、認証モード(authMode)を取得
        val apiClient = DeviceApiClient()
        apiClient.getAuthMode(serial) { authMode ->
            runOnUiThread {
                Log.d("API", "authMode from server = $authMode")
                if (authMode == null) {
//                    Toast.makeText(this@TopActivity, "認証モードの取得に失敗しました", Toast.LENGTH_LONG).show()
                } else {
                    val msg =
                        when (authMode) {
                            0 -> "顔認証モード"
                            1 -> "静脈認証モード"
                            2 -> "顔＋静脈認証モード"
                            else -> "不明なモード -> 顔認証を使用"
                        }
                    Toast.makeText(this@TopActivity, msg, Toast.LENGTH_SHORT).show()

                    // Flow3 (顔+静脈) の際、顔認証結果を持って戻ってきた場合は自動遷移しない
                    if (authMode == 2 && hasFaceResultExtra()) {
                        saveAuthMode("FaceAndVein")
                    } else {
                        // それ以外は自動で認証フローを開始
                        startAuthFlowByMode(authMode)
                    }
                }
            }
        }

        // 3. その他初期化処理
        bindBodyCameraService()
        initView()
        handleRetryIntent(intent)
        handleFaceResultIntent(intent)

        // ボタンのクリックリスナー設定
        setClickListeners()
    }

    // ...

    private fun initView() {
        // UI初期化（必要に応じて実装）
    }

    private fun handleRetryIntent(intent: Intent?) {
        val retryMode = intent?.getStringExtra(EXTRA_RETRY_FLOW)
        if (retryMode != null) {
            Log.d(TAG, "Retry Flow detected: $retryMode")
            // Retryロジックが必要な場合はここに記述
        }
    }

    private fun handleFaceResultIntent(intent: Intent?) {
        if (hasFaceResultExtra()) {
            val resultName = intent?.getStringExtra("ResultName")
            val resultID = intent?.getStringExtra("ResultID")
            val status = intent?.getIntExtra("ResultStatus", 2) ?: 2
            val message = intent?.getStringExtra("ResultMessage") ?: ""
            val similarity = intent?.getStringExtra("ResultSimilarity")
            Log.d(TAG, "Result received via Intent: Name=$resultName, ID=$resultID")
            forwardFaceResultToVeinResultWithDetails(status, message, resultName, resultID, similarity)
        }
    }

    // BodyCamera Service へのバインド処理
    private fun bindBodyCameraService() {
        val intent = Intent("com.bodycamera.service.BodyCameraService")
        intent.setPackage("com.bodycamera.service")

        // サービスのバインドを試みる
        val bound = bindService(intent, mConnection, BIND_AUTO_CREATE)
        if (!bound) {
            Log.e(TAG, "Failed to bind to BodyCameraService")
//            Toast.makeText(this, "BodyCameraサービスへの接続に失敗しました", Toast.LENGTH_LONG).show()
        } else {
            Log.d(TAG, "Binding to BodyCameraService...")
        }
    }

    private fun setClickListeners() {
        // フロー1~3のボタンを指定
        val btnFace = findViewById<Button>(R.id.btnFaceAuth)
        val btnVein = findViewById<Button>(R.id.btnVeinAuth)
        val btnFaceAndVein = findViewById<Button>(R.id.btnFaceAndVeinAuth)
        val btnSettings = findViewById<Button>(R.id.btnSettings) // Added

        // Settings
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }

        // フロー1: 顔認証のみ (Modular New Flow)
        btnFace.setOnClickListener {
            saveAuthMode("Face")
            launchFaceRecognition() // Unified launcher
        }

        // フロー2: 静脈認証のみ
        btnVein.setOnClickListener {
            saveAuthMode("Vein")
            saveFaceResultPending(false)
            launchPalmSecure("identify", null, true, true, true)
        }

        // フロー3: 顔＋静脈認証
        btnFaceAndVein.setOnClickListener {
            saveAuthMode("FaceAndVein")
            saveFaceResultPending(false)
            launchFaceRecognition()
            // finish() は削除: onActivityResult で結果を受け取って Vein 認証へ繋げるため
        }
    }

    // 顔認証アプリ起動（フロー1 & フロー3）
    private fun launchFaceRecognition() {
        Log.i(TAG, "★ face and vein status starts")
        Log.i(TAG, "launchFaceRecognition: Switching to Internal NewFaceAuthActivity")
        val intent = Intent(this, NewFaceAuthActivity::class.java)
        // Flow3のチェーン（顔→静脈）を実現するため、結果を待ちます
        startActivityForResult(intent, REQUEST_FACE)
    }

    /**
     * 顔認証（Flow1専用）を結果待ちで起動する
     * - app側が setResult で ResultName / ResultID を返す前提
     * - TopActivity を finish せず onActivityResult で受け取る
     */
    /**
     * 顔認証（Flow1専用）を結果待ちで起動する
     * - app側が setResult で ResultName / ResultID を返す前提
     * - TopActivity を finish せず onActivityResult で受け取る
     */
    private fun launchFaceRecognitionForFaceOnly() {
        Log.i(TAG, "★ face only status starts")
        Log.i(TAG, "launchFaceRecognitionForFaceOnly: Switching to Internal NewFaceAuthActivity")
        val intent = Intent(this, NewFaceAuthActivity::class.java)
        startActivityForResult(intent, REQUEST_FACE)
    }



    // PalmSecure 起動（フロー2 & フロー3）
    private fun launchPalmSecure(
        mode: String,
        faceId: String?,
        autoStart: Boolean,
        returnResult: Boolean,
        fromExternal: Boolean
    ) {
        val intent =
            Intent().apply {
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
        val dialog = AlertDialog.Builder(this).setCancelable(false).create()

        val view = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)

        view.findViewById<TextView>(android.R.id.text1).apply {
            // 顔認証成功メッセージ
            text = message
            // テキストの属性を定義
            textSize = 40f
            setTextColor(resources.getColor(R.color.white))
            gravity = android.view.Gravity.CENTER
            setPadding(60, 100, 60, 100)
        }

        // ダイアログの背景を設定

        view.setBackgroundColor(0xFF000000.toInt()) // 完全な黒背景

        dialog.setView(view)
        dialog.show()

        // 3.5秒後にPalmSecure起動
        Handler(Looper.getMainLooper())
            .postDelayed(
                {
                    dialog.dismiss()
                    launchPalmSecure(
                        mode = "verify",
                        faceId = faceId,
                        autoStart = true,
                        returnResult = true,
                        fromExternal = true
                    )
                },
                2000
            )
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
                // finish()を削除: 結果を受け取ってPalmSecureを起動するため
                // finish()
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
        return getSharedPreferences("AuthMode", MODE_PRIVATE).getString("AuthName", "") ?: ""
    }

    private fun saveAuthMode(mode: String) {
        getSharedPreferences("AuthMode", MODE_PRIVATE).edit().putString("AuthName", mode).apply()
    }

    // SharedPreferencesに顔認証結果表示を判断させる値(True, 又はFalse)を保存
    private fun saveFaceResultPending(pending: Boolean) {
        getSharedPreferences(PREF_FACE_FLOW, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FACE_PENDING, pending)
            .apply()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // PalmSecure認証からの戻り
        if (requestCode == REQUEST_PALMSECURE) {
            if (resultCode == RESULT_OK && data != null) {
                // 結果取得 (PalmSecure仕様: "result" -> "OK"/"NG", "user_id" -> ID)
                // キー名が異なる可能性があるため、複数パターンで確認します。
                var resultStr = data.getStringExtra("result")
                if (resultStr == null) {
                    resultStr = data.getStringExtra("vein_result")
                }
                resultStr = resultStr ?: "NG" // どちらもなければNG

                val userIdStr = data.getStringExtra("user_id") ?: data.getStringExtra("vein_id")

                Log.d(TAG, "PalmSecure Result: $resultStr, ID: $userIdStr")

                // 結果画面へ遷移
                val intent =
                    Intent(this, VeinResultActivity::class.java).apply {
                        putExtra(
                            VeinResultActivity.EXTRA_VEIN_RESULT,
                            if (resultStr == "OK") "OK" else "NG"
                        )
                        putExtra(VeinResultActivity.EXTRA_VEIN_ID, userIdStr)
                        putExtra(VeinResultActivity.EXTRA_AUTH_MODE, currentAuthMode())
                    }
                Log.d(TAG, "★ TopActivity: Launching VeinResultActivity (PalmSecure return path)")
                startActivity(intent)
                overridePendingTransition(0, 0) // アニメーションなし: 結果画面を即座に表示

                // フロー完了後、TopActivityでの待機は不要なら finish() はせず、VeinResultActivityに任せます。
                // ただし、TopActivity自体はバックグラウンドにあるべきならそのままでOK。
            } else {
                Log.d(TAG, "PalmSecure Canceled or Failed")
                // キャンセルされた場合などは何もしない、あるいはトースト表示
            }
        }
        // Face認証(Flow1 & Flow3)からの戻り
        else if (requestCode == REQUEST_FACE) {
            Log.i(TAG, "★ TopActivity: onActivityResult(REQUEST_FACE) - res:$resultCode, data:${data != null}")
            if (resultCode == RESULT_OK && data != null) {
                val status = data.getIntExtra("ResultStatus", -1)
                val message = data.getStringExtra("ResultMessage") ?: ""
                val resultName = data.getStringExtra("ResultName")
                val resultId = data.getStringExtra("ResultID")
                val similarity = data.getStringExtra("ResultSimilarity")

                val mode = currentAuthMode()
                if (mode == "FaceAndVein") {
                    if (status == 2) {
                        // Flow3: 顔認証成功 → メッセージ表示 → 静脈認証へ
                        showFullScreenMessageAndLaunchPalmSecure("顔認証完了しました\n手をかざしてください", resultId)
                    } else {
                        // Flow3: 顔認証失敗 → 直接結果画面へ（再試行ボタン付き）
                        Log.d(TAG, "★ TopActivity: Launching VeinResultActivity (Flow3 NG path)")
                        val intent = Intent(this, VeinResultActivity::class.java).apply {
                            putExtra(VeinResultActivity.EXTRA_VEIN_RESULT, "NG")
                            putExtra(VeinResultActivity.EXTRA_VEIN_ID, resultId)
                            putExtra(VeinResultActivity.EXTRA_AUTH_MODE, "FaceAndVein")
                            putExtra("ResultName", resultName)
                            putExtra("ResultID", resultId)
                        }
                        startActivity(intent)
                        overridePendingTransition(0, 0) // アニメーションなし: 結果画面を即座に表示
                    }
                } else {
                    // Flow1: 顔認証のみ -> 結果表示画面へ
                    forwardFaceResultToVeinResultWithDetails(status, message, resultName, resultId, similarity)
                }
            }
        }
    }

    /** 顔認証（Flow1）の結果を詳細情報付きで結果画面へ転送 */
    private fun forwardFaceResultToVeinResultWithDetails(status: Int, message: String, name: String?, id: String?, similarity: String?) {
        val intent = Intent(this, VeinResultActivity::class.java).apply {
            // モジュラー版の新しいキーを優先的にセット（VeinResultActivity.handleNewFaceAuthIntent用）
            putExtra(VeinResultActivity.EXTRA_NEW_STATUS, status)
            putExtra(VeinResultActivity.EXTRA_NEW_MESSAGE, message)
            putExtra(VeinResultActivity.EXTRA_NEW_NAME, name)
            putExtra(VeinResultActivity.EXTRA_NEW_ID, id)
            putExtra(VeinResultActivity.EXTRA_NEW_SIMILARITY, similarity)

            // レガシーキーも念のためセット
            putExtra(VeinResultActivity.EXTRA_VEIN_RESULT, if (status == 2) "OK" else "NG")
            putExtra("ResultName", name)
            putExtra("ResultID", id)
            putExtra(VeinResultActivity.EXTRA_AUTH_MODE, "Face")
        }
        Log.i(TAG, "★ TopActivity: Launching VeinResultActivity (Flow1 FaceOnly path)")
        startActivity(intent)
        overridePendingTransition(0, 0) // アニメーションなし: 結果画面を即座に表示
        // Flow 1 の場合は TopActivity は不要になったので終了
        // finish() 
    }
}
