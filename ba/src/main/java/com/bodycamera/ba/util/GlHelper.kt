package com.bodycamera.ba.util

import android.content.Context
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import com.bodycamera.ba.data.Constants
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max


class GlHelper {
    companion object{
         const val TAG = "GlHelper"

        /**
         * 创建opengl程序
         */
        fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            if (vertexShader == 0) {
                return 0
            }
            val pixelShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            if (pixelShader == 0) {
                return 0
            }
            var program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, pixelShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                throw RuntimeException("Could not link program: ${GLES20.glGetProgramInfoLog(program)}")
            }
            return program
        }

        /**
         * 创建图层
         */
        fun createTextures(quantity: Int, texturesId: IntArray, offset: Int) {
            GLES20.glGenTextures(quantity, texturesId, offset)
            for (i in offset until quantity) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturesId[i])
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE
                )
                GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE
                )
            }
        }

        /**
         * 创建摄像头图层
         */
        fun createExternalTextures(quantity: Int, texturesId: IntArray, offset: Int) {
            GLES20.glGenTextures(quantity, texturesId, offset)
            for (i in offset until quantity) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texturesId[i])
                GLES20.glTexParameterf(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameterf(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR.toFloat()
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE
                )
                GLES20.glTexParameteri(
                    GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE
                )
            }
        }

        /**
         * 从res/raw读取文件
         */
        fun getSourceFromRaw(context: Context, id: Int): String {
            var source: String = ""
            try {
                val r = context.resources
                val inputStream = r.openRawResource(id)
                val outStream = ByteArrayOutputStream()
                val byteArray = ByteArray(1024)
                var readBytes = inputStream.read(byteArray)
                while (readBytes != -1) {
                    outStream.write(byteArray,0,readBytes)
                    readBytes = inputStream.read(byteArray)
                }
                source = outStream.toString()
                inputStream.close()
                outStream.close()
            } catch (e: IOException) {
                Log.e(TAG, "getSourceFromRaw: failed", e)
            }
            return source
        }

        /**
         * 从fbo - rbo中获取图片裸数据 GL_RGBA 8888
         */
        fun getBitmapFromFBO(streamWidth: Int, streamHeight: Int): Bitmap? {
            val buffer = ByteBuffer.allocateDirect(streamWidth * streamHeight * 4)
            GLES20.glReadPixels(
                0, 0, streamWidth, streamHeight, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, buffer
            )
            //Create bitmap preview resolution
            val bitmap = Bitmap.createBitmap(streamWidth, streamHeight, Bitmap.Config.ARGB_8888)
            //Set buffer to bitmap
            bitmap.copyPixelsFromBuffer(buffer)
            //Scale to stream resolution
            //Flip vertical
            //return flipVerticalBitmap( bitmap,streamWidth,streamHeight)
            return bitmap
        }

        /**
         * 垂直翻转
         */
        private fun flipVerticalBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap? {
            val cx = width / 2f
            val cy = height / 2f
            val matrix = Matrix()
            matrix.postScale(1f, -1f, cx, cy)
            return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        }


        /**
         * 加载shader
         */
        fun loadShader(shaderType:Int,sourceStr:String):Int{
            var shader = GLES20.glCreateShader(shaderType)
            GLES20.glShaderSource(shader, sourceStr)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                throw RuntimeException("Could not compile shader {$shaderType} - ${GLES20.glGetShaderInfoLog(shader)}")
            }
            return shader
        }

        /**
         * 设置文字水印
         *
         * @param text      文本内容
         * @param textSize  文字大小
         * @param textColor 文字颜色
         * @param bgColor   文字背景颜色 #00000000
         * @param padding   文字与边距距离
         * @return 文字水印的 bitmap
         */
        fun createTextImage(
            text: String,
            textSize: Int,
            textColor: String?,
            bgColor: String? = null,
            padding: Int
        ): Bitmap? {
            val paint = Paint()
            val color = Color.parseColor(textColor)
            paint.color = color
            paint.textSize = textSize.toFloat()
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = true
            val list = text.split(Constants.TEXT_WRAP_SYMBOL)
            var maxWidth = 0
            var totalHeight = 0
            var height = 0
            var heightList = mutableListOf<Int>()
            var width = 0
            var acs = 0f

            //实现多行文字水印
            list.forEach { value ->
                width = paint.measureText(value).toInt()
                val fontMetrics = paint.fontMetrics
                height = ((abs(fontMetrics.bottom) + abs(fontMetrics.top)).toInt())
                acs = abs(fontMetrics.ascent)
                if(width >maxWidth) maxWidth = width
                heightList.add(height)
                totalHeight += height
            }

            maxWidth += 6

            val bm = Bitmap.createBitmap(
                maxWidth ,
                totalHeight + acs.toInt(),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bm)
            if(!bgColor.isNullOrEmpty()) {
                canvas.drawColor(Color.parseColor(bgColor))
            }
            var nx = 3f
            var ny = 0f
            var i = 0
            list.forEach {
                    value ->
                ny += heightList[i++]
                canvas.drawText(value, nx, ny, paint)
            }
            return bm
        }

        /**
         * 设置文字水印
         *
         * @param text      文本内容
         * @param textSize  文字大小
         * @param textColor 文字颜色
         * @param bgColor   文字背景颜色 #00000000
         * @param padding   文字与边距距离
         * @return 文字水印的 bitmap
         */
        fun createMixedImage(
            isTopAligned:Boolean,
            badgeBitmap:Bitmap,
            text: String,
            textSize: Int,
            textColor: String?,
            bgColor: String? = null,
            padding: Int
        ): Bitmap? {
            val paint = Paint()
            val color = Color.parseColor(textColor)
            paint.color = color
            paint.textSize = textSize.toFloat()
            paint.style = Paint.Style.FILL
            paint.isAntiAlias = true
            val list = text.split(Constants.TEXT_WRAP_SYMBOL)
            var maxWidth = 0
            var totalHeight = 0
            var height = 0
            var heightList = mutableListOf<Int>()
            var width = 0
            var acs = 0f

            //实现多行文字水印
            list.forEach { value ->
                width = paint.measureText(value).toInt()
                val fontMetrics = paint.fontMetrics
                height = ((abs(fontMetrics.bottom) + abs(fontMetrics.top)).toInt())
                acs = abs(fontMetrics.ascent)
                if(width >maxWidth) maxWidth = width
                heightList.add(height)
                totalHeight += height
            }

            maxWidth = max(badgeBitmap.width,maxWidth)
            totalHeight += (badgeBitmap.height)



            val bm = Bitmap.createBitmap(
                maxWidth ,
                totalHeight + acs.toInt(),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bm)

            bgColor?.let {
                canvas.drawColor(Color.parseColor(it))
            }

            var nx = 3f
            var ny = 0f
            var i = 0
            if(isTopAligned){
                canvas.drawBitmap(badgeBitmap,
                    (maxWidth - badgeBitmap.width)/2f,0f,paint)
                ny += badgeBitmap.height
            }

            list.forEach {
                    value ->
                ny += heightList[i++]
                canvas.drawText(value, nx , ny, paint)
            }
            if(!isTopAligned){
                ny += 10
                canvas.drawBitmap(badgeBitmap,(maxWidth - badgeBitmap.width)/2f,ny,paint)
            }
            return bm
        }

        /**
         * 加载 bitmap为 2D纹理
         *
         * @param bitmap 图片 bitmap
         * @return 纹理
         */
        fun loadBitmapTexture(): Int {
            val textureIds = IntArray(1)
            GLES20.glGenTextures(1, textureIds, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
            /*
            val bitmapBuffer = ByteBuffer.allocate(bitmap.height * bitmap.width * 4)
            bitmap.copyPixelsToBuffer(bitmapBuffer)
            bitmapBuffer.flip()
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap.width,
                bitmap.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer
            )*/
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return textureIds[0]
        }

        fun fillBitmapTexture(textureId:Int,bitmap: Bitmap){
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            val bitmapBuffer = ByteBuffer.allocate(bitmap.height * bitmap.width * 4)
            bitmap.copyPixelsToBuffer(bitmapBuffer)
            bitmapBuffer.flip()
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, bitmap.width,
                bitmap.height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bitmapBuffer
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }

        fun deleteTextureId(textureId:Int?){
            if(textureId == null || textureId == -1) return
            val textureIds = intArrayOf(textureId)
            GLES20.glDeleteTextures(textureIds.size,textureIds,0)
        }

        /**
         * drawable 转 bitmap
         */
        fun drawableToBitmap(drawable: Drawable): Bitmap? {
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            return bitmap
        }


        fun checkEglError(msg: String) {
            val error = EGL14.eglGetError()
            if (error != EGL14.EGL_SUCCESS) {
                throw RuntimeException("$msg. ----- EGL error: $error")
            }
        }
    }
}