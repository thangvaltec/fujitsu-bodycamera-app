package com.bodycamera.ba.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

object TaskHelper {
    const val THREAD_COUNT = 3
    private  var mExecutorService :ExecutorService?=null
    private  var mDelayExecutorService :ScheduledExecutorService?=null
    fun runTask(runnable: Runnable) {
        if(mExecutorService == null)mExecutorService =  Executors.newCachedThreadPool()
        mExecutorService?.submit(runnable)
    }
    fun runDelayTask(runnable: Runnable,milliseconds:Long){
        if(mDelayExecutorService == null)  mDelayExecutorService = Executors.newScheduledThreadPool(1)
        mDelayExecutorService?.schedule(runnable,milliseconds,TimeUnit.MILLISECONDS)
    }
   fun shutdown(){
       mExecutorService?.shutdown()
       mExecutorService = null
       mDelayExecutorService?.shutdown()
       mDelayExecutorService = null
   }

    fun runOnUiThread(runnable: Runnable){
       val looper = Looper.getMainLooper()
        if(looper.thread === Thread.currentThread()){
            runnable.run()
        }else{
           Handler(looper).post(runnable)
        }
    }


    fun getAllApp(context: Context){
        val intent = Intent(Intent.ACTION_MAIN,null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = context.packageManager
        val appInfos = packageManager.queryIntentActivities(intent,0)
        var iterator = appInfos.iterator()
        while(iterator.hasNext()){
            val it = iterator.next()
            val pkg = it.activityInfo.packageName
            val cls = it.activityInfo.name
            Log.i("9999", "getAllApp: pkg:$pkg - cls:$cls")
        }
    }

    fun jump2QuickLauncher(context: Context){
        val intent = Intent()
        val comp =  ComponentName("com.android.launcher3", "com.android.launcher3.uioverrides.QuickstepLauncher")
        intent.component = comp
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}