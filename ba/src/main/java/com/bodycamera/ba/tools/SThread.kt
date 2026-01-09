package com.bodycamera.ba.tools

import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.atomic.AtomicBoolean

class SThread {
    companion object{
        const val TAG = "SThread"
    }
    private var mHandler:Handler?=null
    private var mHandlerThread:HandlerThread?=null
    private var mLock = Object()
    private fun waitLock(){
        synchronized(mLock){
            mLock.wait()
        }
    }
    private fun notifyLock(){
        synchronized(mLock){
            mLock.notify()
        }
    }
    fun start(){
        synchronized(mLock) {
            if (mHandlerThread == null) {
                mHandlerThread = HandlerThread("$TAG${System.currentTimeMillis()}")
                mHandlerThread?.start()
                mHandler = Handler(mHandlerThread!!.looper)
            }
        }
    }
    fun stop(){
        synchronized(mLock){
            if(mHandlerThread!= null){
                mHandler?.removeCallbacksAndMessages(null)
                mHandlerThread?.quitSafely()
                mHandlerThread = null
                mHandler = null
            }
        }
    }

    fun invokeBoolean(method: () -> Boolean):Boolean{
        if(Thread.currentThread().id == mHandlerThread?.id){
            return method()
        }else{
            val flag = AtomicBoolean(false)
            mHandler?.post {
                flag.set(method())
                notifyLock()
            }
            waitLock()
            return flag.get()
        }
    }

    fun invoke(method:()->Unit) {
        if(Thread.currentThread().id == mHandlerThread?.id){
            method()
        }else {
            mHandler?.post {
                method()
                notifyLock()
            }
            waitLock()
        }
    }

    fun invokeFrontAsync(method: () -> Unit){
        if(Thread.currentThread().id == mHandlerThread?.id) {
            method()
        }else {
            mHandler?.postAtFrontOfQueue(method)
        }
    }

    fun invokeAsync(method: () -> Unit,timeMillis:Long = 0){
        mHandler?.postDelayed(method,timeMillis)
    }

    fun getThreadId() = mHandlerThread?.id

    fun invokeAsync(method: () -> Unit){
        if(mHandlerThread?.id == Thread.currentThread().id){
            method()
        }else {
            mHandler?.post(method)
        }
    }

    fun invokeDelay(method:()->Unit,delayMillis:Long){
        mHandler?.postDelayed(method,delayMillis)
    }
}