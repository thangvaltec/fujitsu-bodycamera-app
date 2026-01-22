package com.bodycamera.ba.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.ba.faceauth.CameraCaptureStrategy
import com.bodycamera.ba.faceauth.FaceCaptureStrategy
import com.bodycamera.ba.faceauth.MakerAppCaptureStrategy
import com.bodycamera.ba.network.FaceRecognitionApi
import com.bodycamera.ba.network.model.FaceAuthResponse
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modular Activity for Face Authentication. Orchestrates the capture strategy and network
 * communication.
 */
class NewFaceAuthActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NewFaceAuthActivity"
    }

    // 設定可能な戦略:
    // 来週Makerアプリに切り替える際は、CameraCaptureStrategyをMakerAppCaptureStrategyに置き換えてください。
    private val captureStrategy: FaceCaptureStrategy = MakerAppCaptureStrategy()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // すぐに起動するためレイアウトは不要です。
        // または、シンプルなロード画面を表示することも可能です（デフォルトは白画面なので何か表示した方が良いかもしれません）。
        if (checkPermission()) {
            startCaptureSafe()
        } else {
            requestPermission()
        }
    }

    private fun startCaptureSafe() {
        Log.d(TAG, "キャプチャ戦略を開始します")
        try {
            captureStrategy.launchCapture(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching capture", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun checkPermission(): Boolean {
        // カメラ権限が宣言されている最新のAndroidでは、実行時にリクエストが必要です。
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: req=$requestCode, res=$resultCode, data=${data != null}")
        val handled =
                captureStrategy.onActivityResult(this, requestCode, resultCode, data) { file, error
                    ->
                    if (file != null) {
                        // Got image, send to server
                        Toast.makeText(this, "Processing: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                        processFaceImage(file)
                    } else {
                        // Failed to capture
                        val msg = error ?: "Unknown capture error"
                        Log.e(TAG, "Capture failed: $msg")
                        showErrorAndFinish(msg)
                    }
                }

        if (!handled) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun processFaceImage(file: File) {
        // ローディング表示（現在はコメントアウト中）
        // Toast.makeText(this, "認証中...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 画像の前処理 (リサイズ・回転・圧縮)
                // サーバー要件 (1MB以下, 正立) に合わせるため必須
                val compressedFile = compressImageIfNeeded(file)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                                    this@NewFaceAuthActivity,
                                    "サイズ: ${file.length()} -> ${compressedFile.length()}",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                }

                // 2. 設定値の取得 (SettingsActivityから)
                val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)

                // サーバーURL設定 (未設定時は空文字)
                val serverUrl = prefs.getString(SettingsActivity.KEY_SERVER_URL, "") ?: ""

                // デバイスID設定 (未設定時は空文字)
                val manualDeviceId = prefs.getString(SettingsActivity.KEY_DEVICE_ID, "") ?: ""

                // 設定未完了のチェック
                if (serverUrl.isEmpty() || manualDeviceId.isEmpty()) {
                    withContext(Dispatchers.Main) { showErrorAndFinish("設定画面でURLとデバイスIDを設定してください") }
                    return@launch
                }

                // 警官ID (現在は未使用のためnull)
                val policeId = "null"

                Log.d(TAG, "送信先: $serverUrl | デバイスID: $manualDeviceId")

                // 3. APIリクエスト送信
                val responseJson =
                        FaceRecognitionApi.sendFaceRecognition(
                                compressedFile, // 圧縮済みファイル
                                manualDeviceId,
                                policeId,
                                serverUrl
                        )

                Log.d(TAG, "レスポンス: $responseJson")

                withContext(Dispatchers.Main) {
                    if (responseJson != null) {
                        try {
                            val gson = Gson()
                            val result = gson.fromJson(responseJson, FaceAuthResponse::class.java)
                            handleAuthResult(result)
                        } catch (e: Exception) {
                            Log.e(TAG, "JSON解析エラー: $responseJson", e)
                            showErrorAndFinish("サーバーからのレスポンスが無効です")
                        }
                    } else {
                        showErrorAndFinish("サーバーから応答がありません")
                    }

                    // 4. 一時ファイルのクリーンアップ
                    try {
                        if (file.exists()) file.delete()
                        if (compressedFile.absolutePath != file.absolutePath &&
                                        compressedFile.exists()
                        ) {
                            compressedFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ネットワークエラー", e)
                withContext(Dispatchers.Main) { showErrorAndFinish("ネットワークエラー: ${e.message}") }
            }
        }
    }

    private fun compressImageIfNeeded(file: File): File {
        try {
            // EXIF情報を読んで回転角度を取得
            val exif = ExifInterface(file.absolutePath)
            val orientation =
                    exif.getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                    )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            // 1. サイズのみ取得
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            // 2. inSampleSizeでざっくり小さくする (メモリ節約)
            var inSampleSize = 1
            // Target 1024px
            val reqDim = 1024
            if (options.outHeight > reqDim || options.outWidth > reqDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= reqDim &&
                        (halfWidth / inSampleSize) >= reqDim) {
                    inSampleSize *= 2
                }
            }

            // 3. 読み込み
            options.inJustDecodeBounds = false
            options.inSampleSize = inSampleSize

            var bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return file

            // 4. Exact Resizeして 1024px に合わせる (Quality対策)
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

            // 5. 回転適用
            if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                try {
                    val rotatedBitmap =
                            Bitmap.createBitmap(
                                    bitmap,
                                    0,
                                    0,
                                    bitmap.width,
                                    bitmap.height,
                                    matrix,
                                    true
                            )
                    if (rotatedBitmap != bitmap) {
                        bitmap.recycle()
                        bitmap = rotatedBitmap
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Rotation failed", e)
                }
            }

            // 6. 保存 (画質100% - サイズが小さいので最高画質でOK)
            val newFile = File(externalCacheDir, "compressed_${file.name}")
            val outputStream = FileOutputStream(newFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            return newFile
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed", e)
            return file
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during compression", e)
            return file
        }
    }

    private fun handleAuthResult(result: FaceAuthResponse) {
        // 結果画面へ遷移します
        val intent =
                Intent(this, VeinResultActivity::class.java).apply {
                    putExtra(VeinResultActivity.EXTRA_NEW_STATUS, result.status)
                    putExtra(VeinResultActivity.EXTRA_NEW_MESSAGE, result.message)
                    putExtra(VeinResultActivity.EXTRA_NEW_SIMILARITY, result.similarity)
                    putExtra(VeinResultActivity.EXTRA_NEW_NAME, result.name)
                    putExtra(VeinResultActivity.EXTRA_NEW_ID, result.realId)

                    // トップ画面をクリアするためのフラグ
                    addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT) // 必要に応じて
                }
        startActivity(intent)
        finish()
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        // または、VeinResultActivityに失敗として表示します
        val intent =
                Intent(this, VeinResultActivity::class.java).apply {
                    putExtra(VeinResultActivity.EXTRA_NEW_STATUS, -1)
                    putExtra(VeinResultActivity.EXTRA_NEW_MESSAGE, message)
                }
        startActivity(intent)
        finish()
    }
}
