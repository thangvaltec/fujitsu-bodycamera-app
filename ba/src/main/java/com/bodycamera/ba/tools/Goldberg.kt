package com.bodycamera.ba.tools
import android.util.Log
import java.lang.RuntimeException
import java.util.concurrent.atomic.AtomicBoolean

 interface IOnGoldBergListener{
    fun onGoldbergCallback(data: Goldberg.GoldBergData)
    fun onGoldbergListenerCreated()
    fun onGoldbergListenerDestroy()
    fun isGoldbergListenerCreated() :Boolean
}

class Goldberg(private val mThreadId:Long) {
    abstract class GoldBergData(private val mJavaClassName:String,
                                private val mIsOk:Boolean = true,
                                private var mErrno:Int = 0,
                                private var mErrMsg:String = ""){
        fun isOk() = mIsOk
        fun getErrno() = mErrno
        fun getErrMsg() = mErrMsg
        fun getJavaClassName(): String = mJavaClassName
    }

    private val mJavaClassList = mutableMapOf<String,MutableList<IOnGoldBergListener>>()

    fun checkJavaClassNameAndAdd(javaClass:String,listener: IOnGoldBergListener){
        if(mThreadId != Thread.currentThread().id) throw RuntimeException("Not run in $mThreadId thread")
        var list = mJavaClassList[javaClass]
        if(list == null){
            list = mutableListOf(listener)
            mJavaClassList[javaClass] = list
        } else {
            if(!list.contains(listener)) list.add(listener)
        }
    }

    fun checkJavaClassNameAndRemove(javaClass:String,listener: IOnGoldBergListener):Boolean{
        if(mThreadId != Thread.currentThread().id) throw RuntimeException("Not run in $mThreadId thread")
        var list = mJavaClassList[javaClass]?:return false
        return list.remove(listener)
    }

    fun checkJavaClassNameAndRemoveList(javaClass:String) :Boolean{
        if(mThreadId != Thread.currentThread().id) throw RuntimeException("Not run in $mThreadId thread")
        return mJavaClassList.remove(javaClass) != null
    }

    fun checkListenersAndRunSend(javaClass:String,data: GoldBergData){
        if(mThreadId != Thread.currentThread().id) throw RuntimeException("Not run in $mThreadId thread")
        var list = mJavaClassList[javaClass]?:return
        list.forEach {
           if(it.isGoldbergListenerCreated()) it.onGoldbergCallback(data)
        }
    }


    companion object{
        private var mInstance:Goldberg?=null

        fun subscribe(canonicalName:String,listener: IOnGoldBergListener){
            mInstance!!.checkJavaClassNameAndAdd(canonicalName,listener)
        }

        fun unsubscribe(canonicalName:String,listener: IOnGoldBergListener){
            mInstance!!.checkJavaClassNameAndRemove(canonicalName,listener)
        }

        fun unsubscribe(canonicalName:String){
            mInstance!!.checkJavaClassNameAndRemoveList(canonicalName)
        }

        fun sendMessage(data:GoldBergData){
            val javaClassName = data.getJavaClassName()
            mInstance!!.checkListenersAndRunSend(javaClassName,data)
        }

    }
}