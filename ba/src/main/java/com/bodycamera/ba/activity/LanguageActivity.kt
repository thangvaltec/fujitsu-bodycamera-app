package com.bodycamera.ba.activity

import android.R
import android.os.Bundle
import android.os.Handler

import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bodycamera.ba.tools.KingStone
import com.bodycamera.tests.databinding.ActivityLanguageBinding
import java.util.Locale


class LanguageActivity : AppCompatActivity() {

    companion object{
        const val TAG = "LanguageActivity"
    }
    lateinit var binding: ActivityLanguageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private var mLanguages = mutableListOf<Locale>()
    fun initView() {
        val listView = binding.lvLanguage
        mLanguages.add(Locale("zh", "CN"))
        mLanguages.add(Locale("ja", "JP"))
        mLanguages.add(Locale("en", "US"))
        mLanguages.add(Locale("es", "ES"))
        mLanguages.add(Locale("vi", "VN"))
        mLanguages.add(Locale("en", "US"))

        val dataList = mutableListOf<String>()
        mLanguages.forEach {
            dataList.add(it.language)
        }
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dataList)
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            val locale = mLanguages[position]
            Toast.makeText(this@LanguageActivity, "[only system app can do it]choose: ${locale.language}", Toast.LENGTH_SHORT)
                .show()
            KingStone.getDeviceFeature()?.switchSystemLanguage(locale.language, locale.country)
            Thread.sleep(200)
            KingStone.exit()
        }
    }
}
