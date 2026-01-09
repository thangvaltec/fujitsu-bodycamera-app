package com.bodycamera.ba.util

import android.content.Context
import android.graphics.*
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.bodycamera.ba.data.Constants
import java.io.File
import java.io.FileOutputStream
import java.lang.Exception

object ImageHelper {
    const val TAG = "ImageHelper"

    fun saveBitmap(file:File?,bitmap: Bitmap?,quality:Int = 100){
        if(bitmap == null || file == null) return
        try {
            if (!file.exists()) {
                file.parentFile.apply {
                    if (!exists()) mkdir()
                }
                file.createNewFile()
            }
            val fos = FileOutputStream(file)
            bitmap?.compress(Bitmap.CompressFormat.JPEG, quality, fos)
            fos.flush()
            fos.close()
        }catch (e:Throwable) {
            e.printStackTrace()
        }
    }

    fun getBitmapFromRes(context:Context,res:Int):Bitmap? {
        var bitmap: Bitmap? = null
        try {
            val vectorDrawable =
                ResourcesCompat.getDrawable(context.resources, res, null) ?: return null
            bitmap = Bitmap.createBitmap(
                vectorDrawable.intrinsicWidth,
                vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
            //DrawableCompat.setTint(vectorDrawable, color)
            vectorDrawable.draw(canvas)
        } catch (ex: Exception) {
            bitmap = null
            Log.e(TAG, "getBitmapFromRes: failed", ex)
        }
        return bitmap
    }



    fun saveBitmap(filepath:String?,bitmap: Bitmap?){
        saveBitmap(File(filepath),bitmap)
    }



    fun isEmptyBitmap(src: Bitmap?): Boolean {
        return src == null || src.width == 0 || src.height == 0
    }

    /**
     * Add watermark text to a Bitmap.
     *
     * @param src source image
     * @param content watermark text
     * @param textSize watermark font size, in pix.
     * @param color watermark font color.
     * @param x starting coordinate x
     * @param y starting coordinate y
     * @param recycle whether to recycle
     * @return the Bitmap with the watermark added.
     */
    fun addTextWatermark(
        src: Bitmap,
        content: String?,
        textSize: Int,
        color: Int,
        width: Int,
        height: Int,

        recycle: Boolean,
        turnGray:Boolean = false
    ): Bitmap? {
        if (isEmptyBitmap(src) || content == null) return null
        var paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val targetBitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)

        if(turnGray){
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val colorMatrixFilter = ColorMatrixColorFilter(colorMatrix)
            paint.colorFilter = colorMatrixFilter
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        paint.textSize = textSize.toFloat()
        //%RN%表示换行符
        val list = content.split(Constants.TEXT_WRAP_SYMBOL)

        val bounds = Rect()
        var nx = 30f
        var ny = height - (list.size * textSize -2f)
        list.forEach {
            value ->
            paint.getTextBounds(value, 0, value.length, bounds)
            canvas.drawText(value, nx, ny, paint)
            ny += paint.descent() - paint.ascent()
        }
        if (recycle && !src.isRecycled) src.recycle()
        return targetBitmap
    }

}