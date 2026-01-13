package com.bodycamera.ba.activity

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.tests.R

/**
 * SettingsActivity
 *
 * サーバー接続先URLやデバイスID（シリアル番号）を設定するための画面です。 ここで設定された値はSharedPreferencesに保存され、認証プロセス全体で使用されます。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var etServerUrl: EditText
    private lateinit var etDeviceId: EditText
    private lateinit var btnSave: Button
    private lateinit var prefs: SharedPreferences

    companion object {
        // 設定保存用プリファレンス名
        const val PREFS_NAME = "BodyCameraPrefs"

        // 設定キー
        const val KEY_SERVER_URL = "server_url"
        const val KEY_DEVICE_ID = "device_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // SharedPreferencesの初期化
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // UIコンポーネントの初期化
        etServerUrl = findViewById(R.id.etServerUrl)
        etDeviceId = findViewById(R.id.etDeviceId)
        btnSave = findViewById(R.id.btnSave)

        // 保存された設定を読み込んで表示
        loadSettings()

        // 保存ボタンのリスナー設定
        btnSave.setOnClickListener { saveSettings() }
    }

    /** 保存されている設定を画面に反映します。 未設定の場合は空文字を表示します（Hintが有効になります）。 */
    private fun loadSettings() {
        val url = prefs.getString(KEY_SERVER_URL, "")
        val deviceId = prefs.getString(KEY_DEVICE_ID, "")

        etServerUrl.setText(url)
        etDeviceId.setText(deviceId)
    }

    /** 入力内容を検証し、SharedPreferencesに保存します。 */
    private fun saveSettings() {
        val url = etServerUrl.text.toString().trim()
        val deviceId = etDeviceId.text.toString().trim()

        // バリデーションチェック
        if (url.isEmpty()) {
            etServerUrl.error = "サーバーURLを入力してください"
            return
        }

        if (deviceId.isEmpty()) {
            etDeviceId.error = "デバイスIDを入力してください"
            return
        }

        // 設定の保存
        prefs.edit().apply {
            putString(KEY_SERVER_URL, url)
            putString(KEY_DEVICE_ID, deviceId)
            apply()
        }

        Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
        finish()
    }
}
