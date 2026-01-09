package com.bodycamera.ba.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.bodycamera.ba.data.Constants
import com.bodycamera.ba.data.KeyAction
import com.bodycamera.ba.tools.KingStone


class BodyCameraReceiver: BroadcastReceiver() {
    companion object{
        const val TAG = "BodyCameraReceiver"
    }
    override fun onReceive(context: Context, intent: Intent?) {
        Log.i(TAG, "onReceive:${intent?.action},${intent?.data}}")
    }

}