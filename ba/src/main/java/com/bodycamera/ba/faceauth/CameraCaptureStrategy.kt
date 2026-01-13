package com.bodycamera.ba.faceauth

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** デバイスの標準カメラを使用したキャプチャ戦略 (MediaStore版)。 Android 10+のファイル削除問題を回避します。 */
class CameraCaptureStrategy : FaceCaptureStrategy {

    companion object {
        private const val REQUEST_CAPTURE = 8888
        private const val TAG = "CameraCaptureStrategy"
    }

    private var currentPhotoUri: Uri? = null

    override fun launchCapture(activity: AppCompatActivity) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(activity.packageManager) != null) {
            val photoUri = createImageUri(activity)
            currentPhotoUri = photoUri
            if (photoUri != null) {
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                intent.addFlags(
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // Front Camera
                intent.putExtra("android.intent.extras.CAMERA_FACING", 1)
                intent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)

                activity.startActivityForResult(intent, REQUEST_CAPTURE)
            } else {
                Toast.makeText(activity, "Error creating image URI", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(activity, "No camera app found", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(
            context: Context,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
            callback: (File?, String?) -> Unit
    ): Boolean {
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == Activity.RESULT_OK) {
                val uri = currentPhotoUri
                if (uri != null) {
                    try {
                        val tempFile = createTempFile(context)
                        copyUriToFile(context, uri, tempFile)

                        if (tempFile.length() > 0) {
                            callback(tempFile, null)
                        } else {
                            callback(
                                    null,
                                    "Captured file is empty (0 bytes). Device Camera failed to write."
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback(null, "Error processing image: ${e.message}")
                    }
                } else {
                    callback(null, "Image URI lost")
                }
            } else {
                callback(null, "Capture cancelled")
            }
            return true
        }
        return false
    }

    private fun createImageUri(context: Context): Uri? {
        val resolver = context.contentResolver
        val contentValues =
                ContentValues().apply {
                    put(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            "face_${System.currentTimeMillis()}.jpg"
                    )
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_PICTURES + "/BodyCamera"
                        )
                    }
                }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    private fun createTempFile(context: Context): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = context.cacheDir
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun copyUriToFile(context: Context, srcUri: Uri, dstFile: File) {
        context.contentResolver.openInputStream(srcUri)?.use { inputStream ->
            dstFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
        }
    }
}
