package com.bodycamera.ba.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bodycamera.ba.data.Constants
import com.bodycamera.ba.data.KeyAction
import com.bodycamera.ba.tools.KingStone


class DeviceKeyReceiver: BroadcastReceiver() {
    interface IOnKeyActionListener{
        fun onKeyAction(keyName:String,keyAction: KeyAction)
    }
    private var currentAction = ""
    private var mListener:IOnKeyActionListener?=null

    fun create(context: Context,listener: IOnKeyActionListener){
        KingStone.checkRunInApiThread()
        val filter = IntentFilter()
        filter.addAction(Constants.ACTION_PTTKEY_DOWN)
        filter.addAction(Constants.ACTION_PTTKEY_UP)

        filter.addAction(Constants.ACTION_VIDEO_DOWN)
        filter.addAction(Constants.ACTION_CAMERA_DOWN)
        filter.addAction(Constants.ACTION_RECORD_DOWN)
        filter.addAction(Constants.ACTION_RECORD_LONG_PRESS)
        filter.addAction(Constants.ACTION_PTTKEY_LONG_PRESS)

        filter.addAction(Constants.ACTION_LASER_DOWN)
        filter.addAction(Constants.ACTION_LASER_UP)
        filter.addAction(Constants.ACTION_RECORD_UP)
        filter.addAction(Constants.ACTION_LASER_LONGPRESS)
        filter.addAction(Constants.ACTION_SOS_LONG_PRESS)
        filter.addAction(Constants.ACTION_SOS_DOWN)
        filter.addAction(Constants.ACTION_SOS_UP)
        filter.addAction(Constants.ACTION_FILE_IMP)
        context.registerReceiver(
            this,
            filter
        )
        mListener = listener
    }

    fun release(context: Context){
        KingStone.checkRunInApiThread()
        context.unregisterReceiver(this)
        mListener = null
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val nowTimeUs = System.currentTimeMillis()
        currentAction = intent?.action ?: ""
        val enableAction = true
        defaultActionHandler(context,enableAction)
    }



    private fun defaultActionHandler(context: Context,enableAction:Boolean){
        when(currentAction){
            Constants.ACTION_CAMERA_DOWN->{
                Log.d("9999", "onReceive: ACTION_CAMERA_DOWN enableAction=$enableAction")
                if(!enableAction)return
                mListener?.onKeyAction(currentAction,KeyAction.DOWN)
            }

            Constants.ACTION_RECORD_DOWN->{
                Log.d("9999", "onReceive: ACTION_RECORD_DOWN enableAction=${!enableAction}")
                if(!enableAction)return
                mListener?.onKeyAction(currentAction,KeyAction.DOWN)
            }

            Constants.ACTION_VIDEO_DOWN->{
                Log.d("9999", "onReceive: ACTION_VIDEO_DOWN enableAction=$enableAction")
                if(!enableAction)return
                mListener?.onKeyAction(currentAction,KeyAction.DOWN)
            }
            Constants.ACTION_LASER_DOWN->{
                Log.d("9999", "onReceive: ACTION_LASER_DOWN")
                mListener?.onKeyAction(currentAction,KeyAction.DOWN)
            }
            Constants.ACTION_LASER_LONGPRESS->{
                Log.d("9999", "onReceive: ACTION_LASER_LONGPRESS")
                mListener?.onKeyAction(currentAction,KeyAction.LONG_CLICK)
            }
            Constants.ACTION_LASER_UP->{
                Log.d("9999", "onReceive: ACTION_LASER_UP 2 enableAction=$enableAction")
                if(!enableAction)return
                mListener?.onKeyAction(currentAction,KeyAction.UP)
            }

            Constants.ACTION_SOS_DOWN->{
                Log.d("9999", "onReceive: ACTION_SOS_DOWN enableAction=$enableAction")
                if(!enableAction)return
                mListener?.onKeyAction(currentAction,KeyAction.DOWN)
            }
            Constants.ACTION_SOS_LONG_PRESS->{
                Log.d("9999", "onReceive: ACTION_SOS_LONG_PRESS enableAction=$enableAction")
                if(!enableAction)return
                mListener?.onKeyAction(currentAction,KeyAction.LONG_CLICK)
            }
            Constants.ACTION_PTTKEY_UP->
            {
                mListener?.onKeyAction(currentAction,KeyAction.UP)
            }
            Constants.ACTION_PTTKEY_DOWN->{
                mListener?.onKeyAction(currentAction,KeyAction.DOWN)
            }
        }
    }




}