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
    
    // Auto Auth Group (Grid Layout)
    private lateinit var rbAutoNone: android.widget.RadioButton
    private lateinit var rbAutoFace: android.widget.RadioButton
    private lateinit var rbAutoVein: android.widget.RadioButton
    private lateinit var rbAutoBoth: android.widget.RadioButton
    
    // Auth Method Setting
    private lateinit var rgFaceAuthMethod: android.widget.RadioGroup
    private lateinit var rgFaceVeinAuthMethod: android.widget.RadioGroup
    
    // Top-K & Threshold
    private lateinit var etTopK: EditText
    private lateinit var etRecognitionThreshold: EditText

    private lateinit var btnSave: Button
    private lateinit var prefs: SharedPreferences

    companion object {
        const val PREFS_NAME = "BodyCameraPrefs"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_LIVENESS_THRESHOLD = "liveness_threshold"//なりまし防止スコア
        const val KEY_IDENT_DISTANCE = "ident_distance" // 距離設定 (0, 1, 2, 3 corresponding to 0.5m, 1m, 1.5m, 2m)
        
        const val KEY_AUTO_AUTH_METHOD = "auto_auth_method" // none, face, vein, both
        const val KEY_FACE_AUTH_METHOD = "face_auth_method" // cloud, local
        const val KEY_FACE_VEIN_AUTH_METHOD = "face_vein_auth_method" // cloud, local
        const val KEY_TOP_K = "top_k_count"
        const val KEY_RECOGNITION_THRESHOLD = "recognition_threshold"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        etServerUrl = findViewById(R.id.etServerUrl)
        etDeviceId = findViewById(R.id.etDeviceId)
        etLivenessThreshold = findViewById(R.id.etLivenessThreshold)
        rgIdentDistance = findViewById(R.id.rgIdentDistance)
        
        rbAutoNone = findViewById(R.id.rbAutoNone)
        rbAutoFace = findViewById(R.id.rbAutoFace)
        rbAutoVein = findViewById(R.id.rbAutoVein)
        rbAutoBoth = findViewById(R.id.rbAutoBoth)
        
        rgFaceAuthMethod = findViewById(R.id.rgFaceAuthMethod)
        rgFaceVeinAuthMethod = findViewById(R.id.rgFaceVeinAuthMethod)
        
        etTopK = findViewById(R.id.etTopK)
        etRecognitionThreshold = findViewById(R.id.etRecognitionThreshold)

        btnSave = findViewById(R.id.btnSave)

        // Setup Mutual Exclusivity for Grid RadioButtons
        setupAutoAuthRadioLogic()

        loadSettings()

        btnSave.setOnClickListener { saveSettings() }
    }

    private fun setupAutoAuthRadioLogic() {
        rbAutoNone.setOnCheckedChangeListener { _, isChecked -> if (isChecked) { rbAutoFace.isChecked = false; rbAutoVein.isChecked = false; rbAutoBoth.isChecked = false } }
        rbAutoFace.setOnCheckedChangeListener { _, isChecked -> if (isChecked) { rbAutoNone.isChecked = false; rbAutoVein.isChecked = false; rbAutoBoth.isChecked = false } }
        rbAutoVein.setOnCheckedChangeListener { _, isChecked -> if (isChecked) { rbAutoNone.isChecked = false; rbAutoFace.isChecked = false; rbAutoBoth.isChecked = false } }
        rbAutoBoth.setOnCheckedChangeListener { _, isChecked -> if (isChecked) { rbAutoNone.isChecked = false; rbAutoFace.isChecked = false; rbAutoVein.isChecked = false } }
    }

    private fun loadSettings() {
        // Load basic text fields
        etServerUrl.setText(prefs.getString(KEY_SERVER_URL, ""))
        etDeviceId.setText(prefs.getString(KEY_DEVICE_ID, ""))
        etLivenessThreshold.setText(prefs.getFloat(KEY_LIVENESS_THRESHOLD, 88.0f).toString())
        etTopK.setText(prefs.getInt(KEY_TOP_K, 1).toString())
        etRecognitionThreshold.setText(prefs.getFloat(KEY_RECOGNITION_THRESHOLD, 60.0f).toString())
        
        // Load Ident Distance
        when (prefs.getInt(KEY_IDENT_DISTANCE, 1)) {
            0 -> findViewById<android.widget.RadioButton>(R.id.rbDist05).isChecked = true
            1 -> findViewById<android.widget.RadioButton>(R.id.rbDist10).isChecked = true
            2 -> findViewById<android.widget.RadioButton>(R.id.rbDist15).isChecked = true
            3 -> findViewById<android.widget.RadioButton>(R.id.rbDist20).isChecked = true
        }
        
        // Load Auto Auth Setting
        when (prefs.getString(KEY_AUTO_AUTH_METHOD, "none")) {
            "none" -> rbAutoNone.isChecked = true
            "face" -> rbAutoFace.isChecked = true
            "vein" -> rbAutoVein.isChecked = true
            "both" -> rbAutoBoth.isChecked = true
            else -> rbAutoNone.isChecked = true
        }

        // Load Face Auth Method
        if (prefs.getString(KEY_FACE_AUTH_METHOD, "cloud") == "local") {
            findViewById<android.widget.RadioButton>(R.id.rbFaceLocal).isChecked = true
        } else {
            findViewById<android.widget.RadioButton>(R.id.rbFaceCloud).isChecked = true
        }

        // Load Face+Vein Auth Method
        if (prefs.getString(KEY_FACE_VEIN_AUTH_METHOD, "cloud") == "local") {
            findViewById<android.widget.RadioButton>(R.id.rbFaceVeinLocal).isChecked = true
        } else {
            findViewById<android.widget.RadioButton>(R.id.rbFaceVeinCloud).isChecked = true
        }
    }

    private fun saveSettings() {
        val url = etServerUrl.text.toString().trim()
        val deviceId = etDeviceId.text.toString().trim()
        
        // Validation
        if (url.isEmpty()) {
            etServerUrl.error = "サーバーURLを入力してください"
            return
        }
        if (deviceId.isEmpty()) {
            etDeviceId.error = "デバイスIDを入力してください"
            return
        }

        val livenessVal = etLivenessThreshold.text.toString().toFloatOrNull() ?: 88.0f
        val topKVal = etTopK.text.toString().toIntOrNull() ?: 1
        val recogThresholdVal = etRecognitionThreshold.text.toString().toFloatOrNull() ?: 60.0f
        
        val identDistIndex = when (rgIdentDistance.checkedRadioButtonId) {
            R.id.rbDist05 -> 0
            R.id.rbDist10 -> 1
            R.id.rbDist15 -> 2
            R.id.rbDist20 -> 3
            else -> 1
        }
        
        val autoAuthMethod = when {
            rbAutoFace.isChecked -> "face"
            rbAutoVein.isChecked -> "vein"
            rbAutoBoth.isChecked -> "both"
            else -> "none"
        }
        
        val faceAuthMethod = if (rgFaceAuthMethod.checkedRadioButtonId == R.id.rbFaceLocal) "local" else "cloud"
        val faceVeinAuthMethod = if (rgFaceVeinAuthMethod.checkedRadioButtonId == R.id.rbFaceVeinLocal) "local" else "cloud"

        prefs.edit().apply {
            putString(KEY_SERVER_URL, url)
            putString(KEY_DEVICE_ID, deviceId)
            putFloat(KEY_LIVENESS_THRESHOLD, livenessVal)
            putInt(KEY_IDENT_DISTANCE, identDistIndex)
            putString(KEY_AUTO_AUTH_METHOD, autoAuthMethod)
            putString(KEY_FACE_AUTH_METHOD, faceAuthMethod)
            putString(KEY_FACE_VEIN_AUTH_METHOD, faceVeinAuthMethod)
            putInt(KEY_TOP_K, topKVal)
            putFloat(KEY_RECOGNITION_THRESHOLD, recogThresholdVal)
            apply()
        }

        Toast.makeText(this, "設定を保存しました", Toast.LENGTH_SHORT).show()
        finish()
    }
}
