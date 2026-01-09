package com.bodycamera.ba.util


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionHelper(private val caller: ActivityResultCaller) {

    private var permissionCallback: ((granted: Boolean, deniedPermissions: List<String>) -> Unit)? = null

    private val permissionLauncher = caller.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filter { !it.value }.map { it.key }
        permissionCallback?.invoke(denied.isEmpty(), denied)
    }


    fun checkAndRequestPermissions(
        context: Context,
        permissions: Array<String>,
        callback: (granted: Boolean, deniedPermissions: List<String>) -> Unit
    ) {
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted.isEmpty()) {
            callback(true, emptyList())
        } else {
            permissionCallback = callback
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    companion object {
        fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
            return permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
        // 判断是否有悬浮窗权限
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else true
        }

        // 判断是否有系统设置写权限
        fun hasWriteSettingsPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.System.canWrite(context)
            } else true
        }

        // 判断是否有外部存储管理权限（针对Android 11+）
        fun hasManageAllFilesPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true
        }

        // 跳转到悬浮窗权限界面
        fun gotoOverlayPermission(context: Context) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        }

        // 跳转到系统设置修改权限界面
        fun gotoWriteSettingsPermission(context: Context) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            context.startActivity(intent)
        }

        // 跳转到所有文件访问管理界面
        fun gotoManageAllFilesPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:${context.packageName}")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    context.startActivity(intent)
                }
            }
        }

        // 跳转到忽略电池优化界面
        fun gotoIgnoreBatteryOptimizations(context: Context) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        }

        // 跳转到未知来源安装权限界面
        fun gotoInstallUnknownAppPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            }
        }
    }

}