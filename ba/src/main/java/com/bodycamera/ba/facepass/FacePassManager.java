package com.bodycamera.ba.facepass;

import android.app.ProgressDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import mcv.facepass.FacePassException;
import mcv.facepass.FacePassHandler;
import mcv.facepass.auth.FacePassAuthCode;
import mcv.facepass.types.FacePassConfig;
import mcv.facepass.types.FacePassModel;
import mcv.facepass.types.FacePassPose;
import com.bodycamera.ba.facepass.utils.FileUtil;

public class FacePassManager {

    private static final String DEBUG_TAG = "FacePassManager";
    public static final String FILE_ROOT_PATH = "/sdcard/Download";

    /* 人脸识别Group */
    public static String group_name = "face-pass-test-5"; // Use our group name

    public static Boolean isLocalGroupExist = false;
    private ProgressDialog progressDialog;

    public enum FacePassAuthMode {
        FACEPASS_AUTH_CHIP,
        FACEPASS_AUTH_MCVSAFE,
        FACEPASS_AUTH_FACEPPLUS,
        FACEPASS_AUTH_ALGOMALL
    }

    /* SDK 实例对象 */
    public static FacePassHandler mFacePassHandler;

    public static String FACE_ALGOMALL_CERT_PATH = "/Download/RK3568-test-20241127.cert"; // Will be updated in init

    private String modelsPath = "";
    // 单例实例
    private static volatile FacePassManager instance;

    public static FacePassManager getInstance() {
        if (instance == null) {
            synchronized (FacePassManager.class) {
                if (instance == null) {
                    instance = new FacePassManager();
                }
            }
        }
        return instance;
    }

