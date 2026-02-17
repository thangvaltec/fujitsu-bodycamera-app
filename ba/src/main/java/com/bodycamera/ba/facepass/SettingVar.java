package com.bodycamera.ba.facepass;

import mcv.facepass.types.FacePassImageRotation;

/**
 * Created by wangzhiqiang on 2017/11/22.
 */

public class SettingVar {
    public static int debugFaceCount = 0;
    public static boolean cameraFacingFront = true;
    public static int faceRotation = FacePassImageRotation.DEG90; // Changed from 180 to 90 based on user feedback
    public static boolean isSettingAvailable = true;
    public static int cameraPreviewRotation = 270;
    public static boolean isCross = false;
    public static String SharedPrefrence = "user";
    public static int mHeight;
    public static int mWidth;
    public static boolean cameraSettingOk = false;
    public static boolean iscameraNeedConfig = false;
    public static boolean isButtonInvisible = false;
}
