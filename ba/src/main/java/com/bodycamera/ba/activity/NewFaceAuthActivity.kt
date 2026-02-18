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
            captureStrategy.launchCapture(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching capture", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * FacePassActivityに検証デリゲートを設定します。
     * デリゲートはキャプチャ画像をAPIに送信し、結果に応じてコールバックを呼び出します。
     */
    // Delegate setup removed (Intent-based architecture)

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Log.d(TAG, "onActivityResult: req=$requestCode, res=$resultCode, data=${data != null}")

        val handled =
                captureStrategy.onActivityResult(this, requestCode, resultCode, data) { file, error ->
                    // ========================================
                    // Smart Retry対応: api_result_jsonを優先チェック
                    // ========================================
                    val apiResultJson = data?.getStringExtra("api_result_json")

                    if (apiResultJson != null) {
                        // Smart Retryフロー: API検証済みの結果
                        Log.d(TAG, "★ Smart Retry結果受信: $apiResultJson")
                        try {
                            val gson = Gson()
                            val result = gson.fromJson(apiResultJson, FaceAuthResponse::class.java)
                            handleAuthResult(result)
                        } catch (e: Exception) {
                            Log.e(TAG, "JSON解析エラー (Smart Retry結果)", e)
                            showErrorAndFinish("結果の解析に失敗しました")
                        }
                    } else if (file != null) {
                        // レガシーフロー: 画像ファイル受信 → ここでAPI呼び出し
                        Log.d(TAG, "レガシーフロー: 画像ファイル受信")
                        Toast.makeText(this, "Processing: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                        processFaceImage(file)
                    } else {
                        // キャプチャ失敗
                        val msg = error ?: "Unknown capture error"
                        Log.e(TAG, "Capture failed: $msg")
                        showErrorAndFinish(msg)
                    }
                }

        if (!handled) {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    /**
     * レガシーフロー用: 画像ファイルをAPIに送信して結果を処理します。
     * Smart Retryが有効な場合、この関数は呼ばれません。
     */
    private fun processFaceImage(file: File) {
        Thread {
            try {
                // 1. 画像の前処理 (リサイズ・回転・圧縮)
                val compressedFile = compressImageIfNeeded(file)

                mainHandler.post {
                    Toast.makeText(
                            this@NewFaceAuthActivity,
                            "サイズ: ${file.length()} -> ${compressedFile.length()}",
                            Toast.LENGTH_SHORT
                    ).show()
                }

                // 2. 設定値の取得
                val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
                val serverUrl = prefs.getString(SettingsActivity.KEY_SERVER_URL, "") ?: ""
                val manualDeviceId = prefs.getString(SettingsActivity.KEY_DEVICE_ID, "") ?: ""

                if (serverUrl.isEmpty() || manualDeviceId.isEmpty()) {
                    mainHandler.post { showErrorAndFinish("設定画面でURLとデバイスIDを設定してください") }
                    return@Thread
                }

                val policeId = "null"
                Log.d(TAG, "送信先: $serverUrl | デバイスID: $manualDeviceId")

                // 3. APIリクエスト送信
                val responseJson = FaceRecognitionApi.sendFaceRecognition(
                        compressedFile, manualDeviceId, policeId, serverUrl
                )
                Log.d(TAG, "レスポンス: $responseJson")

                mainHandler.post {
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
                        if (compressedFile.absolutePath != file.absolutePath && compressedFile.exists()) {
                            compressedFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "ネットワークエラー", e)
                mainHandler.post { showErrorAndFinish("ネットワークエラー: ${e.message}") }
            }
        }.start()
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
                    Log.e(TAG, "Rotation failed", e)
                }
            }

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
        super.onDestroy()
    }
}
