package com.bodycamera.ba.egl

import android.opengl.*
import android.util.Log
import android.view.Surface

class EglCore (eglContext:EGLContext?) {
    /**
     * Constructor flag: surface must be recordable.  This discourages EGL from using a
     * pixel format that cannot be converted efficiently to something usable by the video
     * encoder.
     */

    private var mEglContext = EGL14.EGL_NO_CONTEXT
   // private var mEglSurface = EGL14.EGL_NO_SURFACE
    private var mEglDisplay = EGL14.EGL_NO_DISPLAY
    private var mEGLConfigs :EGLConfig? = null
    private var mEglSurface : EGLSurface ? =null


    init{
        eglSetup(eglContext)
    }


    fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(mEglDisplay, mEglSurface, mEglSurface, mEglContext)) {
            Log.e(TAG, "eglMakeCurrent failed")
        }
    }

    fun swapBuffer() {
        if (!EGL14.eglSwapBuffers(mEglDisplay, mEglSurface)) {
            Log.e(TAG, "eglSwapBuffers failed")
        }
    }

    /**
     * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
     */
    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(mEglDisplay, mEglSurface, nsecs)
    }

    /**
     * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
     */
    private fun eglSetup(
        eglSharedContext: EGLContext?
    ) {
        mEglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (mEglDisplay === EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(mEglDisplay, version, 0, version, 1)) {
            throw RuntimeException("unable to initialize EGL14")
        }

        val attribList: IntArray =
            intArrayOf(
                EGL14.EGL_RED_SIZE,
                8,
                EGL14.EGL_GREEN_SIZE,
                8,
                EGL14.EGL_BLUE_SIZE,
                8,
                EGL14.EGL_SURFACE_TYPE,
                EGL14.EGL_PBUFFER_BIT or EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_RENDERABLE_TYPE,
                EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID,
                FLAG_RECORDABLE,  /* AA https://stackoverflow.com/questions/27035893/antialiasing-in-opengl-es-2-0 */ //EGL14.EGL_SAMPLE_BUFFERS, 1 /* true */,
                //EGL14.EGL_SAMPLES, 4, /* increase to more smooth limit of your GPU */
                EGL14.EGL_NONE
            )

        val configs = arrayOfNulls<EGLConfig>(1)

        val numConfigs = IntArray(1)
        if(!EGL14.eglChooseConfig(mEglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)){
            throw RuntimeException("EglCore eglChooseConfig failed")
        }
        mEGLConfigs = configs[0]
        // Configure context for OpenGL ES 2.0.
        val attrib_list = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE
        )
        mEglContext = EGL14.eglCreateContext(
            mEglDisplay, configs[0],
            eglSharedContext ?: EGL14.EGL_NO_CONTEXT, attrib_list, 0
        )

        if(mEglContext == EGL14.EGL_NO_CONTEXT){
            throw RuntimeException("eglSetup eglContext is null")
        }

        if (!EGL14.eglMakeCurrent(mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, mEglContext)) {
           throw RuntimeException("EglCore eglMakeCurrent failed")
        }

    }

    fun createWindowSurface(surface: Surface) {

        if(mEglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("EglCore mEglDisplay is null")
        val surfaceAttribs = intArrayOf(
            EGL14.EGL_NONE
        )
         mEglSurface = EGL14.eglCreateWindowSurface(mEglDisplay, mEGLConfigs, surface, surfaceAttribs, 0)
        if (mEglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglSetup mEglSurface is null")
        }
    }

    fun createOffWindowSurface() {
        if(mEglDisplay == EGL14.EGL_NO_DISPLAY) throw RuntimeException("EglCore mEglDisplay is null")

        val surfaceAttribs = intArrayOf(
            EGL14.EGL_NONE
        )
        mEglSurface = EGL14.eglCreatePbufferSurface(mEglDisplay,mEGLConfigs,surfaceAttribs,0)
        if (mEglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("eglSetup mEglSurface is null")
        }
    }

    /**
     * Discards all resources held by this class, notably the EGL context.
     */
    fun release() {
        if (mEglDisplay !== EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                mEglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(mEglDisplay, mEglSurface)
            EGL14.eglDestroyContext(mEglDisplay, mEglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(mEglDisplay)
            mEGLConfigs = null

        }
        mEglDisplay = EGL14.EGL_NO_DISPLAY
        mEglContext = EGL14.EGL_NO_CONTEXT
        mEglSurface = EGL14.EGL_NO_SURFACE
    }

    fun getEglContext(): EGLContext? {
        return mEglContext
    }



    companion object {
        const val TAG = "EglCore"
        const val FLAG_RECORDABLE = 0x01

        // Android-specific extension.
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }

}