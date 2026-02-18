package com.bodycamera.ba.faceauth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import java.io.File
import com.bodycamera.ba.activity.SettingsActivity

/**
 * Maker FacePass SDKを使用して顔画像をキャプチャする戦略クラス。
 * Smart Retryパターン対応: api_result_json（検証済みJSON）を優先し、
 * フォールバックとしてimage_path（レガシー）を使用します。
 */
class MakerAppCaptureStrategy : FaceCaptureStrategy {

    companion object {
        private const val TAG = "MakerCapture"
        private const val REQUEST_MAKER_CAPTURE = 7777
    }

    override fun launchCapture(activity: AppCompatActivity) {
        // 外部Makerアプリ Activity を起動
        val intent = Intent()
        intent.setClassName("mcv.testfacepass", "mcv.testfacepass.FacePassActivity")
        
        // Smart Retry用パラメータ設定
        val prefs = activity.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val serverUrl = prefs.getString(SettingsActivity.KEY_SERVER_URL, "")
        val deviceId = prefs.getString(SettingsActivity.KEY_DEVICE_ID, "")
        
        intent.putExtra("server_url", serverUrl)
        intent.putExtra("device_id", deviceId)
        intent.putExtra("police_id", "null")
        
        activity.startActivityForResult(intent, REQUEST_MAKER_CAPTURE)
    }

    override fun onActivityResult(
            context: Context,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
            callback: (File?, String?) -> Unit
    ): Boolean {
        if (requestCode == REQUEST_MAKER_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {

                // ========================================
                // Smart Retryフロー: api_result_json を優先チェック
                // FacePassActivity内でAPI検証済みの場合、JSONが直接返される
                // ========================================
                val apiResultJson = data.getStringExtra("api_result_json")
                if (apiResultJson != null) {
                    Log.d(TAG, "★ Smart Retry結果受信 (API検証済み)")
                    // api_result_jsonが存在する場合、ファイルは不要
                    // callbackにnullを返すが、呼び出し元でapi_result_jsonを直接読み取る
                    callback(null, null)
                    return true
                }

                // ========================================
                // レガシーフロー: URI または image_path から画像取得
                // ========================================

                // 1. URIからコンテンツを取得 (Modern Android 11+ Secure Way)
                val uri = data.data
                if (uri != null) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val tempFile = File(context.externalCacheDir, "capture_sync_${System.currentTimeMillis()}.jpg")
                            val outputStream = tempFile.outputStream()
                            inputStream.use { input ->
                                outputStream.use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Log.d(TAG, "Copied URI content to: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                            callback(tempFile, null)
                            return true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resolve URI: $uri", e)
                    }
                }

                // 2. image_pathからファイル取得 (Legacy Way)
                val imagePath = data.getStringExtra("image_path")
                if (!imagePath.isNullOrEmpty()) {
                    val file = File(imagePath)
                    if (file.exists() && file.length() > 0) {
                        callback(file, null)
                    } else {
                        callback(null, "Image file not found or empty: $imagePath")
                    }
                } else {
                    callback(null, "No image path or URI returned")
                }
            } else {
                callback(null, "Capture canceled or failed")
            }
            return true
        }
        return false
    }
}
