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
    private lateinit var etLivenessThreshold: EditText
    private lateinit var rgIdentDistance: android.widget.RadioGroup
    private lateinit var cbUseTopK: android.widget.CheckBox // Added
    private lateinit var btnSave: Button
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "BodyCameraPrefs"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_USE_TOPK = "use_topk" // Added
        const val KEY_LIVENESS_THRESHOLD = "liveness_threshold"//なりまし防止スコア
        const val KEY_IDENT_DISTANCE = "ident_distance" // 距離設定 (0, 1, 2, 3 corresponding to 0.5m, 1m, 1.5m, 2m)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        etServerUrl = findViewById(R.id.etServerUrl)
        etDeviceId = findViewById(R.id.etDeviceId)
        etLivenessThreshold = findViewById(R.id.etLivenessThreshold)
        rgIdentDistance = findViewById(R.id.rgIdentDistance)
        cbUseTopK = findViewById(R.id.cbUseTopK) // Added
        btnSave = findViewById(R.id.btnSave)

        loadSettings()

        btnSave.setOnClickListener { saveSettings() }
    }

    private fun loadSettings() {
        val url = prefs.getString(KEY_SERVER_URL, "")
        val deviceId = prefs.getString(KEY_DEVICE_ID, "")
        val useTopK = prefs.getBoolean(KEY_USE_TOPK, false) // Default false
        val livenessThreshold = prefs.getFloat(KEY_LIVENESS_THRESHOLD, 88.0f)
        val identDistIndex = prefs.getInt(KEY_IDENT_DISTANCE, 1) // Default to 1m (index 1)

        etServerUrl.setText(url)
        etDeviceId.setText(deviceId)
        etLivenessThreshold.setText(livenessThreshold.toString())
        cbUseTopK.isChecked = useTopK // Added
        
        // Set RadioButton based on saved index
        when (identDistIndex) {
            0 -> findViewById<android.widget.RadioButton>(R.id.rbDist05).isChecked = true
            1 -> findViewById<android.widget.RadioButton>(R.id.rbDist10).isChecked = true
            2 -> findViewById<android.widget.RadioButton>(R.id.rbDist15).isChecked = true
            3 -> findViewById<android.widget.RadioButton>(R.id.rbDist20).isChecked = true
        }
    }

    private fun saveSettings() {
        val url = etServerUrl.text.toString().trim()
        val deviceId = etDeviceId.text.toString().trim()
        val useTopK = cbUseTopK.isChecked // Added
        val livenessStr = etLivenessThreshold.text.toString()
        val livenessVal = if (livenessStr.isEmpty()) 88.0f else livenessStr.toFloat()
        
        val identDistIndex = when (rgIdentDistance.checkedRadioButtonId) {
            R.id.rbDist05 -> 0
            R.id.rbDist10 -> 1
            R.id.rbDist15 -> 2
            R.id.rbDist20 -> 3
            else -> 1
        }

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
            putFloat(KEY_LIVENESS_THRESHOLD, livenessVal)
            putInt(KEY_IDENT_DISTANCE, identDistIndex)
            apply()
        }

        Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
        finish()
    }
}
