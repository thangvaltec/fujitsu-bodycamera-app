package com.bodycamera.ba.activity

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log

object DeviceSerialHelper {

    @SuppressLint("HardwareIds", "PrivateApi")
    fun getDeviceSerial(context: Context): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)

            val keys = listOf(
                "vendor.gsm.serial",  // BodyCamera用 ベンダー独自シリアル
                "ro.serialno",
                "ro.boot.serialno",
                "persist.sys.serialno",
                "gsm.serial",
                "ril.serialnumber"
            )

            for (key in keys) {
                var value = get.invoke(null, key) as String
                if (!value.isNullOrEmpty() && value != "unknown") {
                    value = value.trim()
                    if (value.contains(" ")) {
                        value = value.substringBefore(" ")
                    }
                    Log.d("SERIAL_TEST", "Found serial from $key = $value")
                    return value
                }
            }

            // ここまで来たらシリアルが取れない → ANDROID_ID にフォールバック
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            )
            Log.d("SERIAL_TEST", "Fallback ANDROID_ID = $androidId")

            androidId ?: "UNKNOWN"

        } catch (e: Exception) {
            Log.e("SERIAL_TEST", "Error getDeviceSerial: $e")
            "UNKNOWN"
        }
    }
}
