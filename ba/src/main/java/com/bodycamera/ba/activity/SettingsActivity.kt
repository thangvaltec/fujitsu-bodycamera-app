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

    // Layout and Preference keys
    private lateinit var etServerUrl: EditText
    private lateinit var etDeviceId: EditText
    private lateinit var cbUseTopK: android.widget.CheckBox // Added
    private lateinit var btnSave: Button
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "BodyCameraPrefs"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_USE_TOPK = "use_topk" // Added
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        etServerUrl = findViewById(R.id.etServerUrl)
        etDeviceId = findViewById(R.id.etDeviceId)
        cbUseTopK = findViewById(R.id.cbUseTopK) // Added
        btnSave = findViewById(R.id.btnSave)

        loadSettings()

        btnSave.setOnClickListener { saveSettings() }
    }

    private fun loadSettings() {
        val url = prefs.getString(KEY_SERVER_URL, "")
        val deviceId = prefs.getString(KEY_DEVICE_ID, "")
        val useTopK = prefs.getBoolean(KEY_USE_TOPK, false) // Default false

        etServerUrl.setText(url)
        etDeviceId.setText(deviceId)
        cbUseTopK.isChecked = useTopK // Added
    }

    private fun saveSettings() {
        val url = etServerUrl.text.toString().trim()
        val deviceId = etDeviceId.text.toString().trim()
        val useTopK = cbUseTopK.isChecked // Added

        if (url.isEmpty()) {
            etServerUrl.error = "サーバーURLを入力してください"
            return
        }

        if (deviceId.isEmpty()) {
            etDeviceId.error = "デバイスIDを入力してください"
            return
        }

        prefs.edit().apply {
            putString(KEY_SERVER_URL, url)
            putString(KEY_DEVICE_ID, deviceId)
            putBoolean(KEY_USE_TOPK, useTopK) // Added
            apply()
        }

        Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
        finish()
    }
}
