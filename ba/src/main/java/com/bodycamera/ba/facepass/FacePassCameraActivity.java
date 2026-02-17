package com.bodycamera.ba.facepass;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;

import com.bodycamera.tests.R;
import com.bodycamera.ba.facepass.camera.CameraManager;
import com.bodycamera.ba.facepass.camera.CameraPreview;
import com.bodycamera.ba.facepass.camera.CameraPreviewData;
import com.bodycamera.ba.facepass.camera.ComplexFrameHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.concurrent.ArrayBlockingQueue;

import mcv.facepass.FacePassException;
import mcv.facepass.FacePassHandler;
import mcv.facepass.types.FacePassConfig;
import mcv.facepass.types.FacePassTrackResult;
import mcv.facepass.types.FacePassImage;
import mcv.facepass.types.FacePassImageType;
import mcv.facepass.types.FacePassModel;
import mcv.facepass.types.FacePassPose;
import mcv.facepass.types.FacePassLivenessResult;
import mcv.facepass.types.FacePassImageRotation;
import mcv.facepass.types.FacePassLivenessMode;
import mcv.facepass.types.FacePassTrackedFace;
import mcv.facepass.types.FacePassAddFaceResult;
import mcv.facepass.types.FacePassRecognitionResult;
import mcv.facepass.types.FacePassRecognitionState;
import mcv.facepass.types.FacePassRecogMode;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

/**
 * 顔認証アクティビティ (Dual Camera対応版)
 * FacePass SDKを使用して、RGBカメラとIRカメラの両方を用いて生体検知(Liveness Detection)を行います。
 */
public class FacePassCameraActivity extends Activity implements CameraManager.CameraListener {

    private static final String TAG = "FacePassCameraActivity";
    public static final String EXTRA_IMAGE_PATH = "image_path";

    /* 人脸识别 Group */
    private static final String group_name = "face-pass-test-5";

    /* SDK ハンドラー */
    private FacePassHandler mFacePassHandler;

    /* カメラ管理変数 */
    private CameraManager mCameraManager; // RGBカメラ用 (背面/ID 0相当)
    private CameraManager mIRCameraManager; // IRカメラ用 (前面/ID 1相当)

    // 検知結果と画像データを処理するためのキュー
    private ArrayBlockingQueue<CameraPreviewData> mDetectResultQueue = new ArrayBlockingQueue<CameraPreviewData>(5);
    private ArrayBlockingQueue<FacePassTrackResult> mResultQueue = new ArrayBlockingQueue<FacePassTrackResult>(5);

    /* UI レイアウト */
    private FrameLayout mFramePreviewRgb; // RGBカメラ映像用コンテナ (大画面)
    private FrameLayout mFramePreviewIr; // IRカメラ映像用コンテナ (小画面/PiP)
    private CameraPreview mCameraPreview; // RGBプレビュービュー
    private CameraPreview mIRCameraPreview; // IRプレビュービュー

    // Debug UI
    private android.widget.TextView tvDebugStatus;
    private android.widget.Button btnDebugRotate;
    private android.widget.Button btnCapture;

    // Manual Capture logic
    private FacePassImage currentImageRGB;
    private CameraPreviewData currentFrameData;
    private final Object frameLock = new Object();

