/*
* GUISample.kt
*
*	All Rights Reserved, Copyright(c) FUJITSU FRONTECH LIMITED 2025
*/

package com.fujitsu.frontech.palmsecure_gui_sample

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.fujitsu.frontech.palmsecure.util.PalmSecureConstant
import com.fujitsu.frontech.palmsecure_sample.data.PsThreadResult
import com.fujitsu.frontech.palmsecure_sample.dialog.UserListDialog
import com.fujitsu.frontech.palmsecure_sample.service.PsService
import com.fujitsu.frontech.palmsecure_sample.util.ImageSaver
import com.fujitsu.frontech.palmsecure_sample.xml.PsFileAccessorIni
import com.fujitsu.frontech.palmsecure_gui_sample.PsCaptureDataSaver
import kotlin.math.cos
import kotlin.math.sin


interface GUISampleListener {
    fun guiSampleNotifyProgressMessage(message: String)
    fun guiSampleNotifyGuidance(message: String)
    fun guiSampleNotifySilhouette(bitmap: Bitmap)
    fun guiSampleNotifyPosture(posture: Posture)
    fun guiSampleNotifyResult(result: Result)
    fun guiSampleNotifyOffset(offset: Offset)
    fun guiSampleNotifyCount(count: Int)
    fun guiSampleGetUserId(userId: String)
}

class Constants {
    companion object {
        const val IMAGE_WIDTH: Float = 640f
        const val IMAGE_HEIGHT: Float = 640f
        const val HAND_IMAGE_WIDTH: Float = 640f
        const val HAND_IMAGE_HEIGHT: Float = 480f
        const val CIRCLE_RAD: Float = 265f
        const val LEVEL_RAD: Float = 20f
        private const val DIST_LIMIT: Float = 200f - 50f
        const val DIST_OFFSET_VERIFY: Float = 70f - 50f
        private const val DIST_CIRCLE_MIN: Float = 20f
        private const val POSTURE_DEG_MAX: Float = 90f
        const val DIST_BORDER: Float = 150f - 50f
        const val DIST_MIN_ENROLL: Float = 40f - 50f
        const val DIST_MIN_VERIFY: Float = 35f - 50f
        const val IMAGE_CENTER_X: Float = IMAGE_WIDTH / 2
        const val IMAGE_CENTER_Y: Float = IMAGE_HEIGHT / 2
        const val CIRCLE_LINE: Float = IMAGE_CENTER_X - CIRCLE_RAD
        const val DIST_ADJUST: Float = (CIRCLE_RAD - DIST_CIRCLE_MIN) / DIST_LIMIT
        const val POSTURE_ADJUST: Float = CIRCLE_RAD / POSTURE_DEG_MAX
        const val HAND_IMAGE_TOP: Float = (IMAGE_HEIGHT - HAND_IMAGE_HEIGHT) / 2

    }
}

enum class Offset {
    ENROLL,
    VERIFY
}

enum class Status {
    NO_HANDS,
    NONE,
    ONLY_Z,
    ONLY_XYZ,
    ONLY_XYZ_AND_YAW,
    ALL,
    AWAY_HANDS
}

enum class Result {
    SUCCESSFUL,
    FAILED,
    CANCELED,
    ERROR
}

data class Posture (
    var x: Float,
    var y: Float,
    var z: Float,
    var pitch: Float,
    var roll: Float,
    var yaw: Float,
    var status: Status
)

