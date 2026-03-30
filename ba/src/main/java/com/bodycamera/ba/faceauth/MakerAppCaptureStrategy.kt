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
 *
 * Smart Retryパターン対応:
 * - api_result_json（SDK内で検証済みのJSON）を優先して使用します。
 * - フォールバックとして image_path（レガシー形式）を使用します。
 *
 * OOP設計方針:
 * - Intent構築ロジックを [buildFacePassIntent] に分離し、再利用性を高めています。
 * - 結果処理ロジックを [onActivityResult] 内でフロー別に明確に分岐しています。
 */
class MakerAppCaptureStrategy : FaceCaptureStrategy {

    companion object {
        private const val TAG = "MakerCapture"
        private const val REQUEST_MAKER_CAPTURE = 7777
    }

    /**
     * FacePassActivity を起動してキャプチャを開始します。
     *
     * @param activity 呼び出し元のActivity
     * @param options  追加オプション（例: should_use_topk）
     */
    override fun launchCapture(activity: AppCompatActivity, options: android.os.Bundle?) {
        val intent = buildFacePassIntent(activity, options)
        activity.startActivityForResult(intent, REQUEST_MAKER_CAPTURE)
    }

    /**
     * FacePassActivity 起動用の Intent を構築します。
     * SharedPreferences から全設定値を読み込み、各パラメータをセットします。
     *
     * @param activity 呼び出し元のActivity（SharedPreferencesアクセス用）
     * @param options  追加オプション（例: should_use_topk フラグ）
     * @return 構築済みの Intent
     */
    private fun buildFacePassIntent(activity: AppCompatActivity, options: android.os.Bundle?): Intent {
        val intent = Intent()
        intent.setClassName("mcv.testfacepass", "mcv.testfacepass.FacePassActivity")

        val prefs = activity.getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)

        // 基本接続パラメータ
        val serverUrl = prefs.getString(SettingsActivity.KEY_SERVER_URL, "")
        val deviceId = prefs.getString(SettingsActivity.KEY_DEVICE_ID, "")

        // なりすまし防止（ライブネス）設定
        val livenessThreshold = prefs.getFloat(SettingsActivity.KEY_LIVENESS_THRESHOLD, 88.0f)
        val livenessMode = prefs.getString(SettingsActivity.KEY_LIVENESS_MODE, "off")
        val livenessEnabled = (livenessMode == "on") // "on" → 写真判別あり、"off" → 写真判別なし

        // 識別距離設定（0=0.5m, 1=1m, 2=1.5m, 3=2m）
        val identDistIndex = prefs.getInt(SettingsActivity.KEY_IDENT_DISTANCE, 1)

        // Top-K人数と識別スコア閾値
        val topKCount = prefs.getInt(SettingsActivity.KEY_TOP_K, 1)
        val recogThreshold = prefs.getFloat(SettingsActivity.KEY_RECOGNITION_THRESHOLD, 60.0f)

        // 距離設定に対応する顔の最小サイズ閾値のマッピング（ピクセル単位）
        val faceMinThreshold = when (identDistIndex) {
            0 -> 150 // 0.5m
            1 -> 100 // 1m
            2 -> 60  // 1.5m
            3 -> 25  // 2m
            else -> 100
        }

        // Intent へパラメータをセット
        intent.putExtra("server_url", serverUrl)
        intent.putExtra("device_id", deviceId)
        intent.putExtra("police_id", "null")
        intent.putExtra("liveness_threshold", livenessThreshold)
        intent.putExtra("liveness_enabled", livenessEnabled)
        intent.putExtra("face_min_threshold", faceMinThreshold)
        intent.putExtra("top_k_count", topKCount)
        intent.putExtra("recognition_threshold", recogThreshold)

        // Top-Kモードのフラグを渡す（オプション指定がある場合のみ）
        if (options != null && options.containsKey("should_use_topk")) {
            val useTopK = options.getBoolean("should_use_topk")
            intent.putExtra("should_use_topk", useTopK)
        }

        return intent
    }

    /**
     * FacePassActivity からの結果を処理します。
     *
     * 以下の優先順位でデータを取得します:
     * 1. api_result_json（SDK内でAPI検証済みの場合）→ ファイル不要、そのままコールバック
     * 2. URI（Android 11以降のセキュアな方式）→ 一時ファイルにコピーしてコールバック
     * 3. image_path（レガシー形式）→ ファイルパスを直接使用
     */
    override fun onActivityResult(
            context: Context,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
            callback: (File?, String?) -> Unit
    ): Boolean {
        if (requestCode != REQUEST_MAKER_CAPTURE) return false

        if (resultCode == Activity.RESULT_OK && data != null) {

            // ① Smart Retry フロー: SDK内でAPI検証済みのJSONが直接返される場合
            val apiResultJson = data.getStringExtra("api_result_json")
            if (apiResultJson != null) {
                Log.d(TAG, "★ Smart Retry結果受信（API検証済み）: JSONを直接使用")
                // 呼び出し元が api_result_json を Intent から直接読み取るため、ファイルは不要
                callback(null, null)
                return true
            }

            // ② URI フロー: Android 11以降のセキュアなコンテンツプロバイダ経由（推奨）
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
                        Log.d(TAG, "URIコンテンツを一時ファイルへコピー完了: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                        callback(tempFile, null)
                        return true
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "URI解決エラー: $uri", e)
                }
            }

            // ③ レガシーフロー: image_path による直接ファイルパス参照
            val imagePath = data.getStringExtra("image_path")
            if (!imagePath.isNullOrEmpty()) {
                val file = File(imagePath)
                if (file.exists() && file.length() > 0) {
                    callback(file, null)
                } else {
                    callback(null, "画像ファイルが見つからないか空です: $imagePath")
                }
            } else {
                callback(null, "image_pathもURIも返されませんでした")
            }
        } else {
            // キャプチャがキャンセルまたは失敗した場合
            callback(null, "キャプチャがキャンセルまたは失敗しました")
        }
        return true
    }
}
