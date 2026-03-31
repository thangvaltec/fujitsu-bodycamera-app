package com.bodycamera.ba.activity

//

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
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

    // Flow3 (Face+Vein) における顔認証結果のキャッシュ。
    // 静脈認証完了後に結果画面（VeinResultActivity）で表示するために使用します。
    private var faceResultName: String? = null
    private var faceResultId: String? = null

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
            val candidateList = intent?.getStringArrayListExtra("candidate_list")
            
            Log.d(TAG, "Result received via Intent: Name=$resultName, ID=$resultID, Candidates=${candidateList?.size}")
            
                // 候補リストがある場合、顔＋静脈モードで静脈認証を即時起動するロジックを追加可能（必要に応じて拡張）
            // 例: if (candidateList != null) { launchPalmSecure(..., candidateList) }

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
            Log.d(TAG, "═══════════════════════════════════════════")
            Log.d(TAG, "★ [Flow3] START: 顔＋静脈認証 開始")
            Log.d(TAG, "═══════════════════════════════════════════")
            saveAuthMode("FaceAndVein")
            saveFaceResultPending(false)
            launchFaceRecognition()
            // finish() は削除: onActivityResult で結果を受け取って Vein 認証へ繋げるため
        }
    }

    // 顔認証アプリ起動（フロー1 & フロー3）
    private fun launchFaceRecognition() {
        Log.i(TAG, "launchFaceRecognition: Switching to Internal NewFaceAuthActivity")
        val intent = Intent(this, NewFaceAuthActivity::class.java)

        // SharedPreferences からクラウド/ローカル認証の設定を取得し、Top-Kモードを判定
        val isFlow3 = currentAuthMode() == "FaceAndVein"
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

        // フロー3（顔＋静脈）はローカルモード時のみTop-Kを使用
        val shouldUseTopK = if (isFlow3) {
            prefs.getString(SettingsActivity.KEY_FACE_VEIN_AUTH_METHOD, "cloud") == "local"
        } else {
            prefs.getString(SettingsActivity.KEY_FACE_AUTH_METHOD, "cloud") == "local"
        }

        Log.d(TAG, "起動ポリシー: Flow3=$isFlow3 → UseTopK=$shouldUseTopK")

        intent.putExtra("should_use_topk", shouldUseTopK)
        
        // Flow3のチェーン（顔→静脈）を実現するため、結果を待ちます
        startActivityForResult(intent, REQUEST_FACE)
    }

    /**
     * 顔認証のみ（フロー1専用）を結果待ちで起動します。
     * - setResult で ResultName / ResultID を受け取る前提
     * - TopActivity を finish せず onActivityResult で結果を処理します
     */
    private fun launchFaceRecognitionForFaceOnly() {
        Log.i(TAG, "launchFaceRecognitionForFaceOnly: NewFaceAuthActivity を起動（顔認証のみ）")
        val intent = Intent(this, NewFaceAuthActivity::class.java)

        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val shouldUseTopK = prefs.getString(SettingsActivity.KEY_FACE_AUTH_METHOD, "cloud") == "local"
        intent.putExtra("should_use_topk", shouldUseTopK)
        
        startActivityForResult(intent, REQUEST_FACE)
    }



    // PalmSecure 起動（フロー2 & フロー3）
    private fun launchPalmSecure(
        mode: String,
        faceId: String?,
        autoStart: Boolean,
        returnResult: Boolean,
        fromExternal: Boolean,
        candidateList: ArrayList<String>? = null
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
                if (candidateList != null && candidateList.isNotEmpty()) {
                    putStringArrayListExtra("candidate_list", candidateList)
                    Log.d(TAG, "Launching PalmSecure with ${candidateList.size} candidates")
                }
            }

        try {
            startActivityForResult(intent, REQUEST_PALMSECURE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "PalmSecure app not found", Toast.LENGTH_SHORT).show()
        }
    }

    // 全画面メッセージ + 自動でPalmSecure起動（TopActivity非表示）
    private fun showFullScreenMessageAndLaunchPalmSecure(message: String, faceId: String?, candidateList: ArrayList<String>? = null) {
        // ハードウェアのバックボタンでキャンセル可能にするため、setCancelable を true に変更します。
        // こうすることで、ユーザーが意図しない連続スキャンを自由に中断できるようなUXを確保します。
        val dialog = AlertDialog.Builder(this).setCancelable(true).create()

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

        // ハンドラとRunnableを定義（キャンセル可能な設計：OOPとしてタスクを分離）
        val transitionHandler = Handler(Looper.getMainLooper())
        val transitionTask = Runnable {
            if (dialog.isShowing) {
                dialog.dismiss()
                launchPalmSecure(
                    mode = "verify",
                    faceId = faceId,
                    autoStart = true,
                    returnResult = true,
                    fromExternal = true,
                    candidateList = candidateList
                )
            }
        }

        // ユーザーがハードウェアのバックボタンなどでダイアログをキャンセルした場合の処理
        dialog.setOnCancelListener {
            Log.d(TAG, "待機ダイアログがキャンセルされました。次の認証ステップ（静脈）を中止します。")
            transitionHandler.removeCallbacks(transitionTask) // タイマーをクリアしてフローを中断
        }

        dialog.show()

        // 設定画面から「メッセージ表示の時間設定」を取得。デフォルトは1.0秒（1000ms）です。
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val transitionDelaySec = prefs.getFloat(SettingsActivity.KEY_TRANSITION_DELAY, 1.0f)
        val delayMs = (transitionDelaySec * 1000).toLong()

        // 遷移タスクを設定された時間後に実行します
        transitionHandler.postDelayed(transitionTask, delayMs)
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

    /**
     * VeinResultActivity.btnFinish が FLAG_ACTIVITY_SINGLE_TOP + CLEAR_TOP で戻ってきたとき呼ばれる。
     * is_auto_loop_continue = true の場合、設定に従って次の認証を自動開始する。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("is_auto_loop_continue", false)) {
            triggerAutoLoop()
        }
    }

    private fun triggerAutoLoop() {
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        val autoMethod = prefs.getString(SettingsActivity.KEY_AUTO_AUTH_METHOD, "none") ?: "none"
        Log.d(TAG, "★ Auto-loop triggered: method=$autoMethod")
        when (autoMethod) {
            "face" -> {
                saveAuthMode("Face")
                saveFaceResultPending(true)
                launchFaceRecognitionForFaceOnly()
            }
            "vein" -> {
                saveAuthMode("Vein")
                saveFaceResultPending(false)
                launchPalmSecure("identify", null, autoStart = true, returnResult = true, fromExternal = true)
            }
            "both" -> {
                saveAuthMode("FaceAndVein")
                saveFaceResultPending(false)
                launchFaceRecognition()
            }
            else -> Log.d(TAG, "Auto-loop: mode=none, no auto-restart")
        }
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
                val notRegistered = data.getBooleanExtra("not_registered", false)
                val noVeinData = data.getBooleanExtra("no_vein_data", false)

                Log.d(TAG, "PalmSecure Result: $resultStr, ID: $userIdStr, NotRegistered: $notRegistered, NoVeinData: $noVeinData")

                // 結果画面へ遷移
                val intent =
                    Intent(this, VeinResultActivity::class.java).apply {
                        val isSuccess = (resultStr == "OK")
                        putExtra(VeinResultActivity.EXTRA_VEIN_RESULT, if (isSuccess) "OK" else "NG")
                        putExtra(VeinResultActivity.EXTRA_VEIN_ID, userIdStr)
                        putExtra(VeinResultActivity.EXTRA_AUTH_MODE, currentAuthMode())
                        putExtra(VeinResultActivity.EXTRA_NOT_REGISTERED, notRegistered)
                        putExtra(VeinResultActivity.EXTRA_NO_VEIN_DATA, noVeinData)

                        // 顔認証時の氏名とIDがあれば引き継ぎます（静脈認証成功時またはIDがある場合）
                        if (faceResultName != null) {
                            putExtra("ResultName", faceResultName)
                        }
                        if (userIdStr != null) {
                            putExtra("ResultID", userIdStr) // 静脈IDを優先
                        } else if (faceResultId != null) {
                            putExtra("ResultID", faceResultId)
                        }
                    }
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
                        val candidateList = data.getStringArrayListExtra("candidate_list")
                        Log.d(TAG, "═══════════════════════════════════════════")
                        Log.d(TAG, "★ [Flow3] 顔認証結果受信")
                        Log.d(TAG, "  Status=$status, Name=$resultName, ID=$resultId")
                        
                        // キャッシュを更新
                        faceResultName = resultName
                        faceResultId = resultId
                        
                        Log.d(TAG, "  Similarity=$similarity")
                        Log.d(TAG, "  CandidateList: ${candidateList?.size ?: 0} 人")
                        candidateList?.forEachIndexed { i, id ->
                            Log.d(TAG, "    Candidate[$i]: $id")
                        }
                        Log.d(TAG, "═══════════════════════════════════════════")
                        if (candidateList != null && candidateList.isNotEmpty()) {
                            Log.d(TAG, "★ [Flow3] TopK → PalmSecure起動 (${candidateList.size}人)")
                            showFullScreenMessageAndLaunchPalmSecure("顔認証完了 (TopK)\n手をかざしてください", null, candidateList)
                        } else {
                            Log.d(TAG, "★ [Flow3] Legacy → PalmSecure起動 (faceId=$resultId)")
                            showFullScreenMessageAndLaunchPalmSecure("顔認証完了しました\n手をかざしてください", resultId, null)
                        }
                    } else {
                        // Flow3: 顔認証失敗 → 直接結果画面へ（再試行ボタン付き）
                        val intent = Intent(this, VeinResultActivity::class.java).apply {
                            putExtra(VeinResultActivity.EXTRA_VEIN_RESULT, "NG") // 失敗時はNG固定
                            putExtra(VeinResultActivity.EXTRA_VEIN_ID, resultId) // 顔認証のIDをセット
                            putExtra(VeinResultActivity.EXTRA_AUTH_MODE, "FaceAndVein")
                            
                            // 顔認証時の氏名とIDがあれば引き継ぎます
                            if (faceResultName != null) {
                                putExtra("ResultName", faceResultName)
                            }
                            if (resultId != null) { // 静脈IDの代わりに顔認証IDを優先
                                putExtra("ResultID", resultId) 
                            } else if (faceResultId != null) {
                                putExtra("ResultID", faceResultId)
                            }
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
        startActivity(intent)
        overridePendingTransition(0, 0) // アニメーションなし: 結果画面を即座に表示
        // Flow 1 の場合は TopActivity は不要になったので終了
        // finish()
    }
}
