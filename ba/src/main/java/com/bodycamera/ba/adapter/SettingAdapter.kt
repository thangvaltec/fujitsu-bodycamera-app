package com.bodycamera.ba.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bodycamera.ba.activity.MainActivity
import com.bodycamera.tests.R


class SettingAdapter: RecyclerView.Adapter<SettingAdapter.SettingHolder>(),View.OnClickListener {

    interface IOnSettingClickListener{
        fun onSettingItemClick(item:OptionSetting)
    }

    data class OptionSetting(
        val text:String,
        var id:Int,
        var component: MainActivity.OptionComponent,
        var value:Any?
    )

    private  var mItems  = mutableListOf<OptionSetting>()
    private var mClickListener:IOnSettingClickListener?=null

    class SettingHolder(view:View): RecyclerView.ViewHolder(view) {
        var mRow:LinearLayout = view.findViewById(R.id.ll_row)
        var mLabel:TextView = view.findViewById(R.id.tv_setting_label)
    }

    fun setListener(listener: IOnSettingClickListener){
        mClickListener = listener
    }

    fun reset(list:List<OptionSetting>){
        mItems.clear()
        mItems.addAll(list)
        notifyDataSetChanged()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingHolder {
        val view =  LayoutInflater.from(parent.context).inflate(R.layout.rv_item_text,null,false)
        return SettingHolder(view)
    }

    override fun getItemViewType(position: Int):Int {
        return 1
    }

    override fun onBindViewHolder(holder: SettingHolder, position: Int) {
        val setting = mItems[position]
        holder.mLabel.text = "${setting.text}"
        holder.mRow.tag = position
        holder.mRow.setOnClickListener(this)
    }

    override fun getItemCount(): Int = mItems.size

    override fun onClick(view: View?) {
        val position = view?.tag!! as Int
        val deviceInfo = mItems[position]
        if(mClickListener!=null) mClickListener?.onSettingItemClick(deviceInfo)
    }

    companion object{
        const val TAG ="SettingAdapter"
    }

}