@SuppressLint("SetTextI18n")
class GUISample(parent: MainActivity) : View.OnClickListener, GUISampleListener, AutoCloseable {
    private val activity = parent
    private val dp = activity.resources.displayMetrics.density
    private val soundPool = SoundPool.Builder().setAudioAttributes(AudioAttributes
        .Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(
            AudioAttributes.CONTENT_TYPE_SPEECH).build()).setMaxStreams(2).build()
    private val okSound = soundPool.load(activity, R.raw.ok, 1)
    private val ngSound = soundPool.load(activity, R.raw.ng, 1)
    private val buttonLayout = activity.findViewById<LinearLayout>(R.id.buttonLayout)
    private val buttonLayout2 = activity.findViewById<LinearLayout>(R.id.buttonLayout2)
    private val enrollButton = activity.findViewById<Button>(R.id.enrollButton)
    private val verifyButton = activity.findViewById<Button>(R.id.verifyButton)
    private val cancelButton = activity.findViewById<Button>(R.id.cancelButton)
    private val cancelButton2 = activity.findViewById<Button>(R.id.cancelButton2)
    private val endButton = activity.findViewById<Button>(R.id.endButton)
    private val userIdInput = activity.findViewById<EditText>(R.id.userIdInput)
    private val hiddenUserIdInput = activity.findViewById<TextView>(R.id.hiddenUserIdInput)
    private val listUsersButton = activity.findViewById<Button>(R.id.listUsersButton)
    private val deleteUserButton = activity.findViewById<Button>(R.id.deleteUserButton)
    private val identifyButton = activity.findViewById<Button>(R.id.identifyButton)
    private val progressMessage = activity.findViewById<TextView>(R.id.progressMessage)
    private val guideMessage = activity.findViewById<TextView>(R.id.guideMessage)
    private val imageView = activity.findViewById<ImageView>(R.id.imageView)
    private val msgView = activity.findViewById<ImageView>(R.id.messageView)
    private val okImg = BitmapFactory.decodeResource(activity.getResources(), R.drawable.ok)
    private val ngImg = BitmapFactory.decodeResource(activity.getResources(), R.drawable.ng)
    private val image = Bitmap.createBitmap((Constants.IMAGE_WIDTH * dp).toInt(),
        (Constants.IMAGE_HEIGHT * dp).toInt(), Bitmap.Config.ARGB_8888)
    private val image2 = Bitmap.createBitmap((Constants.IMAGE_WIDTH * dp).toInt(),
        (Constants.IMAGE_HEIGHT * dp).toInt(), Bitmap.Config.ARGB_8888)
    private var handImage = Bitmap.createBitmap((Constants.HAND_IMAGE_WIDTH * dp).toInt(),
        (Constants.HAND_IMAGE_HEIGHT * dp).toInt(), Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(image)
    private val canvas2 = Canvas(image2)
    private val paint = Paint()
    private var offset = Offset.ENROLL
    private var count = 0
    private var waiting = 0
    private var refresh = 0
    private var posture = Posture(0f, 0f, 0f, 0f, 0f, 0f, Status.NO_HANDS)

    // event
    private val handler = Handler(Looper.getMainLooper())
    private val guiListener: GUISampleListener = this
    private val service = PsService(activity, this)
    private var batchCandidates: ArrayList<String>? = null

    init {

        // resource string
        enrollButton.text = activity.getString(R.string.EnrollBtn)
        verifyButton.text = activity.getString(R.string.VerifyBtn)
    identifyButton?.text = activity.getString(R.string.WorkIdentify)
        cancelButton.text = activity.getString(R.string.CancelBtn)
        endButton.text = activity.getString(R.string.ExitBtn)

        // Clear user ID input
        userIdInput.setText("")
        hiddenUserIdInput.setText("")

        // Add text change listener to update button states
        userIdInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                setButtonEnable(true) // Refresh button states when user ID changes
            }
        })

        setProgressMessage("")
        setGuideMessage("")
        paintNoHand()

        // initialize PalmSecure
        service.Ps_Sample_Apl_Java_InitAuthDataFile()

        var resInit = PsThreadResult()
        resInit.result = -1L
        if (PsFileAccessorIni.GetInstance(activity) != null) {
            val param = GUIHelper(activity).createInitParam()
            resInit = service.Ps_Sample_Apl_Java_Request_InitLibrary(param)
        }
        if (resInit.result != PalmSecureConstant.JAVA_BioAPI_OK) {
            val err = if (resInit.result == -1L) "Read Error: "+PsFileAccessorIni.FileName
                else GUIHelper(activity).getErrorMessage(resInit)
            setGuideMessage(err)
            enrollButton.isEnabled = false
            verifyButton.isEnabled = false
            cancelButton.isEnabled = false
            endButton.isEnabled = false

            // initialize Error
            AlertDialog.Builder(activity)
                .setCancelable(false)
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(guideMessage.text)
                .setPositiveButton("OK") { _, _ -> activity.finish() }
                .create().show()
        }
        else {
            service.Ps_Sample_Apl_Java_InitIdList()

            val lvl = PsFileAccessorIni.GetInstance(activity).GetValueInteger(PsFileAccessorIni.MessageLevel)
            when (lvl) {
                2 -> {
                    msgView.visibility = View.INVISIBLE
                }
                else -> {
                    guideMessage.visibility = View.INVISIBLE
                    progressMessage.visibility = View.INVISIBLE
                }
            }
            enrollButton.setOnClickListener(this)
            verifyButton.setOnClickListener(this)
            identifyButton.setOnClickListener(this)
            cancelButton.setOnClickListener(this)
            cancelButton2.setOnClickListener(this)
            endButton.setOnClickListener(this)
            listUsersButton.setOnClickListener(this)
            deleteUserButton.setOnClickListener(this)
//            activity.findViewById<Button>(R.id.captureButton)?.setOnClickListener(this)
            // 外部アプリ（認証デモ）から起動した場合のみ「戻る」ボタンを表示
            val btnGoBack = activity.findViewById<ImageButton>(R.id.btnGoBack)
            btnGoBack.visibility = View.GONE // デフォルト非表示（後で fromExternal に応じて切り替え）
            btnGoBack.setOnClickListener { activity.finish() }
            setButtonEnable(true)

            // BodyCameraアプリからの外部起動時インテント情報を読み取る
            // mode: 動作モード（"verify" / "identify"）
            // face_id: 照合対象ユーザーID（Flow2用。1:1静脈認証で使用）
            // auto_start: 起動後すぐに認証を開始するかどうか（true/false）
            // from_external: 外部アプリ（BodyCamera）からの起動かどうか
            // candidate_list: 顔認証TopKで絞り込んだ候補者IDリスト（Flow3専用）
            val mode = activity.intent.getStringExtra("mode")
            val faceId = activity.intent.getStringExtra("face_id")
            val autoStart = activity.intent.getBooleanExtra("auto_start", false)
            val fromExternal = activity.intent.getBooleanExtra("from_external", false)
            val candidates = activity.intent.getStringArrayListExtra("candidate_list")
            
            Log.i("GUISample", "起動インテント受信: mode=$mode face_id=$faceId auto_start=$autoStart candidates=${candidates?.size}人")

            // 外部アプリから起動した場合はUIボタンレイアウトを切り替える
            // （BodyCamera連携時は専用ボタン群を表示、単体動作時はデフォルトボタン群を表示）
            if (fromExternal == true){
                buttonLayout.visibility = View.INVISIBLE
                buttonLayout2.visibility = View.VISIBLE
                btnGoBack.visibility = View.VISIBLE  // 認証デモからの遷移時のみ表示
            }else{
                buttonLayout.visibility = View.VISIBLE
                buttonLayout2.visibility = View.INVISIBLE
                btnGoBack.visibility = View.GONE     // 単体起動時は非表示
            }

            // ─── 起動モードに応じた自動処理 ──────────────────────────────────
            if (candidates != null && candidates.isNotEmpty()) {
                // [Flow3 TopKバッチモード] 顔認証で絞り込まれた候補者リストを受け取った場合
                // → 候補者リストを保存し、auto_start が true であれば即座にバッチ照合を開始
                batchCandidates = candidates
                Log.i("GUISample", "★ [ID確認] Intent受信 candidate_list: ${candidates.size}件")
                candidates.forEachIndexed { i: Int, id: String ->
                    Log.i("GUISample", "★ [ID確認]   candidate[$i] = \"$id\"")
                }
                setButtonEnable(true)
                if (autoStart) {
                    if (service.getIdList().isEmpty()) {
                        Log.w("GUISample", "★ [Flow3] 静脈データが1件も登録されていません → スキャンをスキップ")
                        returnNoVeinData()
                    } else {
                        verifyBatchClickEvent()
                    }
                }
            } else if (mode == "verify" && !faceId.isNullOrEmpty()) {
                // [Flow2 静脈のみモード] 単一のユーザーIDで 1:1 静脈認証を行う場合
                // → IDフィールドに face_id を事前入力し、auto_start が true であれば即座に認証開始
                userIdInput.setText(faceId)
                setButtonEnable(true)
                if (autoStart) {
                    if (service.getIdList().isEmpty()) {
                        Log.w("GUISample", "★ [Flow2-verify] 静脈データが1件も登録されていません → スキャンをスキップ")
                        returnNoVeinData()
                    } else {
                        verifyClickEvent()
                    }
                }
            } else if (mode == "identify") {
                // [Flow2/識別モード] IDなしで全登録ユーザーと照合する場合
                if (autoStart) {
                    if (service.getIdList().isEmpty()) {
                        Log.w("GUISample", "★ [Flow2-identify] 静脈データが1件も登録されていません → スキャンをスキップ")
                        returnNoVeinData()
                    } else {
                        identifyClickEvent()
                    }
                }
            }
            // 上記条件に合致しない場合は手動操作待ちの通常状態（PalmSecure単体動作）
        }
    }

    /**
     * 静脈データが1件も登録されていない場合に即座に呼び出し元へ返す。
     * スキャンを開始せずに NG + no_vein_data=true を返却し、Activity を終了する。
     */
    private fun returnNoVeinData() {
        val shouldReturn = activity.intent.getBooleanExtra("return_result", false)
        if (shouldReturn) {
            handler.post {
                val data = android.content.Intent().apply {
                    putExtra("vein_result", "NG")
                    putExtra("vein_id", "")
                    putExtra("not_registered", true)
                    putExtra("no_vein_data", true)
                }
                activity.setResult(android.app.Activity.RESULT_OK, data)
                activity.finish()
            }
        }
    }

    /**
     * 顔認証TopKで絞り込まれた候補者リスト（K人）に対して
     * 1:K 静脈バッチ照合（Identify）を開始する。
     * Flow3（顔＋静脈認証フロー）のみで呼び出される。
     *
     * 通常の 1:1 認証（verifyClickEvent）とは完全に独立した処理であり、
     * 単体動作および Flow2 には影響しない。
     */
    private fun verifyBatchClickEvent() {
        // 候補者リストが未設定または空の場合は何もしない
        val candidates = batchCandidates ?: return
        if (candidates.isEmpty()) return

        setButtonEnable(false)
        resetPosture(Status.NONE)

        // PsService 経由でバッチ照合スレッドを起動する
        // ※ Batch モードでは userID は不使用（照合はcandidateList内のIDに対して行われる）
        val param = GUIHelper(activity).createThreadParam("Batch")
        service.Ps_Sample_Apl_Java_Request_VerifyBatch(candidates, param)

        // ユーザー向けに進行状況メッセージを表示する
        guiListener.guiSampleNotifyOffset(Offset.VERIFY)
        guiListener.guiSampleNotifyProgressMessage(
            "顔認証完了 (TopK ${candidates.size}名): 静脈認証を開始してください"
        )
    }

    override fun close() {

        service.Ps_Sample_Apl_Java_Request_TermLibrary()
    }

    override fun guiSampleNotifyProgressMessage(message: String) {
        handler.post {
            setProgressMessage(message)
        }
    }

    override fun guiSampleNotifyGuidance(message: String) {
        handler.post {
            setGuideMessage(message)
        }
    }

    override fun guiSampleNotifySilhouette(bitmap: Bitmap) {
        handler.post {
            setHandImage(bitmap)
        }
    }

    override fun guiSampleNotifyPosture(posture: Posture) {
        handler.post {
            setPosture(posture)
        }
    }

    override fun guiSampleNotifyResult(result: Result) {
        handler.post {
            paintResult(result)
            if (result == Result.SUCCESSFUL) {
                // Refresh user list after successful enrollment
                service.Ps_Sample_Apl_Java_RefreshIdList()
            }
            setButtonEnable(true)

            // Only return result if explicitly requested by caller
            val shouldReturn = activity.intent.getBooleanExtra("return_result", false)
            Log.i("GUISample", "◆ [認証] guiSampleNotifyResult: result=$result shouldReturn=$shouldReturn")
            if (shouldReturn) {
                try {
                    val veinResult = if (result == Result.SUCCESSFUL) "OK" else "NG"
                    val veinId = if (!userIdInput.text.isEmpty()) {
                        userIdInput.text.toString().trim()
                    } else {
                        hiddenUserIdInput.text.toString().trim()
                    }

                    // 未登録判定: 認証失敗時に静脈IDが存在しないか確認する
                    val notRegistered = if (result == Result.FAILED) {
                        // Flow2: batchCandidatesなし → 常に未登録扱い
                        // Flow3: VerifyBatch失敗 → 静脈が誰にも一致しない = 未登録扱い
                        true
                    } else {
                        false
                    }
                    Log.i("GUISample", "◆ [認証] 呼び出し元へ返却: vein_result=$veinResult vein_id=$veinId not_registered=$notRegistered")
                    val data = android.content.Intent().apply {
                        putExtra("vein_result", veinResult)
                        putExtra("vein_id", veinId)
                        putExtra("not_registered", notRegistered)
                    }
                    activity.setResult(android.app.Activity.RESULT_OK, data)
                    activity.finish()
                } catch (e: Exception) {
                    android.util.Log.e("GUISample", "setResult error: ${e.message}")
                }
            }
        }
    }

    override fun guiSampleNotifyOffset(offset: Offset) {
        handler.post {
            this.offset = offset
        }
    }

    override fun guiSampleNotifyCount(count: Int) {
        handler.post {
            this.count = count
        }
    }

    override fun guiSampleGetUserId(userId: String){
        handler.post {
            setUserId(userId)
        }
    }

    override fun onClick(view: View) {
        when (view) {
            enrollButton -> enrollClickEvent()
            verifyButton -> verifyClickEvent()
            identifyButton -> identifyClickEvent()
            cancelButton -> cancelClickEvent()
            cancelButton2 -> cancelClickEvent()
            endButton -> endClickEvent()
            listUsersButton -> listUsersClickEvent()
            deleteUserButton -> deleteUserClickEvent()
//            activity.findViewById<Button>(R.id.captureButton) -> captureClickEvent()
        }
    }

    private fun identifyClickEvent() {
        // Start identification (scan-first), no user ID required
        // Clear any selected user ID so the ID field is empty in identify mode
        userIdInput.setText("")
        hiddenUserIdInput.setText("")
        setButtonEnable(false)
        resetPosture(Status.NONE)

        val param = GUIHelper(activity).createThreadParam("")
        service.Ps_Sample_Apl_Java_Request_Identify(param)

        guiListener.guiSampleNotifyOffset(Offset.VERIFY)
        guiListener.guiSampleNotifyProgressMessage("識別")
    }

    private fun enrollClickEvent() {
        val userId = userIdInput.text.toString().trim()
        if (userId.isEmpty()) {
            setGuideMessage(activity.getString(R.string.enter_user_id))
            return
        }

        // Check for duplicate ID
        if (service.Ps_Sample_Apl_Java_RegisteredId(userId)) {
            setGuideMessage(activity.getString(R.string.duplicate_id_error))
            return
        }

        setButtonEnable(false)
        resetPosture(Status.NONE)

        // enroll with dynamic user ID
        val param = GUIHelper(activity).createThreadParam(userId)
        service.Ps_Sample_Apl_Java_Request_Enroll(param)

        guiListener.guiSampleNotifyOffset(Offset.ENROLL)
        guiListener.guiSampleNotifyProgressMessage(
            activity.getString(R.string.enrolling_user, userId)
        )
    }

    private fun verifyClickEvent() {
        val userId = userIdInput.text.toString().trim()
        if (userId.isEmpty()) {
            setGuideMessage(activity.getString(R.string.enter_user_id))
            return
        }

        setButtonEnable(false)
        resetPosture(Status.NONE)

        // verify with dynamic user ID
        val param = GUIHelper(activity).createThreadParam(userId)
        service.Ps_Sample_Apl_Java_Request_Verify(param)

        guiListener.guiSampleNotifyOffset(Offset.VERIFY)
        guiListener.guiSampleNotifyProgressMessage(
            activity.getString(R.string.WorkVerifyStart)
        )
    }

    private fun cancelClickEvent() {
        setButtonEnable(true)

        // cancel
        service.Ps_Sample_Apl_Java_Request_Cancel()
    }

    private fun endClickEvent() {
        activity.finish()
    }

    private fun captureClickEvent() {
        setButtonEnable(false)
        resetPosture(Status.NONE)

        // Get the raw capture data from the PalmSecure service's silhouette
        val captureData: ByteArray? = synchronized(this) {
            service.silhouette?.clone() // Make a safe copy of the byte array
        }

        Thread {
            var savedPath: String? = null
            if (captureData != null && captureData.isNotEmpty()) {
                // Save the raw palm vein capture data as a .dat file
                savedPath = PsCaptureDataSaver(activity).saveCaptureData(captureData)
            }
            handler.post {
                if (savedPath != null) {
                    setGuideMessage(activity.getString(R.string.save_success, savedPath))
                    setProgressMessage(activity.getString(R.string.capture_success))
                } else {
                    setGuideMessage(activity.getString(R.string.save_failed))
                    setProgressMessage(activity.getString(R.string.capture_failed))
                }
                setButtonEnable(true)
            }
        }.start()
    }

    private fun listUsersClickEvent() {
        try {
            service.Ps_Sample_Apl_Java_RefreshIdList()
            val userList = service.getIdList()

            if (userList.isEmpty()) {
                setGuideMessage(activity.getString(R.string.no_users_registered))
            } else {
                // Show dialog with vertical list
                UserListDialog(activity, userList) { selectedId ->
                    userIdInput.setText(selectedId)
                    setButtonEnable(true)
                }.show()
            }
        } catch (e: Exception) {
            setGuideMessage(activity.getString(R.string.error_listing_users))
        }
    }

    private fun deleteUserClickEvent() {
        val userId = userIdInput.text.toString().trim()
        if (userId.isEmpty()) {
            setGuideMessage(activity.getString(R.string.delete_user_prompt))
            return
        }

        try {
            service.Ps_Sample_Apl_Java_DeleteId(userId)
            setGuideMessage(activity.getString(R.string.delete_user_success, userId))
            // Clear the user ID input field after successful deletion
            userIdInput.setText("")
            hiddenUserIdInput.setText("")
            setButtonEnable(true) // Refresh button states
        } catch (e: Exception) {
            setGuideMessage(activity.getString(R.string.delete_user_error, e.message))
        }
    }

    private fun setProgressMessage(message: String) {
        progressMessage.text = message
    }

    private fun setGuideMessage(message: String) {
        guideMessage.text = message
    }

    private fun setUserId(userId: String) {
        hiddenUserIdInput.text = userId
    }

    private fun setButtonEnable(flag: Boolean) {
        val userId = userIdInput.text.toString().trim()
        val isUserRegistered = if (userId.isNotEmpty()) {
            service.Ps_Sample_Apl_Java_RegisteredId(userId)
        } else {
            false
        }

        enrollButton.isEnabled = flag
        verifyButton.isEnabled = flag && isUserRegistered
        cancelButton.isEnabled = !flag
        endButton.isEnabled = flag
        listUsersButton.isEnabled = flag
        deleteUserButton.isEnabled = flag && isUserRegistered
        userIdInput.isEnabled = flag

        val anim = imageView.background
        if (anim is Animatable)
            if (flag) {
                imageView.setBackground(null)
                imageView.setBackgroundResource(R.drawable.guide)
            }
            else anim.start()
    }

    private fun paintResult(result: Result) {
        when (result) {
            Result.CANCELED -> {
                msgView.setBackgroundResource(R.color.black)
            }
            Result.SUCCESSFUL -> {
                imageView.setImageBitmap(okImg)
                soundPool.play(okSound, 1.0f, 1.0f, 0, 0, 1.0f)
                val anim = imageView.background
                if (anim is Animatable && count > 0) {
                    anim.stop()
                    msgView.setBackgroundResource(R.drawable.enr_3)
                }
            }
            Result.FAILED -> {
                imageView.setImageBitmap(ngImg)
                soundPool.play(ngSound, 1.0f, 1.0f, 0, 0, 1.0f)
                val anim = imageView.background
                if (anim is Animatable && count > 0) {
                    anim.stop()
                    msgView.setBackgroundResource(R.drawable.enr_3)
                }
            }
            Result.ERROR -> {
                msgView.setBackgroundResource(R.color.black)
                if (!guideMessage.isVisible) {
                    // Lib Error
                    AlertDialog.Builder(activity)
                        .setCancelable(false)
                        .setTitle(android.R.string.dialog_alert_title)
                        .setMessage(guideMessage.text)
                        .setPositiveButton("OK", null)
                        .create().show()
                }
            }
        }
        count = 0
        waiting = 0
        //setButtonEnable(true)
    }

    private fun setPosture(posture: Posture) {
        when (posture.status) {
            Status.NONE -> {
                resetPosture(Status.NONE)
            }
            Status.NO_HANDS -> {
                waiting = 0
                paintNoHand()
                // clear hand
                handImage.eraseColor(Color.BLACK)
            }
            Status.AWAY_HANDS -> {
                waiting = 1
                paintNoHand()
            }
            else -> {
                this.posture = posture.copy()
                if ((offset == Offset.ENROLL && posture.z < Constants.DIST_MIN_ENROLL)
                    || (offset == Offset.VERIFY && posture.z < Constants.DIST_MIN_VERIFY)) {
                    // clear hand
                    handImage.eraseColor(Color.BLACK)
                }
                paintAll()
                refresh = 1
            }
        }
    }

    private fun resetPosture(status: Status) {
        posture = Posture(0f, 0f, 0f, 0f, 0f, 0f, status)
    }

    private fun setHandImage(hand: Bitmap) {
        handImage = hand.copy(Bitmap.Config.ARGB_8888, true)
        when (refresh) {
            0 -> paintAll()
            else -> refresh = 0
        }
    }

    private fun paintScenery() {
        paint.style = Paint.Style.STROKE
        paint.setARGB(255, 201,219, 250)
        paint.strokeWidth = (Constants.CIRCLE_LINE - 30) * dp
        canvas.drawCircle(
            Constants.IMAGE_CENTER_X * dp, Constants.IMAGE_CENTER_Y * dp,
            (Constants.CIRCLE_RAD + Constants.CIRCLE_LINE / 2 - 15) * dp, paint)
    }

    private fun paintScenery2() {
        paint.style = Paint.Style.STROKE
        paint.setARGB(255, 201,219, 250)
        paint.strokeWidth = (Constants.CIRCLE_LINE - 30) * dp
        canvas2.drawCircle(
            Constants.IMAGE_CENTER_X * dp, Constants.IMAGE_CENTER_Y * dp,
            (Constants.CIRCLE_RAD + Constants.CIRCLE_LINE / 2 - 15) * dp, paint)
    }

    private fun paintHand() {
        val matrix = Matrix()
        matrix.preScale(-dp, dp)
        canvas.drawBitmap(Bitmap.createBitmap(
            handImage, 0, 0, handImage.width, handImage.height, matrix, false),
            0f, Constants.HAND_IMAGE_TOP * dp, null)
    }

    private fun paintCross() {
        paint.strokeWidth = 2 * dp
        paint.color = Color.GREEN
        canvas.drawLine(0f, Constants.IMAGE_CENTER_Y * dp,
            Constants.IMAGE_WIDTH * dp, Constants.IMAGE_CENTER_Y * dp, paint)
        paint.color = Color.CYAN
        canvas.drawLine(
            Constants.IMAGE_CENTER_X * dp, 0f,
            Constants.IMAGE_CENTER_X * dp, Constants.IMAGE_HEIGHT * dp, paint)
    }

    private fun paintDistance() {
//        val r = Constants.CIRCLE_RAD - ((posture.z - if (offset == Offset.VERIFY)
        val cr = when {
            (posture.z > Constants.DIST_BORDER) -> Constants.DIST_BORDER
            (posture.z < 0f) -> (posture.z * 2)
            else -> posture.z
        }
        val r = Constants.CIRCLE_RAD - ((cr - if (offset == Offset.VERIFY)
            Constants.DIST_OFFSET_VERIFY else 0f) * Constants.DIST_ADJUST)
        paint.setARGB(128, 255, 255, 255)
        paint.style = Paint.Style.FILL
        if (posture.status == Status.ONLY_Z) {
            canvas.drawCircle(
                Constants.IMAGE_CENTER_X * dp,
                Constants.IMAGE_CENTER_Y * dp,
                r * dp, paint)
        } else if (posture.status.ordinal >= Status.ONLY_XYZ.ordinal) {
            canvas.drawCircle(
                (posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                r * dp, paint)
        }
    }

    private fun rotate(p: PointF, deg: Float): PointF {
        val rad = Math.PI * -deg / 180f
        return PointF((p.x * cos(rad) - p.y * sin(rad)).toFloat(),
            (p.x * sin(rad) + p.y * cos(rad)).toFloat())
    }

    private fun paintPosture() {
        if (posture.status.ordinal >= Status.ONLY_XYZ_AND_YAW.ordinal) {
            val r = Constants.CIRCLE_RAD - ((posture.z - if (offset == Offset.VERIFY)
                Constants.DIST_OFFSET_VERIFY else 0f) * Constants.DIST_ADJUST)
            var p = rotate(PointF(r, 0f), posture.yaw)
            var q = rotate(PointF(-r, 0f), posture.yaw)
            paint.strokeWidth = dp
            paint.color = Color.GREEN
            canvas.drawLine(
                (p.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (p.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                (q.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (q.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                paint)
            p = rotate(PointF(0f, r), posture.yaw)
            q = rotate(PointF(0f, -r), posture.yaw)
            paint.color = Color.CYAN
            canvas.drawLine(
                (p.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (p.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                (q.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                (q.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                paint)
            if (posture.status == Status.ALL) {
                p = rotate(PointF(posture.roll * Constants.POSTURE_ADJUST,
                    posture.pitch * Constants.POSTURE_ADJUST
                ), posture.yaw)
                paint.setARGB(128, 255, 0, 0)
                canvas.drawCircle(
                    (p.x + posture.x * -Constants.DIST_ADJUST + Constants.IMAGE_CENTER_X) * dp,
                    (p.y + posture.y * Constants.DIST_ADJUST + Constants.IMAGE_CENTER_Y) * dp,
                    Constants.LEVEL_RAD * dp, paint)
            }
        }
    }

    private fun paintMessage() {
        when (count) {
            1 -> msgView.setBackgroundResource(R.drawable.gauge1)
            2 -> msgView.setBackgroundResource(R.drawable.gauge2)
            3 -> msgView.setBackgroundResource(R.drawable.gauge3)
            else -> msgView.setBackgroundResource(R.color.black)
        }
        val anim = msgView.background
        if (anim is Animatable) anim.start()
    }

    private fun paintAll() {
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawRect(Rect(0, 0, canvas.width, canvas.height), paint)
        paintHand()
        paintCross()
        paintScenery()
        paintDistance()
        paintPosture()
        imageView.setImageBitmap(image)

        paintMessage()
    }

    private fun paintNoHand() {
        paintScenery2()
        imageView.setImageBitmap(image2)
        when (waiting) {
            1 -> imageView.setBackgroundResource(R.drawable.up_1)
            else -> {
                imageView.setBackgroundResource(R.drawable.guide)
                val anim = imageView.background
                if (anim is Animatable) {
                    if (!anim.isRunning && count == 1) anim.start()
                }
            }
        }

        paintMessage()
    }
}