    // Threads
    private FeedFrameThread mFeedFrameThread;
    private RecognizerThread mRecognizeThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 画面を常時点灯に設定
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Android 11+ (API 30+) のための全ファイルアクセス権限要求
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 102);
            }
        }

        // レイアウト設定 (activity_face_pass_camera.xml)
        setContentView(R.layout.activity_face_pass_camera);

        // UIコンポーネントの初期化
        mFramePreviewRgb = findViewById(R.id.fl_preview_rgb);
        mFramePreviewIr = findViewById(R.id.fl_preview_ir);

        // Debug UI Init
        tvDebugStatus = findViewById(R.id.tv_debug_status);
        btnDebugRotate = findViewById(R.id.btn_debug_rotate);
        btnCapture = findViewById(R.id.btn_capture);

        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processManualCapture();
            }
        });

        btnDebugRotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Rotation Cycle: 0 -> 90 -> 180 -> 270 -> 0
                int current = SettingVar.faceRotation;
                int next = 0;
                String label = "";
                switch (current) {
                    case 0:
                        next = 90;
                        label = "90";
                        break; // DEG0->DEG90 (Assuming int values match, checking SettingVar)
                    case 90:
                        next = 180;
                        label = "180";
                        break;
                    case 180:
                        next = 270;
                        label = "270";
                        break;
                    case 270:
                        next = 0;
                        label = "0";
                        break;
                    default:
                        next = 0;
                        label = "0";
                        break;
                }
                SettingVar.faceRotation = next;
                btnDebugRotate.setText("Rotate AI (" + label + ")");
                Toast.makeText(FacePassCameraActivity.this, "AI Rotation set to: " + label, Toast.LENGTH_SHORT).show();
            }
        });

        // RGBプレビュー設定
        // 1. 180度回転 (カメラ設定で270度を指定したため、View回転は不要に戻す)
        // mFramePreviewRgb.setRotation(180f);

        // 2. 左右反転 (鏡像表示 - 自撮り用)
        mFramePreviewRgb.setScaleX(-1f);

        // SDKの初期化と認証
        // initFacePassSDK();
        // -> 権限チェック後に実行します

        if (checkPermissions()) {
            // FacePassManager.getInstance().init(getApplicationContext()); // Called inside
            // initFacePassSDK() wrapper for now
            initFacePassSDK();
            initCamera();

            mRecognizeThread = new RecognizerThread();
            mRecognizeThread.start();

            mFeedFrameThread = new FeedFrameThread();
            mFeedFrameThread.start();
        } else {
            requestPermissions(new String[] {
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.CAMERA
            }, 101);
        }
    }

    private boolean checkPermissions() {
        int readStorage = checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        int camera = checkSelfPermission(android.Manifest.permission.CAMERA);
        return readStorage == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                camera == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                initFacePassSDK();
                initCamera();
                mRecognizeThread = new RecognizerThread();
                mRecognizeThread.start();
                mFeedFrameThread = new FeedFrameThread();
                mFeedFrameThread.start();
            } else {
                Toast.makeText(this, "権限が必要です。設定から許可してください。", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * FacePass SDKの初期化処理
     * 必要なモデルファイルの読み込み、ライセンス認証、パラメータ設定を行います。
     */
    private void initFacePassSDK() {
        // コンテキストとライセンスキー(空文字)で初期化
        FacePassHandler.initSDK(getApplicationContext(), "");

        // ライセンス認証の実行 (Downloadフォルダの.certファイルを使用)
        boolean authSuccess = checkAndAuth();
        if (!authSuccess) {
            showErrorDialog(
                    "ライセンス認証に失敗しました。\nDownloadフォルダに有効な .cert ファイルがあるか確認してください。\n(例: RK3568-test-20241127.cert)");
            return;
        }

        FacePassConfig config = new FacePassConfig();
        // モデルファイルの読み込み (assets/models/ フォルダから)
        try {
            config.rgbIrLivenessModel = FacePassModel.initModel(getApplicationContext().getAssets(),
                    "models/mcv_liveness_A.bin");
            config.LivenessModel = FacePassModel.initModel(getApplicationContext().getAssets(),
                    "models/mcv_livenessrgb_A.bin");
            config.searchModel = FacePassModel.initModel(getApplicationContext().getAssets(),
                    "models/mcv_feature_Ari.bin");
            config.poseBlurModel = FacePassModel.initModel(getApplicationContext().getAssets(),
                    "models/mcv_poseblur_A.bin");
            config.postFilterModel = FacePassModel.initModel(getApplicationContext().getAssets(),
                    "models/mcv_postfilter_A.bin");
            config.rcAttributeModel = FacePassModel.initModel(getApplicationContext().getAssets(),
                    "models/mcv_rc_attribute_A.bin");
            config.detectModel = FacePassModel.initModel(getApplicationContext().getAssets(),
                    "models/mcv_rk3568_det_A_det.bin");
            config.occlusionFilterModel = FacePassModel.initModel(getApplicationContext().getAssets(),
                    "models/mcv_occlusion_B.bin");
        } catch (Exception e) {
            showErrorDialog("モデルファイルの読み込みエラー: " + e.getMessage());
            return;
        }

        // デュアルカメラ(RGB+IR)生体検知設定
        // config.rgbIrLivenessEnabled = true; // RGB+IRモードを有効化
        config.rgbIrLivenessEnabled = false; // DEBUG: RGB単独モードでテスト
        config.livenessThreshold = 88f; // 生体検知の閾値 (推奨: 80-88)

        // その他の検出パラメータ設定 - EXTREME RELAXED FOR DEBUGGING
        config.faceMinThreshold = 50; // Minimum face size in pixels (demo uses 100)
        config.rcAttributeEnabled = false; // Disable attribute check
        config.searchThreshold = 50f; // Lower threshold
        config.poseThreshold = new FacePassPose(90f, 90f, 90f); // Accept any angle
        config.blurThreshold = 0.3f; // Accept blurry images
        config.lowBrightnessThreshold = 10f; // Accept very dark
        config.highBrightnessThreshold = 250f; // Accept very bright
        config.brightnessSTDThreshold = 200f;
        config.retryCount = 10;
        config.maxFaceEnabled = true; // 最大の顔のみを検出
        config.fileRootPath = "/sdcard/Download"; // CRITICAL: Must match demo!

        /*
         * REFACTORED: Use FacePassManager Singleton
         * Replaces local init logic to match Maker's Demo exactly.
         */
        FacePassManager.getInstance().init(this);
        mFacePassHandler = FacePassManager.mFacePassHandler; // Might be null initially, check before use

    }

    /**
     * ライセンスファイルの確認と認証
     * フォルダ内の .cert ファイルを検索して認証を試みます。
     */
    private boolean checkAndAuth() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File sdcardDir = Environment.getExternalStorageDirectory();

        // 検索対象ディレクトリ
        File[] searchDirs = { downloadDir, sdcardDir };

        // 優先ファイル名（もしあれば）
        String[] priorityFiles = {
                "FacePassSDK20250911.cert",
                "RK3568-test-20241127.cert",
                "CBG_Panel_Face_Reco_MiniCapacity---30-Trial-one-stage.cert"
        };

        StringBuilder debugInfo = new StringBuilder();

        for (File dir : searchDirs) {
            if (dir == null || !dir.exists() || !dir.canRead())
                continue;

            Log.d(TAG, "Searching in: " + dir.getAbsolutePath());

            // 1. 優先ファイル名の確認
            for (String priorityName : priorityFiles) {
                File f = new File(dir, priorityName);
                if (f.exists()) {
                    if (tryAuth(f))
                        return true;
                }
            }

            // 2. ディレクトリ内の全 .cert ファイルを試行
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().endsWith(".cert")) {
                        debugInfo.append("Found: ").append(f.getName()).append("\n");
                        if (tryAuth(f)) {
                            return true;
                        }
                    }
                }
            }
        }

        Log.e(TAG, "Auth Failed. Found certs:\n" + debugInfo.toString());
        final String debugMsg = debugInfo.toString();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (debugMsg.isEmpty()) {
                    Toast.makeText(FacePassCameraActivity.this, "No .cert files found.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(FacePassCameraActivity.this, "Auth failed for: " + debugMsg, Toast.LENGTH_LONG)
                            .show();
                }
            }
        });

        return false;
    }

    private boolean tryAuth(File certFile) {
        String certContent = readFileContent(certFile);
        if (certContent != null && !certContent.isEmpty()) {
            int ret = FacePassHandler.auth_algomall(certContent);
            if (ret == 0) {
                Log.d(TAG, "認証成功 (AlgoMall) path=" + certFile.getAbsolutePath());
                return true;
            } else {
                Log.e(TAG, "認証失敗 path=" + certFile.getAbsolutePath() + " code=" + ret);
            }
        }
        return false;
    }

    /** ユーティリティ: テキストファイル読み込み */
    private String readFileContent(File file) {
        StringBuilder sb = new StringBuilder();
        try {
            FileInputStream inputStream = new FileInputStream(file);
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, len));
            }
            inputStream.close();
            return sb.toString().trim();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** エラーダイアログ表示用 */
    private void showErrorDialog(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(FacePassCameraActivity.this)
                        .setTitle("FacePass エラー")
                        .setMessage(message)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        })
                        .setCancelable(false)
                        .show();
            }
        });
    }

    private void initCamera() {
        // RGBカメラの初期化 (Back Camera)
        mCameraManager = new CameraManager();
        mCameraPreview = new CameraPreview(this);

        // IRカメラの初期化 (Front Camera)
        mIRCameraManager = new CameraManager();
        mIRCameraPreview = new CameraPreview(this);

        // レイアウトへの追加: IRを下層、RGBを上層に配置
        mFramePreviewIr.removeAllViews();
        mFramePreviewRgb.removeAllViews();
        mFramePreviewIr.addView(mIRCameraPreview);
        mFramePreviewRgb.addView(mCameraPreview);

        mCameraManager.setPreviewDisplay(mCameraPreview);
        mCameraManager.setListener(this);

        mIRCameraManager.setPreviewDisplay(mIRCameraPreview);
        mIRCameraManager.setListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 画面サイズ取得
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        SettingVar.mHeight = displayMetrics.heightPixels;
        SettingVar.mWidth = displayMetrics.widthPixels;

        // 両方のカメラを開く
        // RGB (false: Back), IR (true: Front)
        // 解像度を1280x720に設定 (Demoアプリ準拠)
        int reqWidth = 1280;
        int reqHeight = 720;
        mCameraManager.open(getWindowManager(), false, reqWidth, reqHeight);
        mIRCameraManager.open(getWindowManager(), true, reqWidth, reqHeight);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraManager != null)
            mCameraManager.release();
        if (mIRCameraManager != null)
            mIRCameraManager.release();
    }

    @Override
    public void onPictureTaken(CameraPreviewData cameraPreviewData) {
        // DEBUG: Flash a toast every 3 seconds to prove camera is working
        throttleToast("Frame Received: " + (cameraPreviewData.front ? "Front" : "Back"));

        if (cameraPreviewData.front) {
            // Front Camera -> IR
            ComplexFrameHelper.addIRFrame(cameraPreviewData);
        } else {
            // Back Camera -> RGB
            ComplexFrameHelper.addRgbFrame(cameraPreviewData);
        }
    }

    private long lastToastTime = 0;

    private void throttleToast(final String msg) {
        long now = System.currentTimeMillis();
        if (now - lastToastTime > 3000) {
            lastToastTime = now;
            runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
        }
    }

    // Manual Capture Logic - DISABLED for Demo Flow
    private void processManualCapture() {
        Toast.makeText(this, "Manual Capture Disabled. Frame Feeding is Auto.", Toast.LENGTH_SHORT).show();
    }

    private void saveDebugImage(byte[] nv21, int width, int height, String tag) {
        try {
            android.graphics.YuvImage yuvimage = new android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21,
                    width, height, null);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            yuvimage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 90, baos);
            byte[] jdata = baos.toByteArray();

            String fileName = "debug_face_" + tag + "_" + System.currentTimeMillis() + ".jpg";
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(dir, fileName);

            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            fos.write(jdata);
            fos.close();

            final String msg = "Saved: " + file.getAbsolutePath();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(FacePassCameraActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            });
            Log.d(TAG, msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * カメラフレーム供給スレッド
     * RGBとIRのペアを取得し、SDKに入力します。
     */
    // Demo-style FeedFrameThread
    private class FeedFrameThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                try {
                    // RGB/IRのペアが揃うのを待つ
                    Pair<CameraPreviewData, CameraPreviewData> framePair = ComplexFrameHelper.takeComplexFrame();
                    if (FacePassManager.mFacePassHandler == null) {
                        try {
                            FacePassManager.mFacePassHandler = FacePassManager.getInstance().mFacePassHandler;
                        } catch (Exception e) {
                        }
                        if (FacePassManager.mFacePassHandler == null) {
                            try {
                                Thread.sleep(50);
                            } catch (Exception e) {
                            }
                            throttleToast("SDK Handler is NULL...");
                            continue;
                        }
                    }

                    // Demo Logic: Use SettingVar.faceRotation directly with RAW data.
                    throttleToast("Processing Frame..."); // proves loop is running past handler check
                    FacePassImage imageRGB;
                    FacePassTrackResult detectionResult = null;

                    try {
                        FacePassConfig cfg = FacePassManager.mFacePassHandler.getConfig();
                        // If cfg is null, skip
                        if (cfg == null) {
                            throttleToast("Config is NULL");
                            continue;
                        }

                        if (cfg.rgbIrLivenessEnabled) {
                            imageRGB = new FacePassImage(framePair.first.nv21Data, framePair.first.width,
                                    framePair.first.height, SettingVar.faceRotation, FacePassImageType.NV21);
                            FacePassImage imageIR = new FacePassImage(framePair.second.nv21Data,
                                    framePair.second.width, framePair.second.height, SettingVar.faceRotation,
                                    FacePassImageType.NV21);

                            // Feed RGB+IR
                            detectionResult = FacePassManager.mFacePassHandler.feedFrameRGBIR(imageRGB, imageIR);
                        } else {
                            imageRGB = new FacePassImage(framePair.first.nv21Data, framePair.first.width,
                                    framePair.first.height, SettingVar.faceRotation, FacePassImageType.NV21);

                            // Feed RGB only
                            detectionResult = FacePassManager.mFacePassHandler.feedFrame(imageRGB);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (detectionResult != null && detectionResult.trackedFaces.length > 0) {
                        // Face Found!
                        int faceCount = detectionResult.trackedFaces.length;
                        SettingVar.debugFaceCount = faceCount;
                        Log.d(TAG, "Face Detected: " + faceCount);

                        // Queue for Recognize
                        if (detectionResult.message.length != 0) {
                            mResultQueue.offer(detectionResult);
                            mDetectResultQueue.offer(framePair.first);
                        }

                        // Update UI Debug
                        final int fc = faceCount;
                        runOnUiThread(() -> {
                            if (tvDebugStatus != null)
                                tvDebugStatus.setText("Face: " + fc);
                        });
                    } else {
                        // No Face Found
                        runOnUiThread(() -> {
                            if (tvDebugStatus != null)
                                tvDebugStatus.setText("Scanning... (No Face)");
                        });
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    throttleToast("Exception: " + e.getMessage());
                }
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    // Demo-style RecognizerThread
    private class RecognizerThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                try {
                    FacePassTrackResult result = mResultQueue.take();
                    CameraPreviewData frameData = mDetectResultQueue.take();

                    if (result.trackedFaces.length > 0) {
                        FacePassTrackedFace face = result.trackedFaces[0];
                        Log.d(TAG, "Recognizing Face TrackID: " + face.trackId);

                        // Liveness Check
                        float threshold = 80f; // Relaxed
                        FacePassLivenessResult[] livenessResults = FacePassManager.mFacePassHandler.livenessClassify(
                                result.message,
                                face.trackId,
                                FacePassLivenessMode.FP_REG_MODE_LIVENESS,
                                threshold);

                        boolean isLive = false;
                        if (livenessResults != null && livenessResults.length > 0) {
                            for (FacePassLivenessResult lr : livenessResults) {
                                if (lr.trackId == face.trackId) {
                                    if (lr.livenessState == 0) { // PASS
                                        isLive = true;
                                        Log.d(TAG, "Liveness PASS: Score=" + lr.livenessScore);
                                        runOnUiThread(() -> {
                                            Toast.makeText(FacePassCameraActivity.this,
                                                    "Liveness PASS! Send to Server...", Toast.LENGTH_SHORT).show();
                                        });

                                        // TODO: Send frameData to Server API
                                        // Next step as per user request

                                    } else {
                                        Log.d(TAG, "Liveness FAIL: " + lr.livenessState);
                                        final int state = lr.livenessState;
                                        runOnUiThread(() -> {
                                            if (tvDebugStatus != null)
                                                tvDebugStatus
                                                        .setText("Liveness Check: " + (state == 0 ? "OK" : "FAIL"));
                                        });
                                    }
                                    break;
                                }
                            }
                        }

                        // User said: "Stop here, no need to verify with local DB yet."
                        // So we don't call recognize() local.
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    // 画像保存ヘルパー
    private String saveImage(byte[] data, int width, int height) {
        try {
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "facepass_capture_" + System.currentTimeMillis() + ".jpg");
            android.graphics.YuvImage yuv = new android.graphics.YuvImage(data, android.graphics.ImageFormat.NV21,
                    width, height, null);
            FileOutputStream out = new FileOutputStream(file);
            yuv.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 90, out);
            out.close();
            return file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 底库 (Local Group) の確認と作成
     * group_name が存在しない場合は作成します。
     */

    /**
     * Add Face Logic (Step 3)
     * Adds a bitmap to the SDK and returns the face token.
     */
    public void addFace(Bitmap bitmap) {
        if (mFacePassHandler == null)
            return;
        try {
            FacePassAddFaceResult result = mFacePassHandler.addFace(bitmap);
            if (result != null) {
                if (result.result == 0) {
                    String msg = "add face successfully: " + new String(result.faceToken);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                    // faceTokenEt.setText(new String(result.faceToken)); // Only if UI has EditText
                    Log.d(TAG, msg);
                } else if (result.result == 1) {
                    Toast.makeText(this, "no face !", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "quality problem！", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (FacePassException e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Bind Group Logic (Step 4)
     * Binds a face token to the local group.
     */
    public void bindFaceToGroup(byte[] faceToken) {
        if (mFacePassHandler == null)
            return;
        try {
            boolean b = mFacePassHandler.bindGroup(group_name, faceToken);
            String result = b ? "success" : "failed";
            Toast.makeText(this, "bind " + result, Toast.LENGTH_SHORT).show();
            Log.d(TAG, "bindGroup(" + group_name + ", token) -> " + result);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Bind Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
