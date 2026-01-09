package com.bodycamera.ba.util

import android.content.Context
import com.bodycamera.tests.R


/**
 * Play beep sound and vibrate for 200 milliseconds
 */
object BeepHelper {
    fun alarm(context: Context){
        val rawId = 0
        Reminder.playBeepAndVibrate(context,rawId)
    }

    fun fall(context: Context){
        Reminder.playBeepAndVibrate(context, R.raw.fall)
    }

    fun play(context: Context,rawId:Int){
        Reminder.playBeepAndVibrate(context,rawId)
    }

    fun danger(context: Context){
        Reminder.playBeepAndVibrate(context,R.raw.danger)
    }

    fun success(context: Context){
        val rawId = R.raw.beep_1
        Reminder.playBeepAndVibrate(context,rawId)
    }
    fun shot(context: Context){
        val rawId = R.raw.dingding
        Reminder.playBeepAndVibrate(context,rawId)
    }


    fun failed(context: Context){
        val rawId = R.raw.danger
        Reminder.playBeepAndVibrate(context,rawId)
    }

    fun warn(context: Context){
        val rawId = R.raw.dingding
        Reminder.playBeepAndVibrate(context,rawId)
    }

    fun answer(context: Context){
        val rawId = 0
        Reminder.playBeepAndVibrate(context,rawId)
    }

    fun blacklist(context: Context){
        var rawId = R.raw.blacklist
        Reminder.playBeepAndVibrate(context,rawId)
    }

    fun whitelist(context: Context){
        var rawId = R.raw.whitelist
        Reminder.playBeepAndVibrate(context,rawId)
    }



}