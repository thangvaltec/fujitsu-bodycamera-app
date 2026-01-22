package com.bodycamera.ba.faceauth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.ba.facepass.FacePassCameraActivity
import android.util.Log
import java.io.File

/**
 * Maker FacePass SDKを使用して顔画像をキャプチャする戦略クラス。
 * FacePassCameraActivityを起動し、結果として返されるファイルパスを取得します。
 */
class MakerAppCaptureStrategy : FaceCaptureStrategy {

    companion object {
        private const val REQUEST_MAKER_CAPTURE = 7777
    }

    override fun launchCapture(activity: AppCompatActivity) {
        // Launch external Maker App Activity
        val intent = Intent()
        intent.setClassName("mcv.testfacepass", "mcv.testfacepass.FacePassActivity")
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
                // 1. Try to get image from URI (Modern Android 11+ Secure Way)
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
                            android.util.Log.d("MakerCapture", "Copied URI content to: ${tempFile.absolutePath} (${tempFile.length()} bytes)")
                            callback(tempFile, null)
                            return true
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MakerCapture", "Failed to resolve URI: $uri", e)
                    }
                }

                // 2. Fallback to imagePath (Legacy Way)
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
