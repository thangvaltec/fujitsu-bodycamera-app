package com.bodycamera.ba

import android.content.ComponentName
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.bodycamera.tests.R

class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
    }


    fun testPCM(){
        val pkg = "com.android.mot"
        val cls = "MainActivity"
        val intent = Intent()
        intent.component = ComponentName(pkg, cls)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        //1.long_press == 0 ,short press
        //2.long_press == 1 ,long press
        intent.putExtra("long_press",0)
        intent.putExtra("source","bodycamera")
    }

    fun testAAC(){

    }

    fun testH264(){

    }

    fun testH265(){

    }

    fun testMp4(){

    }

}