    public void init(final Context context) {
        // 创建并显示加载对话框
        // Note: ProgressDialog is deprecated but used in demo.
        // We can skip it or use it. Let's try to use it for visual feedback if context
        // allows.
        try {
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("SDK Initializing...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean success = initFacePassSDK(context);
                    if (!success) {
                        Log.e(DEBUG_TAG, "initFacePassSDK failed!");
                    }

                    // Close dialog
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (progressDialog != null && progressDialog.isShowing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                }
                            }
                            if (success) {
                                Toast.makeText(context, "SDK Init Success", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(context, "SDK Init Failed", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private boolean initFacePassSDK(Context context) throws IOException {
        // 1. Setup paths and copy assets if needed
        modelsPath = context.getExternalFilesDir(null).getAbsolutePath() + "/models";

        // FIX: Cert is in Download folder, not assets!
        FACE_ALGOMALL_CERT_PATH = "/sdcard/Download/facepass_test.cert";

        Log.d(DEBUG_TAG, "Checking cert at: " + FACE_ALGOMALL_CERT_PATH);

        // Ensure models folder exists
        File modelsDir = new File(modelsPath);
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }

        // Copy all assets/models to internal storage if cert doesn't exist
        // (Simplified logic from demo: if cert missing, copy all. Or strictly copy
        // all?)
        // Demo: if (!new File(FACE_ALGOMALL_CERT_PATH).exists()) { copy... }
        if (!new File(FACE_ALGOMALL_CERT_PATH).exists()) {
            Log.d(DEBUG_TAG, "Cert not found, copying assets to: " + modelsPath);
            FileUtil.copyAssetsToInternal(context, "models", modelsPath);
        } else {
            Log.d(DEBUG_TAG, "Cert found, skipping copy.");
            FileUtil.copyAssetsToInternal(context, "models", modelsPath); // COPY ANYWAY to be safe for model updates
        }

        Log.d(DEBUG_TAG, "FACE_ALGOMALL_CERT_PATH: " + FACE_ALGOMALL_CERT_PATH);
        Context ctx = context.getApplicationContext();
        FacePassHandler.initSDK(ctx, "");
        Log.d(DEBUG_TAG, "SDK Version: " + FacePassHandler.getVersion());

        // 2. Auth
        boolean authRet = auth(ctx, FacePassAuthMode.FACEPASS_AUTH_ALGOMALL);
        if (!authRet) {
            Log.e(DEBUG_TAG, "Auth failed!");
            return false;
        }

        // 3. Check Available
        boolean ret = FacePassHandler.isAvailable();
        if (ret) {
            createHandler(context);
        } else {
            Log.d(DEBUG_TAG, "Face auth result : available check failed.");
            return false;
        }

        return true;
    }

    private boolean auth(Context ctx, FacePassAuthMode mode) {
        if (mode == FacePassAuthMode.FACEPASS_AUTH_ALGOMALL) {
            // 算法商城单独授权
            String cert = FileUtil.readExternal(FACE_ALGOMALL_CERT_PATH).trim();
            if (TextUtils.isEmpty(cert)) {
                Log.d(DEBUG_TAG, "face algo mall cert is null at " + FACE_ALGOMALL_CERT_PATH);
                return false;
            }

            int ret = FacePassHandler.auth_algomall(cert);
            if (ret != FacePassAuthCode.FP_AUTH_OK) {
                Log.d(DEBUG_TAG, "auth_algomall failed: " + ret);
                return false;
            }

            Log.d(DEBUG_TAG, "face algo mall success.");
            return true;
        }
        return false;
    }

    private void createHandler(final Context context) {
        // In demo this runs in a thread, but we are already in a thread in init().
        // Demo creates ANOTHER thread inside createHandler? Yes.
        // Let's keep it simple and run synchronously since we are already in background
        // thread from init()

        while (FacePassHandler.isAvailable()) {
            // Demo has a complex wait loop here involving synchronized wait.
            // It seems to wait until available?
            // "while (FacePassHandler.isAvailable())" ... wait, lines 245 in demo:
            /*
             * while (FacePassHandler.isAvailable()) {
             * synchronized (FacePassHandler.class) {
             * while (!FacePassHandler.isAvailable()) { ... wait ... }
             * }
             * ... init config ...
             */
            // This logic in demo (lines 245-255) looks like a retry loop or wait for
            // availability?
            // Actually line 245 says `while (FacePassHandler.isAvailable())`.
            // If valid, it enters loop.
            // Inside, line 247: `while (!FacePassHandler.isAvailable())` wait.
            // This seems to handle case where it might become unavailable?
            // Let's simplify: just try to init.

            try {
                FacePassConfig config = new FacePassConfig();
                // Use hardcoded paths instead of R.string
                config.rgbIrLivenessModel = FacePassModel.initModel(context.getAssets(), "models/mcv_liveness_A.bin");
                config.LivenessModel = FacePassModel.initModel(context.getAssets(), "models/mcv_livenessrgb_A.bin");
                config.searchModel = FacePassModel.initModel(context.getAssets(), "models/mcv_feature_Ari.bin");
                config.poseBlurModel = FacePassModel.initModel(context.getAssets(), "models/mcv_poseblur_A.bin");
                config.postFilterModel = FacePassModel.initModel(context.getAssets(), "models/mcv_postfilter_A.bin");
                config.rcAttributeModel = FacePassModel.initModel(context.getAssets(), "models/mcv_rc_attribute_A.bin");
                config.detectModel = FacePassModel.initModel(context.getAssets(), "models/mcv_rk3568_det_A_det.bin");
                config.occlusionFilterModel = FacePassModel.initModel(context.getAssets(),
                        "models/mcv_occlusion_B.bin");

                /* 送识别阈值参数 */
                config.searchThreshold = 55f; // Relaxed from 65f
                config.livenessThreshold = 80f; // Relaxed from 88f
                config.faceMinThreshold = 50; // Demo 100, we use 50
                config.poseThreshold = new FacePassPose(90f, 90f, 90f); // EXTREME RELAXED
                config.blurThreshold = 0.3f; // Accept blurry
                config.lowBrightnessThreshold = 10f;
                config.highBrightnessThreshold = 250f;
                config.brightnessSTDThreshold = 200f;

                // Match Demo Config Exactly
                config.rgbIrLivenessEnabled = true; // Set to TRUE for Dual Camera
                config.rcAttributeEnabled = true; // Set to TRUE to match Demo

                /* 其他设置 */
                config.maxFaceEnabled = false; // Set to FALSE to match Demo
                config.retryCount = 10;
                config.fileRootPath = FILE_ROOT_PATH; // /sdcard/Download

                /* 创建SDK实例 */
                mFacePassHandler = new FacePassHandler();
                int ret = mFacePassHandler.initHandle(config);
                if (ret != 0) {
                    Log.d(DEBUG_TAG, "Build FacePassHandler failed, error: " + ret);
                    return;
                }

                mFacePassHandler.setIRConfig(1.0, 0.0, 1.0, 0.0, 0.3);

                /* 入库阈值参数 */
                // Optional, but good to have
                try {
                    FacePassConfig addFaceConfig = mFacePassHandler.getAddFaceConfig();
                    if (addFaceConfig != null) {
                        addFaceConfig.faceMinThreshold = 50;
                        mFacePassHandler.setAddFaceConfig(addFaceConfig);
                    }
                } catch (Exception e) {
                }

                checkGroup(context);
                Log.d(DEBUG_TAG, "Build FacePassHandler success.");

                // Break loop after success
                break;

            } catch (FacePassException e) {
                e.printStackTrace();
                Log.d(DEBUG_TAG, "Build FacePassHandler failed exception.");
                return;
            }
        }
    }

    public void checkGroup(Context context) {
        if (mFacePassHandler == null) {
            return;
        }
        String[] localGroups = new String[0];
        try {
            localGroups = mFacePassHandler.getLocalGroups();
        } catch (FacePassException e) {
            e.printStackTrace();
        }
        isLocalGroupExist = false;
        if (localGroups == null || localGroups.length == 0) {
            try {
                mFacePassHandler.createLocalGroup(group_name);
            } catch (FacePassException e) {
                e.printStackTrace();
            }
            return;
        }
        for (String group : localGroups) {
            if (group_name.equals(group)) {
                isLocalGroupExist = true;
                break;
            }
        }
        if (!isLocalGroupExist) {
            try {
                mFacePassHandler.createLocalGroup(group_name);
            } catch (FacePassException e) {
                e.printStackTrace();
            }
        }
    }

}
