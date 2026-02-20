package com.bodycamera.ba.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.tests.R

class VeinResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VEIN_RESULT = "vein_result"
        const val EXTRA_VEIN_ID = "vein_id"
        const val EXTRA_AUTH_MODE = "extra_auth_mode"

        // モジュラー顔認証用の新しいExtras
        const val EXTRA_NEW_STATUS = "extra_new_status" // Int
        const val EXTRA_NEW_MESSAGE = "extra_new_message"
        const val EXTRA_NEW_SIMILARITY = "extra_new_similarity"
        const val EXTRA_NEW_NAME = "extra_new_name"
        const val EXTRA_NEW_ID = "extra_new_id"
        const val EXTRA_CANDIDATE_LIST = "candidate_list"
    }

    // UI
    private lateinit var ivResult: ImageView
    private lateinit var tvResultTitle: TextView
    private lateinit var tvNameLabel: TextView // 氏名ラベル
    private lateinit var tvName: TextView // 氏名表示
    private lateinit var tvIdLine: TextView // ID: 12345 を1行で表示
    private lateinit var btnFinish: Button
    private lateinit var llRetryActions: LinearLayout
    private lateinit var btnRetry: Button
    private lateinit var btnBack: Button

    // 新しいUIフィールド
    private lateinit var tvMessage: TextView
    private lateinit var tvSimilarity: TextView

    // Flow1 / Flow2 / Flow3
    private var currentAuthMode: String = ""
    private var faceName: String? = null // Flow1 用
    private var faceId: String? = null // Flow1 用

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vein_result)

        initViews()
        handleIntent()
        setupClickListeners()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == TopActivity.REQUEST_PALMSECURE && resultCode == Activity.RESULT_OK) {
            val veinResult = data?.getStringExtra(EXTRA_VEIN_RESULT)
            val veinId = data?.getStringExtra(EXTRA_VEIN_ID)
            updateUI(veinResult, veinId)
        }
    }

    private fun initViews() {
        ivResult = findViewById(R.id.ivResult)
        tvResultTitle = findViewById(R.id.tvResultTitle)
        tvNameLabel = findViewById(R.id.tvNameLabel)
        tvName = findViewById(R.id.tvName)
        tvIdLine = findViewById(R.id.tvIdLine)
        btnFinish = findViewById(R.id.btnFinish)
        llRetryActions = findViewById(R.id.llRetryActions)
        btnRetry = findViewById(R.id.btnRetry)
        btnBack = findViewById(R.id.btnBack)

        tvMessage = findViewById(R.id.tvMessage)
        tvSimilarity = findViewById(R.id.tvSimilarity)
    }

    private fun handleIntent() {

        // 新しいモジュラー顔認証フローを最初に確認します
        if (intent.hasExtra(EXTRA_NEW_STATUS)) {
            handleNewFaceAuthIntent()
            return
        }

        val veinResult = intent.getStringExtra(EXTRA_VEIN_RESULT)
        val veinId = intent.getStringExtra(EXTRA_VEIN_ID)

        // Flow1: nhận thêm RESULTNAME + RESULTID từ camera app
        faceName = intent.getStringExtra("ResultName")
        faceId = intent.getStringExtra("ResultID")

        currentAuthMode =
                intent.getStringExtra(EXTRA_AUTH_MODE)
                        ?: getSharedPreferences("AuthMode", MODE_PRIVATE).getString("AuthName", "")
                                ?: ""

        val isSuccess = veinResult == "OK"

        ivResult.setImageResource(
                if (isSuccess) R.drawable.ic_check_circle else R.drawable.ic_error
        )
        ivResult.setColorFilter(
                resources.getColor(
                        if (isSuccess) R.color.success_green else R.color.error_red,
                        theme
                )
        )

        tvResultTitle.text =
                getString(if (isSuccess) R.string.auth_success else R.string.auth_failed)

        tvResultTitle.setTextColor(
                resources.getColor(
                        if (isSuccess) R.color.success_green else R.color.error_red,
                        theme
                )
        )

        // Flow1: Hiển thị 氏名 + ID (một dòng "ID: 12345")
        if (currentAuthMode == "Face" && !faceName.isNullOrEmpty()) {
            tvNameLabel.visibility = View.VISIBLE
            tvName.visibility = View.VISIBLE
            tvName.text = faceName

            if (!faceId.isNullOrEmpty()) {
                tvIdLine.text = "ID: $faceId"
                tvIdLine.visibility = View.VISIBLE
            } else {
                tvIdLine.visibility = View.GONE
            }

            // ログ送信 (Face Only)
            uploadAuthLog(isSuccess = true, veinId = null, veinResultStr = null)

            showButtons(isSuccess = true) // Flow1 luôn nút 終了
            return
        }

        // Flow2 & Flow3: chỉ hiển thị ID nếu có, không dùng name
        if (isSuccess && !veinId.isNullOrEmpty()) {
            tvIdLine.text = "ID: $veinId"
            tvIdLine.visibility = View.VISIBLE
        } else {
            tvIdLine.visibility = View.GONE
        }

        tvNameLabel.visibility = View.GONE
        tvName.visibility = View.GONE

        // ログ送信 (Vein / FaceAndVein)
        uploadAuthLog(isSuccess, veinId, veinResult)

        showButtons(isSuccess)
    }

    private fun handleNewFaceAuthIntent() {
        val status = intent.getIntExtra(EXTRA_NEW_STATUS, -1)
        val message = intent.getStringExtra(EXTRA_NEW_MESSAGE)
        val similarity = intent.getStringExtra(EXTRA_NEW_SIMILARITY)
        val name = intent.getStringExtra(EXTRA_NEW_NAME)
        val realId = intent.getStringExtra(EXTRA_NEW_ID)

        val isSuccess = (status == 2)

        // 1. アイコンとタイトルの色
        ivResult.setImageResource(
                if (isSuccess) R.drawable.ic_check_circle else R.drawable.ic_error
        )
        val colorRes = if (isSuccess) R.color.success_green else R.color.error_red
        ivResult.setColorFilter(resources.getColor(colorRes, theme))
        tvResultTitle.setTextColor(resources.getColor(colorRes, theme))

        // 2. タイトルテキスト
        tvResultTitle.text = if (isSuccess) "認証成功" else "認証失敗"

        // 3. メッセージと類似度
        if (!message.isNullOrEmpty()) {
            tvMessage.text = "メッセージ：$message"
            tvMessage.visibility = View.VISIBLE
        } else {
            tvMessage.visibility = View.GONE
        }

        if (!similarity.isNullOrEmpty()) {
            tvSimilarity.text = "識別スコア: $similarity%"
            tvSimilarity.visibility = View.VISIBLE
        } else {
            tvSimilarity.visibility = View.GONE
        }

        // 4. 名前とID
        if (isSuccess) {
            if (!name.isNullOrEmpty()) {
                tvNameLabel.visibility = View.VISIBLE
                tvName.visibility = View.VISIBLE
                tvName.text = name
            }
            if (!realId.isNullOrEmpty()) {
                tvIdLine.text = "ID: $realId"
                tvIdLine.visibility = View.VISIBLE
            }
            tvIdLine.visibility = View.GONE
        }
        
        // Debug: Show candidates if present
        val candidates = intent.getStringArrayListExtra(EXTRA_CANDIDATE_LIST)
        if (candidates != null && candidates.isNotEmpty()) {
             tvMessage.text = "${tvMessage.text}\n[Debug] Candidates: ${candidates.joinToString(",")}"
             tvMessage.visibility = View.VISIBLE
        }

        // 5. ボタン（新しいフローでは現在は終了ボタンのみ）
        btnFinish.visibility = View.VISIBLE
        llRetryActions.visibility = View.GONE

        // ログ送信は後で追加?
    }

    private fun showButtons(isSuccess: Boolean) {
        if (isSuccess) {
            btnFinish.visibility = View.VISIBLE
            llRetryActions.visibility = View.GONE
        } else {
            btnFinish.visibility = View.GONE
            llRetryActions.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {

        // 成功時：「終了」→ TopActivityへ
        btnFinish.setOnClickListener {
            startActivity(
                    Intent(this, TopActivity::class.java)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
            )
            finish()
        }

        // 失敗時：「再実行」
        btnRetry.setOnClickListener {
            when (currentAuthMode) {

                // Flow1: retry → chạy lại Face (quy về FaceAndVein để Top xử lý được)
                "Face" -> {
                    val intent =
                            Intent(this, TopActivity::class.java).apply {
                                addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                                putExtra(TopActivity.EXTRA_RETRY_FLOW, "FaceAndVein")
                            }
                    startActivity(intent)
                    finish()
                }

                // Flow2 → Vein retry
                "Vein" -> {
                    val intent =
                            Intent(this, TopActivity::class.java).apply {
                                addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                                putExtra(TopActivity.EXTRA_RETRY_FLOW, "Vein")
                            }
                    startActivity(intent)
                    finish()
                }

                // Flow3 → Face+Vein retry
                "FaceAndVein" -> {
                    val intent =
                            Intent(this, TopActivity::class.java).apply {
                                addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                                putExtra(TopActivity.EXTRA_RETRY_FLOW, "FaceAndVein")
                            }
                    startActivity(intent)
                    finish()
                }
            }
        }

        // 「戻る」→ TopActivity
        btnBack.setOnClickListener {
            startActivity(
                    Intent(this, TopActivity::class.java)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
            )
            finish()
        }
    }

    // PalmSecure 再実行から返ってきた結果更新
    private fun updateUI(veinResult: String?, veinId: String?) {

        val isSuccess = veinResult == "OK"

        ivResult.setImageResource(
                if (isSuccess) R.drawable.ic_check_circle else R.drawable.ic_error
        )
        ivResult.setColorFilter(
                resources.getColor(
                        if (isSuccess) R.color.success_green else R.color.error_red,
                        theme
                )
        )

        tvResultTitle.text =
                getString(if (isSuccess) R.string.auth_success else R.string.auth_failed)

        if (isSuccess && !veinId.isNullOrEmpty()) {
            tvIdLine.text = "ID: $veinId"
            tvIdLine.visibility = View.VISIBLE
        } else {
            tvIdLine.visibility = View.GONE
        }

        tvNameLabel.visibility = View.GONE
        tvName.visibility = View.GONE

        showButtons(isSuccess)

        // ============================================
        // ログ送信機能の呼び出し
        // 新機能: 認証結果をサーバーへ送信する
        // ============================================
        uploadAuthLog(isSuccess, veinId, veinResult)
    }

    /** 認証結果ログをアップロードする フローに応じてユーザーIDや認証モードを判定して送信 */
    private fun uploadAuthLog(isSuccess: Boolean, veinId: String?, veinResultStr: String?) {
        try {
            // 1. デバイスシリアル取得
            val serialNo = DeviceSerialHelper.getDeviceSerial(applicationContext)

            // 2. 認証モードを数値に変換 (API仕様に合わせる)
            // 0:Face, 1:Vein, 2:FaceAndVein
            // currentAuthMode は "Face", "Vein", "FaceAndVein" のいずれか
            val modeInt =
                    when (currentAuthMode) {
                        "Face" -> 0
                        "Vein" -> 1
                        "FaceAndVein" -> 2
                        else -> -1 // 未定義
                    }

            // 3. UserId と UserName の特定
            var userId = ""
            var userName: String? = null

            if (currentAuthMode == "Face") {
                // 顔認証の場合: Face3Activity から渡された faceId / faceName を使用
                userId = faceId ?: ""
                userName = faceName
            } else {
                // 静脈またはハイブリッドの場合: 結果の veinId を使用
                // ハイブリッドでも最終OKなら veinId が入る。NGなら null なので空文字
                userId = veinId ?: ""
                // 静脈認証だけでは名前は取れないため null (サーバー側でマスタ管理していれば不要だが、念のため)
                userName = null

                // フロー3(Face+Vein)の場合、Face段階の名前があるならそれを使うことも可能だが
                // ここではシンプルに静脈IDを主とする
                if (currentAuthMode == "FaceAndVein" && !faceName.isNullOrEmpty()) {
                    userName = faceName
                }
            }

            // 4. エラーメッセージの生成
            // モードに応じたプレフィックス ("顔", "静脈", "顔＋静脈") を付与
            val modePrefix =
                    when (currentAuthMode) {
                        "Face" -> "顔"
                        "Vein" -> "静脈"
                        "FaceAndVein" -> "顔＋静脈"
                        else -> ""
                    }
            val statusText = if (isSuccess) "認証成功" else "認証失敗"
            val errorMessage = modePrefix + statusText

            // 5. 送信実行
            if (userId.isNotEmpty() || !isSuccess) {
                // IDがある、または失敗時(IDなしでも記録残す)にログ送信
                DeviceApiClient()
                        .sendAuthLog(
                                serialNo = serialNo,
                                userId = userId,
                                userName = userName,
                                authMode = modeInt,
                                isSuccess = isSuccess,
                                errorMessage = errorMessage
                        )
            }
        } catch (e: Exception) {
            // ログ送信失敗してもメインフロー(UI)は止めない
            e.printStackTrace()
        }
    }
}
