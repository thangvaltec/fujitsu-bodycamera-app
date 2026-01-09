package com.bodycamera.ba.tools

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bodycamera.ba.data.IOnAndroidDevice
import com.bodycamera.ba.data.TriLightColor
import com.bodycamera.ba.util.TaskHelper
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class OnM530DeviceImpl(private val context: Context): IOnAndroidDevice {
    private var mThread:SThread?=null
    private var mHasStarted = AtomicBoolean(false)
    override fun create(): Boolean {
        if(mThread == null){
            mThread = SThread()
            mThread?.start()
            mThread?.invokeAsync{
                mHasStarted.set(true)
                runRoutine()
            }
        }
        return true
    }

    private fun runRoutine(){
        if(!mHasStarted.get()) return
        if (isAlertLightRunning) {
             alertRefCount++
             if (alertRefCount == 1)
                 turnRedLightOn()
             else if (alertRefCount == 3) {
                 turnBlueLightOn()
             }
             if (alertRefCount == 4) alertRefCount = 0
        }

            if (triLightColor != TriLightColor.OFF) {
                triRefCount++
                if (triRefCount == 1)
                    when(triLightColor){
                        TriLightColor.RED -> turnTriLightColor(255,0,0)
                        TriLightColor.GREEN -> turnTriLightColor(0,255,0)
                        TriLightColor.YELLOW -> turnTriLightColor(0,0,255)
                        else ->{}
                    }

                else if (triRefCount == 3)
                    turnTriLightColor(
                        0,
                        0,
                        0
                    )
                if (triRefCount == 4) triRefCount = 0
            }
        if (isIrDetecting) {
            irRefCount++
            if (irRefCount == 3) {
                detectIRInternal()
                irRefCount = 0
            }
        }
        mThread?.invokeDelay({runRoutine()},500)
    }

    override fun release() {
        mHasStarted.set(false)
        mThread?.stop()
        mThread = null
    }

    override fun isMassStorageSet(): Boolean {
        return isUsbMassStorageEnabled()
    }

    override fun setMassageStorage(flag: Boolean): Boolean {
        if(flag) enableUsbMassStorage() else disableUsbMassStorage()
        return true
    }


    override fun setIRCut(flag:Boolean): Boolean {
       if(flag) turnIROn() else turnIROff()
        return true
    }

    override fun isIRCutSet(): Boolean {
        return isIROn
    }

    override fun setAutoIRCut(flag: Boolean): Boolean {
        setAutoIR(flag)
        return true
    }

    override fun isAutoIRCut(): Boolean {
        return isIrDetecting
    }

    override fun setTorch(flag: Boolean): Boolean {
       if(flag) turnTorchOn() else turnTorchOff()
       return true
    }

    override fun isTorchSet(): Boolean {
       return isTorchOn
    }

    override fun setLightSensor(flag: Boolean): Boolean {
        if(flag) turnLightSensorOn() else turnLightSensorOff()
        return true
    }

    override fun isLightSensorSet(): Boolean {
        return isLightSensorOn()
    }

    override fun setLaser(flag: Boolean): Boolean {
         turnLaser(flag)
        return true
    }

    override fun isLaserSet() = isLaserOn


    override fun setTriColorLight(color: TriLightColor): Boolean {
        triLightColor = color
        if(color == TriLightColor.OFF){
            TaskHelper.runDelayTask({
                turnTriLightColor(0,0,0)
            },1200)
        }
        return true
    }

    override fun isTriColorLightSet() = triLightColor

    override fun setAlarmLight(flag: Boolean): Boolean {
        if(flag) turnAlertLightsOn() else turnAlertLightsOff()
        return true
    }

    override fun isAlarmLightSet(): Boolean {
        return isAlertLightRunning
    }


    private var isAlertLightRunning = false
    private var alertRefCount = 0
    private var isLaserOn = false
    private var triRefCount = 0
    private var isIrDetecting = false
    private var irRefCount = 0
    private var triLightColor = TriLightColor.OFF
    private var isIROn = false
    private var isTorchOn = false


    fun getSystemPropertyString(context: Context, propName: String): String? {
        @SuppressLint("WrongConstant") val obj = context.getSystemService("custservice")
        var cl: Class<*>? = null
        try {
            cl = Class.forName("android.app.CustServiceManager")
            val getValueMethod = cl.getMethod(
                "readSystemPropertyString", *arrayOf<Class<*>>(
                    String::class.java
                )
            )
            val propertyString = getValueMethod.invoke(obj, *arrayOf<Any>(propName)) as String?
            return propertyString
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "readSystemPropertyString:+" + propName + " error:" + e.message)
            return ""
        }
    }



    fun writeSysFileStatusInt(
        context: Context, filename: String,
        value: Int
    ) {
        try {
            @SuppressLint("WrongConstant") val obj = context.getSystemService("custservice")
            val custServiceManager = Class
                .forName("android.app.CustServiceManager")
            val expand: Method = custServiceManager.getDeclaredMethod(
                "writeSysFileStatusInt", *arrayOf<Class<*>?>(
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
            )
            expand.isAccessible = true
            if (expand != null) {
                val ret: Any = expand.invoke(obj, *arrayOf<Any>(filename, value))

            } else {
                Log.i(
                    TAG,
                    "writeSysFileStatusInt expand==null$filename $value"
                )
            }
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "writeSysFileStatusInt error = $e")
        }
    }


    fun readSysFileStatusInt(context: Context, filename: String): Int {
        @SuppressLint("WrongConstant") val obj = context.getSystemService("custservice")
        var cl: Class<*>? = null
        return try {
            cl = Class.forName("android.app.CustServiceManager")
            val getValueMethod = cl.getMethod(
                "readSysFileStatusInt", *arrayOf<Class<*>>(
                    String::class.java
                )
            )
            val value = getValueMethod.invoke(obj, filename) as Int
            //Log.d(DeviceHelper.TAG, "readSysFileStatusInt :$filename value:$value")
            value
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "readSysFileStatusInt: $filename " , e)
            e.printStackTrace()
            -1
        }
    }



    fun setGpio(context: Context, gpionumber: Int, value: Int): Boolean {
        return try {
            @SuppressLint("WrongConstant") val obj = context.getSystemService("custservice")
            val custServiceManager = Class
                .forName("android.app.CustServiceManager")
            val expand = custServiceManager.getDeclaredMethod(
                "setGpio",
                *arrayOf<Class<*>?>(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            )
            expand.isAccessible = true
            if (expand != null) {
                val ret = expand.invoke(
                    obj,
                    *arrayOf<Any>(gpionumber, value)
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "setGpio error$e")
            false
        }
    }


    fun getGpio(context: Context, gpionumber: Int): Int {
        @SuppressLint("WrongConstant") val obj = context.getSystemService("custservice")
        return try {
            val custServiceManager = Class
                .forName("android.app.CustServiceManager")
            val expand = custServiceManager.getDeclaredMethod(
                "getGpio", *arrayOf<Class<*>?>(
                    Int::class.javaPrimitiveType
                )
            )
            expand.isAccessible = true
            if (expand != null) {
                val value = expand.invoke(obj, *arrayOf<Any>(gpionumber)) as Int
                Log.d(TAG, "getGpio $gpionumber")
                value
            } else {
                Log.e(TAG, "getGpio expand==null $gpionumber")
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "getGpio error$e")
            0
        }
    }

    fun turnIROn(){
        if(isIROn) return
        KingStone.getSurfaceWrapper()?.setCameraGrayFlag(true)
        setGpio(context,GPIO,1)
        setIRPositive()
        isIROn = true
    }


    fun turnIROff(){
        setGpio(context,GPIO,0)
        setIRNegative()
        isIROn = false
        TaskHelper.runDelayTask({
            setIRClose()
            KingStone.getSurfaceWrapper()?.setCameraGrayFlag(false)
        },200)
    }



    /**
     * open IR_CUT in positive direction
     */
    private fun setIRPositive(){
        setGpio(context, GPIO_14, 1)
        setGpio(context, GPIO_16, 0)
        setGpio(context, GPIO_179, 1)
    }

    /**
     * open IR_CUT in positive direction
     */
    private fun setIRNegative(){
        setGpio(context, GPIO_14, 0)
        setGpio(context, GPIO_16, 1)
        setGpio(context, GPIO_179, 1)
    }

    /**
     * close IR_CUT
     */
    private fun setIRClose(){
        setGpio(context, GPIO_14, 0)
        setGpio(context, GPIO_16, 0)
        setGpio(context, GPIO_179, 0)
    }

    private fun turnRedLightOn(){
        setGpio(context, GPIO_86, 1)
        setGpio(context, GPIO_88, 0)

    }

    private fun turnBlueLightOn(){
        setGpio(context, GPIO_86, 0)
        setGpio(context, GPIO_88, 1)
    }

    private fun turnRBLightsOff(){
        setGpio(context, GPIO_86, 0)
        setGpio(context, GPIO_88, 0)
    }

    private  fun turnLightSensorOn(){
        writeSysFileStatusInt(context, ALS_ENABLE, 1)
    }

    private fun turnLightSensorOff(){
        writeSysFileStatusInt(context, ALS_ENABLE, 0)
    }

    private fun isLightSensorOn():Boolean{
        return readSysFileStatusInt(context,ALS_ENABLE) == 1
    }

    override fun getLightSensorValue():Int{
        return readSysFileStatusInt(context, ALS_LUX)
    }

    fun setAutoIR(state:Boolean){
        irShieldValue = 0
        irRefCount = 0
        isIrDetecting = state
    }

    //[-3 - 3]
    private var irShieldValue = 0

    private fun detectIRInternal() {
        if(!isLightSensorOn()) turnLightSensorOn()
        val value = getLightSensorValue()
        if (value < 0) {
            Log.e(TAG,"detectIRInternal failed,can not read ALS_LUX")
            return
        }

        val MIN_SHIELD = 30
        val MAX_SHIELD = 60
        //下门限值15，上门限值30
        if (value < MIN_SHIELD) {
            irShieldValue = if (irShieldValue <= 0) {
                1
            } else {
                min(++irShieldValue, 4)
            }
        } else {//if (value > MAX_SHIELD) {
            irShieldValue = if (irShieldValue >= 0) {
                -1
            } else {
                max(--irShieldValue, -4)
            }
        }
        if (irShieldValue == 4) {
            //check if camera is opened
            if(!isIROn) {
                turnIROn()
            }
            irShieldValue = 0
        }
        if (irShieldValue == -4) {
            if(isIROn ){
                turnIROff()
            }
            irShieldValue = 0
        }
    }

    fun turnAlertLightsOn(){
        isAlertLightRunning = true
        alertRefCount = 0
    }

    fun turnAlertLightsOff(){
        isAlertLightRunning = false
        turnRBLightsOff()
    }




    fun turnTorchOn(){
        setGpio(context, LIGHT1_GPIO, 0)
        setGpio(context, LIGHT2_GPIO, 1)
        setGpio(context, SWITCH_GPIO, 1)
        isTorchOn = true
    }

    fun turnTorchOff(){
        setGpio(context, LIGHT1_GPIO, 0)
        setGpio(context, LIGHT2_GPIO, 0)
        setGpio(context, SWITCH_GPIO, 0)
        isTorchOn = false
    }


    companion object{
        const val RED_CHARGING = "/sys/class/leds/red/brightness"
        const val GREEN_CHARGING = "/sys/class/leds/green/brightness"
        const val BLUE_CHARGING = "/sys/class/leds/blue/brightness"
        const val ALS_ENABLE = "/sys/devices/platform/cg5251/als_enable" //light sensor switch
        const val ALS_LUX = "/sys/devices/platform/cg5251/als_lux" //light sensor value
        //drop detection
        const val FALL_SENSOR = "/sys/bus/platform/drivers/KX023_SENSOR/kx023sensordata"

        const val BACK_COVER = "/sys/bus/platform/drivers/CUSTDriver/k833_back_cover"

        //Backup voltage, return the backup voltage value. If it returns -1, it indicates that the backup voltage is not in place.
        const val BACK_BAT_V = "/sys/bus/platform/drivers/CUSTDriver/k833_back_bat_v"

        //Backup power, return the percentage of backup power, for example: 50 represents 50% of the power
        const val BACK_BAT_SOC = "/sys/class/power_supply/battery/back_bat_soc"

        const val GPIO: Int = 34 //IR LED GPIO

        const val EINT_FILTER_ACTION = "android.intent.action.EINT_SWITCH"

        const val  LIGHT1_GPIO = 37
        const val  LIGHT2_GPIO = 39
        const val  SWITCH_GPIO = 40

        const val MSG_LIGHT_FLASH = 100
        const val MSG_DETECT_IR   = 200

        /**
         * IR_CUT operation
         */
        const val GPIO_14 = 14
        const val GPIO_16 = 16
        const val GPIO_179 = 179

        /**
         * operations of red and blue flashing lights
         */
        const val GPIO_86 = 86 //red light
        const val GPIO_88 = 88 //blue light
        const val GPIO_152 = 152 //laser light


        const val TAG = "M530Service"
    }




    fun release(context: Context){
        if(isIROn) turnIROff()
        if(isLaserOn) turnLaser(false)
        if(isTorchOn) turnTorchOff()
    }

    private fun turnLaser(flag:Boolean){
        if(isLaserOn == flag) return
        setGpio(context, GPIO_152, if(flag)1 else 0)
        isLaserOn = flag
    }


    /**
     * red:   0- 255
     * green: 0 - 255
     * blue:  0- 255
     */
   private fun turnTriLightColor(red:Int,green:Int,blue:Int){
        writeSysFileStatusInt(context , RED_CHARGING, red) //red
        writeSysFileStatusInt(context, GREEN_CHARGING, green) //green
        writeSysFileStatusInt(context, BLUE_CHARGING, blue)
    }
    /*** end M530 */

    private fun enableUsbMassStorage(): Boolean {
        val intent = Intent("android.intent.action.USB_ENABLE_SDCARD")
        intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
        context.sendBroadcast(intent)
        return true
    }


   private fun disableUsbMassStorage(): Boolean {
        val intent = Intent("android.intent.action.USB_DISABLE_SDCARD")
        intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
        context.sendBroadcast(intent)
        return true
    }

   private fun isUsbMassStorageEnabled(): Boolean {
        var result = false
        val obj = context.getSystemService(Context.STORAGE_SERVICE)
        try {
            val cl = Class.forName("android.os.storage.StorageManager")
            val getValueMethod = cl.getMethod("isUsbMassStorageEnabled")
            result = getValueMethod.invoke(obj) as Boolean
            Log.d(TAG, "isUsbMassStorageEnabled - $result")
        } catch (e: java.lang.Exception) {
            Log.d(TAG, "isUsbMassStorageEnabled  error:" + e.message)
        }
        return result
    }

    /**
     * Battery removal message
     */
    override fun isBackCoverClosed():Boolean{
        //0-closed 1-opened
        val ret = readSysFileStatusInt(context, BACK_COVER) == 0
        return ret
    }

    override fun switchSystemLanguage(language: String, area:String): Boolean {
        //adb shell am broadcast -a com.kfree.action_ChangeSystemLanguage -n com.android.sysopt/.SysoptReceiver --es language zh --es area CN
        //adb shell am broadcast -a com.kfree.action_ChangeSystemLanguage -n com.android.sysopt/.SysoptReceiver --es language en --es area US
        val intent = Intent("com.kfree.action_ChangeSystemLanguage")
        //intent.setPackage("android")
        intent.putExtra("language", language)
        intent.putExtra("area", area)
        intent.addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
        Log.e(TAG, "changeSystemLanguage: language=$language,area=$area")
        context.sendBroadcast(intent)
        return true
    }

    /**
     * retrieve Battery Volt
     * return -1 , backup battery is not powered on
     */
    override fun getBackupBatteryVoltage():Int{
        return readSysFileStatusInt(context, BACK_BAT_V)
    }

    /**
     *Return the percentage of backup power, for example: 50 represents 50% of the power
     */
    override fun getBackupBatteryLevel():Int{
        return readSysFileStatusInt(context, BACK_BAT_SOC)
    }

    override fun returnDefaultLauncher(context: Context) {
        val intent = Intent()
        intent.component = ComponentName(
            "com.android.launcher3",
            "com.android.launcher3.uioverrides.QuickstepLauncher"
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

}