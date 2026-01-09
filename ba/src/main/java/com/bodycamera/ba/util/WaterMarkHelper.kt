package com.bodycamera.ba.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.util.Log
import android.util.TypedValue
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

data class WaterMarkText(
    val mText:String,
    val mFontSize:Int,
    val mFontColor:String
)

object WaterMarkHelper {

    const val VAR_POLICE_ID = "%police_id%"
    const val VAR_DEVICE_ID = "%user_id%"
    const val VAR_DATE_FORMAT = "%date_ymd_hms%"
    const val VAR_LOCATION = "%gprs%"
    const val VAR_CUSTOM_TEXT = ""
    const val VAR_DELIMITER = "%RN%"


    const val WATER_MARK= "%user_id%%RN%%police_id%%RN%%date_ymd_hms%%RN%%gprs%"
    const val DEVICE_ID = "1000088"
    const val POLICE_ID = "00000"
    const val LOCATION_TEXT = "0.00N 0.00S"
    const val FONT_SIZE = 48
    const val POSITION = "TOP_LEFT"
    const val FONT_COLOR = "#ffffff"



    fun getWaterMarkText(currentTime:Long,outputWidth:Int): WaterMarkText {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val dateString = formatter.format(Date(currentTime))
        val width = outputWidth
        var fontSize = FONT_SIZE
        var fontColor = FONT_COLOR

        fontSize = (fontSize * (width.toFloat() / 1920) + 1).toInt()

        val textMark = getWaterMarkFormat(dateString)
        return WaterMarkText(textMark,fontSize,fontColor)
    }

    fun getWaterMarkFormat(dateFormat:String):String{
       var mark= WATER_MARK
       val user_id = DEVICE_ID
       val police_id = POLICE_ID
       val gpsText = LOCATION_TEXT
       val map = mapOf(
            VAR_POLICE_ID to police_id,
            VAR_DEVICE_ID to user_id,
            VAR_DATE_FORMAT to dateFormat,
            VAR_LOCATION to gpsText
       )
        map.forEach{
            item->
                mark = mark.replace(item.key,item.value)
        }
       return mark
    }
    fun getWaterMarkLocation(bimapWidth:Int,bitmapHeight:Int,outputWidth:Int,outputHeight:Int):FloatArray{
        val width_r = min(bimapWidth.toFloat()/outputWidth.toFloat(),1f)
        val height_r = min(bitmapHeight/outputHeight.toFloat(),1f)

        //TOP_LEFT TOP_RIGHT TOP_CENTER BOTTOM_LEFT BOTTOM_RIGHT BOTTOM_CENTER
        val position = POSITION
        return when(position){
                "TOP_LEFT"->{
                    var y = (0.5f - height_r) * 2
                    var x = (width_r-0.5f) * 2
                    floatArrayOf(
                        -1f , y,
                        x, y,
                        -1f, 1f,
                        x, 1f)
                }
                "TOP_RIGHT"->{
                    val y = (0.5f - height_r) * 2
                    val x = (0.5f - width_r) * 2
                    floatArrayOf(
                        x, y,
                        1f, y,
                        x, 1f,
                        1f, 1f)
                }
                "TOP_CENTER"->{
                    val y = (0.5f - height_r) * 2
                    val x = width_r/2
                    val x1 = x * -2
                    val x2 = x * 2
                    floatArrayOf(
                        x1, y,
                        x2, y,
                        x1, 1f,
                        x2, 1f)
                }
                "BOTTOM_LEFT"->{
                    val y = (height_r - 0.5f) * 2
                    val x = (width_r-0.5f) * 2
                    floatArrayOf(
                        -1f, -1f,
                        x, -1f,
                        -1f, y,
                        x, y)
                }
                "BOTTOM_RIGHT"->{
                    val y = (height_r - 0.5f) * 2
                    val x = (0.5f - width_r) * 2
                    floatArrayOf(
                        x, -1f,
                        1f, -1f,
                        x, y,
                        1f, y)
                }
                "BOTTOM_CENTER"->{
                    val y = (height_r - 0.5f) * 2
                    val x = width_r/2
                    val x1 = x * -2
                    val x2 = x * 2
                    floatArrayOf(
                        x1, -1f,
                        x2, -1f,
                        x1, y,
                        x2, y)
                }else->{
                    floatArrayOf(
                        -1f, -1f,
                        1f, -1f,
                        -1f, 1f,
                        1f, 1f)
                    }
            }
    }
}