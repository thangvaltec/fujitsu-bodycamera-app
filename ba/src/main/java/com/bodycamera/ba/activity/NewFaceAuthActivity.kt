package com.bodycamera.ba.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.ba.faceauth.FaceCaptureStrategy
import com.bodycamera.ba.faceauth.MakerAppCaptureStrategy
import com.bodycamera.ba.network.FaceRecognitionApi
import com.bodycamera.ba.network.model.FaceAuthResponse
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream

/**
 * 顔認証モジュラーActivity。
 * キャプチャ戦略とAPI検証を統合し、Smart Retryパターンをサポートします。
 *
 * Smart Retry フロー:
 * 1. FacePassActivityにデリゲートを設定してからキャプチャを起動
 * 2. FacePassActivity内でLiveness PASS → 画像キャプチャ → デリゲート経由でAPI検証
 * 3. API OK → FacePassActivityが終了 → onActivityResultで結果を受け取る
 * 4. API NG → FacePassActivity内でToast表示 → カメラスキャン継続（自動再試行）
 */
class NewFaceAuthActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NewFaceAuthActivity"
    }

    // UIスレッドハンドラー
    private val mainHandler = Handler(Looper.getMainLooper())

    // キャプチャ戦略
    private val captureStrategy: FaceCaptureStrategy = MakerAppCaptureStrategy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // BroadcastReceiverをonCreateで登録する（onResumeではない）
        // FacePassActivityがフォアグラウンドの時、このActivityはpaused状態になるため
        // onResumeで登録するとreceiverが解除されてBroadcastを受信できなくなる
        val filter = android.content.IntentFilter()
        filter.addAction(ACTION_PROCESS_FACE)
        filter.addAction(ACTION_CANDIDATE_LIST)
        registerReceiver(faceReceiver, filter)
        Log.d(TAG, "★ BroadcastReceiver登録完了 (ACTION_PROCESS_FACE & ACTION_CANDIDATE_LIST 待機開始)")

        if (checkPermission()) {
            startCaptureSafe()
        } else {
            requestPermission()
        }
    }

    /**
     * キャプチャ戦略を起動する前に、検証デリゲートを設定します。
     * これにより、FacePassActivity内でAPI検証が行われ、
     * NG時はカメラ画面でToast表示→自動再試行が可能になります。
     */
    private fun startCaptureSafe() {
        Log.d(TAG, "キャプチャ戦略を開始します")
        try {
            // Retrieve TopK option from Intent
            val useTopK = intent.getBooleanExtra("should_use_topk", false)
            val options = android.os.Bundle()
            options.putBoolean("should_use_topk", useTopK)
            
            captureStrategy.launchCapture(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "キャプチャ起動エラー", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * FacePassActivityに検証デリゲートを設定します。
     * デリゲートはキャプチャ画像をAPIに送信し、結果に応じてコールバックを呼び出します。
     */
    // デリゲート設定削除済み（Broadcastベースのアーキテクチャに移行）

    private fun checkPermission(): Boolean {
        return checkSelfPermission(android.Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.isNotEmpty() &&
                            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                startCaptureSafe()
            } else {
                Toast.makeText(this, "Camera permission needed", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // ------------------------------------------------------------------------
    // リファクタリング: Broadcastアーキテクチャ
    // ------------------------------------------------------------------------
    
    private var mPendingAuthResponse: FaceAuthResponse? = null
    
    // Broadcastアクション定数
    private val ACTION_PROCESS_FACE = "com.bodycamera.ba.ACTION_PROCESS_FACE"
    private val ACTION_AUTH_RESULT = "com.bodycamera.ba.ACTION_AUTH_RESULT"
    private val ACTION_CANDIDATE_LIST = "com.bodycamera.ba.ACTION_CANDIDATE_LIST"
    
    private val faceReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == ACTION_PROCESS_FACE) {
                val imagePath = intent.getStringExtra("image_path")
                if (imagePath != null) {
                    Log.d(TAG, "★受信ACTION_PROCESS_FACE: imagePath=$imagePath")
                    val file = File(imagePath)
                    if (file.exists()) {
                        Log.d(TAG, "★受信ファイル確認OK: size=${file.length()/1024}KB, path=${file.absolutePath}")
                        processFaceImage(file)
                    } else {
                        Log.e(TAG, "★受信ファイルが存在しません: $imagePath")
                        broadcastAuthResult(false, "Image file not found")
                    }
                }
            } else if (intent?.action == ACTION_CANDIDATE_LIST) {
                val candidates = intent.getStringArrayListExtra("candidate_list")
                if (candidates != null && candidates.isNotEmpty()) {
                    Log.d(TAG, "★受信ACTION_CANDIDATE_LIST: count=${candidates.size}")
                    handleCandidateList(candidates)
                }
            }
        }
    }

    /**
     * TopK候補リストを受け取った場合の処理
     */
    private fun handleCandidateList(candidates: ArrayList<String>) {
        Log.d(TAG, "═══════════════════════════════════════════")
        Log.d(TAG, "★ handleCandidateList: ${candidates.size} candidates received")
        candidates.forEachIndexed { i, token ->
            Log.d(TAG, "  → Received[$i]: $token")
        }
        Log.d(TAG, "★ Setting RESULT_OK with candidate_list → Finishing NewFaceAuthActivity...")
        Log.d(TAG, "═══════════════════════════════════════════")
        
        val data = Intent().apply {
            putStringArrayListExtra("candidate_list", candidates)
            putExtra("ResultStatus", 2)
            putExtra("ResultMessage", "TopK Candidates Found")
        }
        setResult(RESULT_OK, data)
        finish()
    }



    /**
     * FacePassActivityに結果をBroadcast送信するヘルパー
     */
    private fun broadcastAuthResult(isSuccess: Boolean, message: String, json: String? = null) {
        val intent = Intent(ACTION_AUTH_RESULT)
        intent.putExtra("is_success", isSuccess)
        intent.putExtra("message", message)
        if (json != null) {
            intent.putExtra("api_response_json", json)
        }
        sendBroadcast(intent)
        Log.d(TAG, "★ 送信ACTION_AUTH_RESULT → Maker App: Success=$isSuccess, message=$message")
    }

    /**
     * 画像ファイルをAPIに送信して結果を処理します。
     * Broadcast Architecture対応版
     */
    private fun processFaceImage(file: File) {
        Thread {
            try {
                // 1. 画像の前処理 (リサイズ・回転・圧縮)
                // ※ FacePassActivity側で1024pxリサイズ済みだが、念のため再チェック
                Log.d(TAG, "★ API処理 開始 - 元ファイル: ${file.absolutePath} (${file.length()/1024}KB)")
                val compressedFile = compressImageIfNeeded(file)
                Log.d(TAG, "★ API処理 圧縮後ファイル: ${compressedFile.absolutePath} (${compressedFile.length()/1024}KB)")

                mainHandler.post {
                    // 必要に応じてUI更新（プログレス表示等）
                }

                // 2. 設定値の取得
                val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                val serverUrl = prefs.getString(SettingsActivity.KEY_SERVER_URL, "") ?: ""
                val manualDeviceId = prefs.getString(SettingsActivity.KEY_DEVICE_ID, "") ?: ""

                if (serverUrl.isEmpty() || manualDeviceId.isEmpty()) {
                    val msg = "設定画面でURLとデバイスIDを設定してください"
                    Log.w(TAG, "★ API処理 設定不足: serverUrl=$serverUrl, deviceId=$manualDeviceId")
                    mainHandler.post { broadcastAuthResult(false, msg) }
                    return@Thread
                }

                val policeId = "null"
                Log.d(TAG, "★ API送信 送信先URL: $serverUrl")
                Log.d(TAG, "★ API送信 デバイスID: $manualDeviceId | policeId: $policeId")
                Log.d(TAG, "★ API送信 送信ファイル: ${compressedFile.name} (${compressedFile.length()/1024}KB)")

                // 3. APIリクエスト送信
                val startTime = System.currentTimeMillis()
                val responseJson = FaceRecognitionApi.sendFaceRecognition(
                        compressedFile, manualDeviceId, policeId, serverUrl
                )
                val elapsed = System.currentTimeMillis() - startTime
                Log.d(TAG, "★ API応答 応答時間: ${elapsed}ms")
                Log.d(TAG, "★ API応答 レスポンスJSON: $responseJson")

                mainHandler.post {
                    if (responseJson != null) {
                        try {
                            val gson = Gson()
                            val result = gson.fromJson(responseJson, FaceAuthResponse::class.java)
                            
                            // ステータス判定
                            // Status 0: 認証成功
                            // Status 2: 認証成功（情報/警告付き）
                            // Status 1: 認証失敗
                            val isSuccess = (result.status == 0 || result.status == 2)
                            
                            Log.d(TAG, "★ API結果 status=${result.status}, name=${result.name}, similarity=${result.similarity}, message=${result.message}")
                            Log.d(TAG, "★ API結果 判定: ${if (isSuccess) "成功" else "失敗"}")
                            
                            if (isSuccess) {
                                // onActivityResult用に結果を保持
                                mPendingAuthResponse = result
                                // Maker Appに終了通知
                                broadcastAuthResult(true, "認証成功", responseJson)
                            } else {
                                // 認証失敗 → Maker App側でリトライ
                                val msg = result.message ?: "認証失敗 (status=${result.status})"
                                broadcastAuthResult(false, msg, responseJson)
                            }
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "JSON解析エラー: $responseJson", e)
                            broadcastAuthResult(false, "サーバー無効応答")
                        }
                    } else {
                        Log.e(TAG, "★ API応答 失敗: レスポンスが空(null)です。NW環境 hoặc Server link を確認してください。")
                        broadcastAuthResult(false, "ネットワークエラー")
                    }

                    }

                    // 4. 一時圧縮ファイルの削除
                    try {
                        // 圧縮ファイルがあれば削除
                        if (compressedFile.absolutePath != file.absolutePath && compressedFile.exists()) {
                            compressedFile.delete()
                        }
                        
                        // ★ Logic xóa ảnh gốc sau khi dùng xong (Hiên tại Disable để Debug)
                        // Original file deleted to save storage (Commented out for debugging)
                        // if (file.exists()) {
                        //      val deleted = file.delete()
                        //      if (deleted) Log.d(TAG, "★ Clean up: Deleted original face image: ${file.name}")
                        //      else Log.w(TAG, "★ Clean up: Failed to delete original face image: ${file.name}")
                        // }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
            } catch (e: Exception) {
                Log.e(TAG, "★ API通信例外が発生しました: ${e.message}", e)
                mainHandler.post { broadcastAuthResult(false, "NW Error: ${e.message}") }
            }
        }.start()
    }

    /**
     * onActivityResult: Broadcast経由の結果を優先チェック
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: req=$requestCode, res=$resultCode")
        
        // Broadcast経由の保留結果を優先チェック（API成功時はここで処理される）
        // ※ MakerAppはfinish()でRESULT_CANCELEDを返すため、この判定を先に行う必要がある
        if (mPendingAuthResponse != null) {
             Log.d(TAG, "★ Broadcast経由の認証結果あり → 結果画面へ遷移")
             handleAuthResult(mPendingAuthResponse!!)
             mPendingAuthResponse = null
             return
        }

        // ユーザーがカメラ画面でバックボタンを押した場合（API結果なし）
        // → エラー画面ではなく、トップ画面に直接戻る
        if (resultCode == RESULT_CANCELED) {
            Log.d(TAG, "★ ユーザーがバックボタンを押しました → トップ画面に戻ります (Time: ${System.currentTimeMillis()})")
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        val handled =
                captureStrategy.onActivityResult(this, requestCode, resultCode, data) { file, error ->
                     if (file != null) {
                         processFaceImage(file)
                     } else {
                         val msg = error ?: "Cancelled"
                         if (error != null) showErrorAndFinish(msg)
                         else finish()
                     }
                }

        if (!handled) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * 画像ファイルを圧縮・回転処理します。
     */
    private fun compressImageIfNeeded(file: File): File {
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var inSampleSize = 1
            val reqDim = 1024
            if (options.outHeight > reqDim || options.outWidth > reqDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqDim && (halfWidth / inSampleSize) >= reqDim) {
                    inSampleSize *= 2
                }
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize
            var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return file

            var width = bitmap.width
            var height = bitmap.height
            if (width > reqDim || height > reqDim) {
                val ratio = width.toFloat() / height.toFloat()
                if (ratio > 1) {
                    width = reqDim
                    height = (width / ratio).toInt()
                } else {
                    height = reqDim
                    width = (height * ratio).toInt()
                }
                val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                try {
                    val rotatedBitmap = Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                    )
                    if (rotatedBitmap != bitmap) {
                        bitmap.recycle()
                        bitmap = rotatedBitmap
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "回転処理に失敗しました", e)
                }
            }

            val newFile = File(externalCacheDir, "compressed_${file.name}")
            val outputStream = FileOutputStream(newFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            return newFile
        } catch (e: Exception) {
            Log.e(TAG, "圧縮処理に失敗しました", e)
            return file
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "圧縮処理中にメモリ不足が発生", e)
            return file
        }
    }

    /**
     * 認証成功結果をTopActivityに返します。
     */
    private fun handleAuthResult(result: FaceAuthResponse) {
        val data = Intent().apply {
            putExtra("ResultName", result.name)
            putExtra("ResultID", result.realId)
            putExtra("ResultStatus", result.status)
            putExtra("ResultMessage", result.message)
            putExtra("ResultSimilarity", result.similarity)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    /**
     * エラー結果をTopActivityに返します。
     */
    private fun showErrorAndFinish(message: String) {
        val data = Intent().apply {
            putExtra("ResultStatus", -1)
            putExtra("ResultMessage", message)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(faceReceiver)
            Log.d(TAG, "★ [終了] BroadcastReceiver登録解除完了")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }
}
