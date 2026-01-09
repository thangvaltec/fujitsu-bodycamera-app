package com.bodycamera.ba.util

import android.app.Service
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Vibrator
import android.util.Log
import com.bodycamera.tests.R

/**
 *  music + vibration
 */
object Reminder
{
    /**
     * Vibration
     * @param milliseconds vibration duration in milliseconds
     */
    fun vibrate(context: Context,milliseconds:Long = 200){
        val vibrator = context.getSystemService(Service.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(milliseconds)
    }

    /**
     * @param pattern Custom vibration mode [silent period,vibration period,silent period,vibration period...] in milliseconds
     * @param isRepeat true :repeat vibration ,false :only once
     */
    fun vibrate(context: Context,pattern:LongArray,isRepeat:Boolean = false){
        val vibrator = context.getSystemService(Service.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(pattern, if(isRepeat) 1 else -1)
    }

    /**
     * play beep sound
     * @param callback beep sound play complete,will invoke IBeepCompleteCallback.onComplete()
     */
    fun playBeep(context: Context,rawId:Int){

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var isMuted = false
        if(audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL){
            isMuted = true
        }
        if(isMuted) return
        val mediaPlay = MediaPlayer()
        mediaPlay.setAudioStreamType(AudioManager.STREAM_MUSIC)
        mediaPlay.setOnCompletionListener {
            it.stop()
            it.release()
        }
        try{
            val beepFile = context.resources.openRawResourceFd(rawId)
            mediaPlay.setDataSource(beepFile.fileDescriptor,beepFile.startOffset,beepFile.length)
            beepFile.close()
            mediaPlay.setVolume(0f,1f)
            mediaPlay.prepare()
            mediaPlay.start()
        }catch (ex:java.lang.Exception){
            Log.e(TAG, "playBeep: failed",ex )
            mediaPlay.stop()
            mediaPlay.release()
        }
    }

    /**
     * @param rawID raw mp3 file 's id
     * @param milliseconds vibration duration in milliseconds
     * @param callback callback.onComplete() when the beep sound play complete
     */
    fun playBeepAndVibrate(context: Context,rawID:Int,milliseconds: Long = 200){
        vibrate(context,milliseconds)
        playBeep(context,rawID)
    }

    /**
     * @param rawID  mp3 file store resource/raw
     */
    fun playBeepAndVibrate(context: Context,rawID:Int){
        playBeepAndVibrate(context,rawID,500L)
    }
    /**
     * play beep dingding sound
     */
    fun playBeepAndVibrate(context: Context){
        playBeepAndVibrate(context, R.raw.dingding)
    }

    const val TAG = "Reminder"

}




































