package com.bodycamera.ba.activity

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import android.os.Process
import com.bodycamera.ba.tools.KingStone
import kotlin.system.exitProcess

class MyApplication: Application() {
    private val mActivityList = mutableListOf<Activity>()
    override fun onCreate() {
        super.onCreate()
        KingStone.mustInitFirst(this)
    }

    fun exit(){
        stopActivities()
        val manager =
            getSystemService(ACTIVITY_SERVICE) as ActivityManager
        manager.killBackgroundProcesses(packageName)
        exitProcess(0)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig!!)
        Process.killProcess(Process.myPid())
    }

    private fun stopActivities(){
        val list = mActivityList.toList()
        mActivityList.clear()
        for(item in list)
            item.finish()
    }

    private  fun onActivityCreated2(activity: Activity, savedInstanceState: Bundle?)  {
        mActivityList.add(activity)
        //LanguageHelper.init(activity)
    }
    private fun onActivityDestroyed2(activity: Activity) {
        mActivityList.remove(activity)
    }

    private var callbacks: ActivityLifecycleCallbacks = object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            onActivityCreated2(activity,savedInstanceState)
        }
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            onActivityDestroyed2(activity)
        }
    }


}
