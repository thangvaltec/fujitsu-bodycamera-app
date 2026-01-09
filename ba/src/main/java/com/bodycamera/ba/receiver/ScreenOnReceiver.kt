package com.bodycamera.ba.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * android.intent.action.SCREEN_ON
 */
class ScreenOnReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if(intent?.action.equals(Intent.ACTION_SCREEN_ON)){

        }
    }
}