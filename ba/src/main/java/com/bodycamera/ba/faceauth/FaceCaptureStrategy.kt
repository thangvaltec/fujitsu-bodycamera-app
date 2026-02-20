package com.bodycamera.ba.faceauth

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** 顔キャプチャ戦略のインターフェース定義。 顔画像のソース（カメラ、Makerアプリ、ファイルなど）を抽象化します。 */
interface FaceCaptureStrategy {

    /**
     * Launch the capture process.
     * @param activity The host activity.
     * @param options Optional parameters for the capture strategy.
     */
    fun launchCapture(activity: AppCompatActivity, options: android.os.Bundle? = null)

    /**
     * Handle the result from onActivityResult.
     * @param requestCode request code
     * @param resultCode result code
     * @param data intent data
     * @param callback function to be called with the captured file (or null if failed)
     * @return true if the result was handled by this strategy, false otherwise.
     */
    fun onActivityResult(
            context: android.content.Context,
            requestCode: Int,
            resultCode: Int,
            data: Intent?,
            callback: (File?, String?) -> Unit
    ): Boolean
}
