package com.bodycamera.ba.activity

import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Process
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.bodycamera.ba.TestBodyCameraSDKActivity
import com.bodycamera.ba.adapter.SettingAdapter
import com.bodycamera.ba.data.Constants
import com.bodycamera.ba.receiver.BodyCameraReceiver
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.PermissionHelper
import com.bodycamera.ba.util.SDCardHelper
import com.bodycamera.tests.R
import com.bodycamera.tests.databinding.ActivityMainBinding
import java.util.Locale
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity(),
    SettingAdapter.IOnSettingClickListener {
    enum class OptionComponent{
        COMPONENT_SWITCH,
        COMPONENT_ACTIVITY,
        COMPONENT_SPIN,
        COMPONENT_TEXT
    }
    private var mReceiver1 : BodyCameraReceiver?=null

    private fun newBroadcast(){
        val filter = IntentFilter()
        filter.addAction("com.bodycamera.event_message")
        mReceiver1 = BodyCameraReceiver()
        registerReceiver(
            mReceiver1,
            filter
        )
    }
    private fun deleteBroadcast(){
        unregisterReceiver(mReceiver1)
        mReceiver1 = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
        newBroadcast()
    }

    override fun onDestroy() {
        super.onDestroy()
        deleteBroadcast()
    }

    private val mMenuList = mutableListOf<SettingAdapter.OptionSetting>()
    lateinit var binding:ActivityMainBinding
    private var mAdapter :SettingAdapter?=null

    fun initView(){
        val sdcardPath = SDCardHelper.getSDCardStorageRoot(this)?.absolutePath?:"unknown"
        mMenuList .addAll( mutableListOf<SettingAdapter.OptionSetting>(
            SettingAdapter.OptionSetting("Top (Face/Vein)", 200, OptionComponent.COMPONENT_ACTIVITY,null),
            SettingAdapter.OptionSetting("Aging Test", 99, OptionComponent.COMPONENT_ACTIVITY,null),
            SettingAdapter.OptionSetting("Tri Color Light", 100, OptionComponent.COMPONENT_ACTIVITY,null),
            SettingAdapter.OptionSetting("Alert light",101, OptionComponent.COMPONENT_SWITCH,false),
            SettingAdapter.OptionSetting("Drop/Back Cover/Backup battery Detection",103, OptionComponent.COMPONENT_SWITCH,false),
            SettingAdapter.OptionSetting("light detection",106, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("Ir-cut operation",107, OptionComponent.COMPONENT_ACTIVITY,null),
            SettingAdapter.OptionSetting("Camera anti-shake",108, OptionComponent.COMPONENT_SWITCH,false),
            SettingAdapter.OptionSetting("Back to system launcher",109, OptionComponent.COMPONENT_SWITCH,false),
            SettingAdapter.OptionSetting("Change system language",110, OptionComponent.COMPONENT_SWITCH,false),
            SettingAdapter.OptionSetting("Mass storage mode operation",111, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("Button operation",112, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("Laser operation",113, OptionComponent.COMPONENT_SWITCH,false),
            SettingAdapter.OptionSetting("Torch light",114, OptionComponent.COMPONENT_SWITCH,false),
            SettingAdapter.OptionSetting("NFC reader",115, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("SDCard",116, OptionComponent.COMPONENT_TEXT,sdcardPath),
            SettingAdapter.OptionSetting("Camera operation",117, OptionComponent.COMPONENT_ACTIVITY,""),
            SettingAdapter.OptionSetting("Recording",118, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("Face detection",119, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("Audio routine",120, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("G-sensor",121, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("Location",122, OptionComponent.COMPONENT_ACTIVITY,false),
            SettingAdapter.OptionSetting("BodyCamera App SDK",123, OptionComponent.COMPONENT_ACTIVITY,false)

        ))
        supportActionBar?.hide()

        mAdapter = SettingAdapter()
        var linearLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.rvList.layoutManager = linearLayoutManager
        binding.rvList.adapter = mAdapter
        mAdapter?.reset(mMenuList)
        mAdapter?.setListener(this)
        /**
        val permissionHelper = PermissionHelper(this)

        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.INTERNET,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            android.Manifest.permission.FOREGROUND_SERVICE,
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_ADMIN,
        )
         * android.permission.ACCESS_COARSE_LOCATION
         * android.permission.ACCESS_FINE_LOCATION
         * android.permission.ACCESS_WIFI_STATE
         * android.permission.ACCESS_NETWORK_STATE
         * android.permission.CHANGE_WIFI_STATE
         * android.permission.KILL_BACKGROUND_PROCESSES"
         * android.permission.WRITE_INTERNAL_STORAGE
         * android.permission.READ_INTERNAL_STORAGE
         * android.permission.MANAGE_EXTERNAL_STORAGE
         * android.permission.INTERNET
         * android.permission.VIBRATE
         *
         * android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
         *
         * android.permission.WRITE_SETTINGS
         * android.permission.FOREGROUND_SERVICE
         * android.permission.READ_MEDIA_STORAGE
         * android.permission.REQUEST_INSTALL_PACKAGES
         * android.permission.MANAGE_USB
         * com.bodycamera.permission.SERVICE1
         * android.permission.RECEIVE_BOOT_COMPLETED
         * android.permission.READ_EXTERNAL_STORAGE
         * android.permission.CAMERA
         * android.permission.RECORD_AUDIO
         * android.permission.DISABLE_KEYGUARD
         * android.permission.SYSTEM_ALERT_WINDOW
         * android.permission.MODIFY_AUDIO_SETTINGS
         * android.permission.SENSOR_INFO
         * android.permission.SENSOR_ENABLE
         * android.permission.BLUETOOTH
         * android.permission.FLASHLIGHT
         * android.permission.BLUETOOTH_ADMIN
         * android.permission.USES_POLICY_FORCE_LOCK
        permissionHelper.checkAndRequestPermissions(this, permissions) { granted, deniedList ->
            if (granted) {
                doSomething()
            } else {

                showDeniedDialog(deniedList)
            }
        } */
    }
    private fun doSomething() {}
    private fun showDeniedDialog(deniedList: List<String>) {}

    override fun onSettingItemClick(item: SettingAdapter.OptionSetting) {
        val cc = KingStone.getDeviceFeature()?:return
        when(item.id){
            200 -> {
                val intent = Intent(this, TopActivity::class.java)
                startActivity(intent)
            }
            99 -> {
                val intent = Intent(this,AgingTestActivity::class.java)
                startActivity(intent)
            }
            100 -> {
                val intent = Intent(this,TriColorActivity::class.java)
                startActivity(intent)
            }
            101 -> {
                val intent = Intent(this,AlarmLightActivity::class.java)
                startActivity(intent)
            }
            103 -> {
                //Drop Detection
                val intent = Intent(this,DropDetectionActivity::class.java)
                startActivity(intent)
            }
            106 -> {
                val intent = Intent(this,LightSensorActivity::class.java)
                startActivity(intent)
            }
            107 -> {
                val intent = Intent(this,IrCutActivity::class.java)
                startActivity(intent)
            }

            109 -> {
                cc.returnDefaultLauncher(this)
            }
            110 -> {
                val intent = Intent(this,LanguageActivity::class.java)
                startActivity(intent)
            }
            111 -> {
                val intent = Intent(this,MassStorageActivity::class.java)
                startActivity(intent)
            }
            112 -> {
                val intent = Intent(this,KeysActivity::class.java)
                startActivity(intent)
            }
            113 -> {
                val intent = Intent(this,TorchAndLaserActivity::class.java)
                startActivity(intent)
            }
            114 -> {
                val intent = Intent(this,TorchAndLaserActivity::class.java)
                startActivity(intent)
            }
            115 -> {
                val intent = Intent(this,NfcActivity::class.java)
                startActivity(intent)
            }
            116 -> {
                //sdcard path ignore
                val intent = Intent(this,SDCardActivity::class.java)
                startActivity(intent)
            }
            108,
            117 -> {
                //Camera operation
                val intent = Intent(this,CameraActivity::class.java)
                startActivity(intent)
            }
            118 -> {
                //Recording
                val intent = Intent(this,RecordingActivity::class.java)
                startActivity(intent)
            }
            119 -> {
                //Face detection ignore
            }
            120 -> {
                //Audio routine
                val intent = Intent(this,AudioRoutineActivity::class.java)
                startActivity(intent)
            }
            121 -> {
                val intent = Intent(this, GSensorActivity::class.java)
                startActivity(intent)
            }
            122 -> {
                val intent = Intent(this,LocationActivity::class.java)
                startActivity(intent)
            }
            123 -> {
                val intent = Intent(this,TestBodyCameraSDKActivity::class.java)
                startActivity(intent)
            }
        }
    }

}
