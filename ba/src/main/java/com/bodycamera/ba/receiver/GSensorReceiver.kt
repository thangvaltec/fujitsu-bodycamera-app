package com.bodycamera.ba.receiver

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.ba.util.TaskHelper
import kotlin.math.abs
import kotlin.math.floor

class GSensorReceiver :SensorEventListener {
    interface IOnGSensorChangedListener {
        fun onGSensorChanged(rotate:Float,speed:Float,timeMillis:Long)
    }


    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var mGravity = FloatArray(3)
    private var mGeomagnetic = FloatArray(3)
    private val R = FloatArray(9)
    private val I = FloatArray(9)
    private var currentDegree = 0f
    private var currentSpeed = 0f
    private var lastRotate = 0f
    private var lastSpeed = 0f
    private var lastUpdateTime = 0L
    private var azimuthFix = 0
    private var mListener:IOnGSensorChangedListener?=null


    fun create(context: Context,listener: IOnGSensorChangedListener) {
        KingStone.checkRunInApiThread()
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager!!.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        mListener = listener
        resume()

    }

    fun resume() {
        KingStone.checkRunInApiThread()
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManager?.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_NORMAL)

    }

    fun pause() {
        KingStone.checkRunInApiThread()
        sensorManager?.unregisterListener(this)

    }

    fun release() {
        pause()
        mListener = null
        sensorManager = null
        magnetometer = null
        accelerometer = null
        currentDegree = 0f
        currentSpeed = 0f
        lastRotate = 0f
        lastSpeed = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        val alpha = 0.96f
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            mGravity[0] = alpha * mGravity[0] + (1 - alpha) * event.values[0]
            mGravity[1] = alpha * mGravity[1] + (1 - alpha) * event.values[1]
            mGravity[2] = alpha * mGravity[2] + (1 - alpha) * event.values[2]
        }
        if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            mGeomagnetic[0] = alpha * mGeomagnetic[0] + (1 - alpha) * event.values[0]
            mGeomagnetic[1] = alpha * mGeomagnetic[1] + (1 - alpha) * event.values[1]
            mGeomagnetic[2] = alpha * mGeomagnetic[2] + (1 - alpha) * event.values[2]
        }
        val success = SensorManager.getRotationMatrix(
            R, I, mGravity, mGeomagnetic
        )

        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(R, orientation)
            currentDegree = Math.toDegrees(orientation[0].toDouble()).toFloat() // orientation
            currentDegree = (currentDegree + azimuthFix + 360) % 360

            val nowTime = System.currentTimeMillis()
            if (nowTime - lastUpdateTime > 500) {
                lastRotate = currentDegree
                lastUpdateTime = nowTime
            }

            val speed = abs(mGravity!![0]) / 2 + abs(mGravity!![1]) / 2 + abs(mGravity!![2])
            var _speed = speed - 10
            currentSpeed = floor(java.lang.Float.max(0f, _speed))
           // if (currentSpeed < 500) {
              lastSpeed = currentSpeed
           // }
            mListener?.onGSensorChanged(lastRotate,lastSpeed,nowTime)
            Log.i(TAG, "onSensorChanged: GSensorReceiver")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, i: Int) {}

    companion object {
        const val TAG = "GSensorReceiver"
    }
}