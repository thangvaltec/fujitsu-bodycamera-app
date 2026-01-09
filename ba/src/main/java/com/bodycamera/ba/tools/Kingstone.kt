package com.bodycamera.ba.tools

import android.content.Context
import com.bodycamera.ba.activity.MyApplication
import com.bodycamera.ba.data.IOnAndroidDevice
import com.bodycamera.ba.data.IOnDeviceFeatureListener
import java.lang.RuntimeException

class KingStone {
    companion object{
        const val TAG = "KingStone"
        //a complex project need a thread to make api job thread-safe and async running
        private var kApiThread: SThread?=null
        private var kInstance: KingStone?=null
        private var kGoldberg:Goldberg?=null
        private var kDeviceFeature:IOnAndroidDevice?=null
        private var kContext:Context?=null
        private var kDeviceFeatureListener:OnDeviceFeatureListener?=null
        private var kSurfaceWrapper :SurfaceWrapper?=null

        fun getDeviceFeature() = kDeviceFeature
        fun getContext() = kContext?:throw RuntimeException("invoke mustInitFirst() in Application first")

        fun registerDeviceFeatureListener(listener: IOnDeviceFeatureListener){
            kDeviceFeatureListener?.registerCallback(listener)
        }

        fun unregisterDeviceFeatureListener(listener: IOnDeviceFeatureListener){
            kDeviceFeatureListener?.unregisterCallback(listener)
        }
        fun getDeviceFeatureListener() = kDeviceFeatureListener

        fun getSurfaceWrapper() = kSurfaceWrapper

        fun mustInitFirst(context: Context){
            kContext = context
            if(kApiThread == null){
                kApiThread = SThread()
                kApiThread?.start()
            }
            runBlock {
                if(kInstance == null){
                    kInstance = KingStone()
                }
                if(kSurfaceWrapper == null){
                    kSurfaceWrapper = SurfaceWrapper()
                    kSurfaceWrapper?.create()
                }
                if(kGoldberg == null){
                    kGoldberg = Goldberg(Thread.currentThread().id)
                }
                if(kDeviceFeature == null){
                    kDeviceFeature = OnM530DeviceImpl(context)
                    kDeviceFeature?.create()
                }
                if(kDeviceFeatureListener == null){
                    kDeviceFeatureListener = OnDeviceFeatureListener()
                }
            }
        }

        fun isRunInApiThread():Boolean{
            return kApiThread?.getThreadId() == Thread.currentThread().id
        }

        fun checkRunInApiThread(){
            if(!isRunInApiThread()) throw RuntimeException("it should run in ApiThread")
        }

        fun runBlock(method:()->Unit){
            kApiThread?.invoke (method)
        }

        fun runAsync(method:()->Unit){
            kApiThread?.invokeAsync (method)
        }

        fun runBoolean(method: () -> Boolean){
            kApiThread?.invokeBoolean (method)
        }

        fun exit(){
            if(kContext != null){
                val app = kContext as MyApplication
                app.exit()
            }
        }

    }

}