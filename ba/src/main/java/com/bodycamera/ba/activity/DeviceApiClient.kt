package com.bodycamera.ba.activity

import android.util.Log
import com.bodycamera.tests.BuildConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class DeviceApiClient {

    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.DEVICE_API_BASE_URL
    private val authPath = BuildConfig.DEVICE_API_AUTHMODE_PATH
    // 新しいログ送信APIのパス (build.gradle に DEVICE_API_AUTHLOG_PATH を定義してください)
    // 定義がない場合のデフォルト値を入れるか、BuildConfigを参照する形にします
    private val logPath = BuildConfig.DEVICE_API_AUTHLOG_PATH // BuildConfig.DEVICE_API_AUTHLOG_PATH に置き換え推奨

    /**
     * サーバーにシリアルを送り、authMode を取得する
     */
    fun getAuthMode(serial: String, callback: (Int?) -> Unit) {
        val json = JSONObject().apply { put("serialNo", serial) }

        val body = json.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(baseUrl + authPath)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("API", "Send serial failed: $e")
                callback(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = response.body?.string()
                Log.d("API", "Response: $result")

                try {
                    val jsonObj = JSONObject(result)
                    val authMode = jsonObj.getInt("authMode")
                    callback(authMode)
                } catch (e: Exception) {
                    Log.e("API", "Parse error: $e")
                    callback(null)
                }
            }
        })
    }

    /**
     * 認証ログを送信する
     * @param serialNo デバイスシリアル番号
     * @param userId ユーザーID
     * @param userName ユーザー名（任意）
     * @param authMode 認証モード (0:Face, 1:Vein, 2:Dual)
     * @param isSuccess 認証結果 (true:成功, false:失敗)
     * @param errorMessage エラーメッセージ（失敗時のみ）
     */
    fun sendAuthLog(
        serialNo: String,
        userId: String,
        userName: String?,
        authMode: Int,
        isSuccess: Boolean,
        errorMessage: String
    ) {
        try {
            val json = JSONObject().apply {
                put("serialNo", serialNo)
                put("userId", userId)
                if (!userName.isNullOrEmpty()) {
                    put("userName", userName)
                }
                put("authMode", authMode)
                put("isSuccess", isSuccess)
                put("errorMessage", errorMessage)
                // Timestamp はサーバー側で生成するため送信しない
            }

            // URL生成
            val body = json.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(baseUrl + logPath)
                .post(body)
                .build()

            // 非同期で送信（結果はログ出力のみで、UIには影響させない）
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("API", "Log upload failed: $e")
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d("API", "Log upload response: ${response.code}")
                    response.close()
                }
            })
        } catch (e: Exception) {
            Log.e("API", "Log create error: $e")
        }
    }
}