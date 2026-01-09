package com.bodycamera.ba.codec.filter

import android.content.Context
import com.bodycamera.ba.data.MySize
import com.bodycamera.ba.data.VideoAlign

class SurfaceViewFilter(context: Context): BaseFilter(context),IOnFilterCallback {
    override fun onFilterCreate(outputSize: MySize, videoAlign: VideoAlign) {
        mOutputSize = outputSize
        mVideoAlign = videoAlign
        init()
    }

    override fun onFilterDrawTexture(textureId: Int, textureSize: MySize): Int {
        draw(textureId,textureSize)
        return 0
    }

    override fun onFilterRelease() {
        release()
    }
}
