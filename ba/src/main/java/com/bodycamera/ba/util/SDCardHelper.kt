package com.bodycamera.ba.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import com.bodycamera.ba.data.StorageF

import java.io.File
import java.lang.reflect.Array

/**
 *
 * SDCardHelper
 */
object SDCardHelper {


    private var mSDCards:ArrayList<File>? = null
    private var mHasInited = false
    private var mIsRecycleCheck = false

    fun getInternalStorageDirectory():File{
        //Base2Application.getMyApp().externalCacheDir
        val oDir =  File(Environment.getExternalStorageDirectory().absolutePath  + "/" + Environment.DIRECTORY_DCIM)
        //val oDir =  File(Environment.getExternalStorageDirectory().absolutePath + "a12/" +  Environment.DIRECTORY_DCIM)
        //val oDir = File(Environment.getDataDirectory(),Environment.DIRECTORY_DCIM)
        //val oDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        if(!oDir.exists()) oDir.mkdirs()
        return oDir
    }
    fun getInternalStorageRoot():File{
        return Environment.getExternalStorageDirectory()
    }
    fun getSDCardStorageRoot(context: Context):File? {
        initSDCardAtFirst(context)
        return if(mSDCards?.isNotEmpty() == true)  mSDCards?.first() else null
    }

    fun getSDCardList(context: Context):List<File>?{
        initSDCardAtFirst(context)
        return if(mSDCards?.isNotEmpty() == true) mSDCards?.toList() else null
    }

    fun amountSDCard(context: Context,file:File){
        initSDCardAtFirst(context)
        if(mSDCards == null) mSDCards = ArrayList()
        if(getSDCardState(file) == Environment.MEDIA_MOUNTED) {
            mSDCards?.add(file)
        }
    }


    fun unAmountSDCard(context: Context,file:File){
        initSDCardAtFirst(context)
        mSDCards?.let {
            val iterator = it.iterator()
            while(iterator.hasNext()){
                val it = iterator.next()
                if(it.absolutePath == file.absolutePath){
                    iterator.remove()
                    return@let
                }
            }
        }
    }

    fun getSDCardStorageDirectory(context: Context):File? {
        val sdcard = getSDCardStorageRoot(context) ?: return null
        val file = File(sdcard, "/Android/data/${context.packageName}/cache")
        Log.d(TAG, "getSDCardStorageDirectory: file=${file.absolutePath}")
        if (!file.exists()) file.mkdirs()
        return file
    }

    private fun initSDCardAtFirst(context: Context){
        initSDCard(context)
    }


    private fun initSDCard(context: Context){

        val result =  filterSDCard(context)
        val iterator = result.iterator()
        while(iterator.hasNext()){
            val it = iterator.next()
            if(getSDCardState(it) != Environment.MEDIA_MOUNTED){
                iterator.remove()
            }
        }
        mSDCards = result
    }

    private fun getSDCardState(sdcard:File):String{
        return if(Build.VERSION.SDK_INT>=19){
            Environment.getStorageState(sdcard)
        } else {
            if (sdcard.exists() && sdcard.isDirectory && sdcard.canRead()) {
                if (sdcard.canWrite()) {
                    Environment.MEDIA_MOUNTED
                } else {
                    Environment.MEDIA_MOUNTED_READ_ONLY
                }
            } else {
                Environment.MEDIA_REMOVED
            }
        }
    }

    private fun filterSDCard(context: Context):ArrayList<File> {
        val result = arrayListOf<File>()
        val dirs = getDiskVolume(context) ?:return result
        dirs.forEachIndexed { index, file ->
            if(index != 0 && file.isDirectory){
                result.add(file)
            }
        }
        return result
    }

    @SuppressLint("SuspiciousIndentation")
    private fun getDiskVolume(context: Context): List<File> {
        val mContext = context.applicationContext
        val mStorageManager = mContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val  sv = mStorageManager.storageVolumes
        val results = mutableListOf<File>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            for(ss in sv){
                if(ss.isRemovable || ss.isPrimary){
                    val file = ss.directory
                    if(file!=null) results.add(file)
                    Log.i(TAG, "getDiskVolume30: ${file?.absolutePath}")
                }
            }
        }else{
            try {
                val storageVolumeClazz = Class.forName("android.os.storage.StorageVolume")
                val getVolumeList = mStorageManager.javaClass.getMethod("getVolumeList")
                val getPath = storageVolumeClazz.getMethod("getPath")
                val result = getVolumeList.invoke(mStorageManager)
                val length = Array.getLength(result)

                for (i in 0 until length) {
                    val storageVolumeElement = Array.get(result, i)
                    val file = getPath.invoke(storageVolumeElement) as String
                    if(file!=null) results.add(File(file))
                    Log.i(TAG, "getDiskVolume: ${file}")
                }
            } catch (ex: Exception) {
                Log.e(TAG, "getDiskVolume: failed", ex)
            }
        }
        return results


    }

    /**
     * 获取系统 android/data
     */
    fun getSystemMemorySize(context: Context?): StorageF {
        val file = Environment.getDataDirectory()
        Log.i(TAG, "getSystemMemorySize: file=$file")
        val statf = StatFs(file.path)
        val blockSize = statf.blockSizeLong
        val availableBlocks = statf.availableBlocksLong
        val totalBlock = statf.blockCountLong
        return StorageF(StorageF.STORAGE_SYSTEM,totalBlock * blockSize,availableBlocks * blockSize)
    }

    /**
     * 获取系统 外置
     */
    fun getExternalMemorySize(context: Context?): StorageF {
        return if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            val file = Environment.getExternalStorageDirectory()
            Log.i(TAG, "getExternalMemorySize: file=$file")
            val statf = StatFs(file.path)
            val blockSize = statf.blockSizeLong
            val availableBlocks = statf.availableBlocksLong
            val totalBlock = statf.blockCountLong
            StorageF(
                StorageF.STORAGE_EXTERNAL,
                totalBlock * blockSize,
                availableBlocks * blockSize
            )
        }else
            StorageF(
                StorageF.STORAGE_EXTERNAL,
                0,
                0
            )
    }

    /**
     * 获取系统 sdcard
     */
    fun getSDCardMemorySize(context: Context?): StorageF {
        val file = getSDCardStorageRoot(context!!)
        return if(file != null) {
            try {
                val statf = StatFs(file.path)
                val blockSize = statf.blockSizeLong
                val availableBlocks = statf.availableBlocksLong
                val totalBlock = statf.blockCountLong
                StorageF(
                    StorageF.STORAGE_SDCARD,
                    totalBlock * blockSize,
                    availableBlocks * blockSize
                )
            }catch (ex:java.lang.Exception){
                Log.e(TAG, "getSDCardMemorySize: failed", ex)
                StorageF(
                    StorageF.STORAGE_SDCARD,
                    0,
                    0
                )
            }
        }else{
            StorageF(
                StorageF.STORAGE_SDCARD,
                0,
                0
            )
        }
    }




    /**
     * 1.stop recording
     * 2.start deleting files in external memory
     */
    fun formatExternalMemory(context: Context) :Boolean {
        val external = getSDCardStorageRoot(context) ?: return false
        val files = external.listFiles()
        if(files!= null && files.isNotEmpty()) {
            for (file in files) {
                Log.e(TAG, "formatExternalMemory: ${file.absolutePath},${file.isRooted},${file.isHidden}", )
                if (file.isDirectory) {
                    if(!file.name.startsWith("System Volume Information",true)
                        && !file.name.startsWith("LOST.DIR",true))
                        file.deleteRecursively()
                }
                else if (file.isFile) file.delete()
            }
        }
        return true
    }



    fun getStoragePath(context: Context, volume: StorageVolume): String? {
        // 如果是 Android 11 及以上
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return volume.directory?.path
        } else {
            // Android 11 以下版本
            return if (volume.isPrimary) {
                // 主存储器（通常是内置存储）
                Environment.getExternalStorageDirectory().path
            } else {
                // 可移除的存储器（如 SD 卡）
                // 使用 StorageManager 获取所有存储路径
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val storageVolumes: List<StorageVolume> = storageManager.storageVolumes

                // 遍历存储卷，返回可移除的存储路径
                for (vol in storageVolumes) {
                    if (!vol.isPrimary && vol.isRemovable) {
                        // 在 Android 11 以下无法直接获取路径，可以尝试其他逻辑
                        return Environment.getExternalStorageDirectory().path // 示例路径
                    }
                }
                null // 如果没有找到对应的路径
            }
        }
    }


    const val TAG = "SDCardHelper"
}