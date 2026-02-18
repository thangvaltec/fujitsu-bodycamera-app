package mcv.testfacepass;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.toolbox.ImageLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import org.json.JSONObject;
import mcv.testfacepass.utils.FacePassApiHelper;

import mcv.facepass.FacePassException;
import mcv.facepass.types.FacePassConfig;
import mcv.facepass.types.FacePassImage;
import mcv.facepass.types.FacePassImageRotation;
import mcv.facepass.types.FacePassImageType;
import mcv.facepass.types.FacePassLivenessMode;
import mcv.facepass.types.FacePassLivenessResult;
import mcv.facepass.types.FacePassRCAttribute;
import mcv.facepass.types.FacePassRecogMode;
import mcv.facepass.types.FacePassRecognitionResult;
import mcv.facepass.types.FacePassRecognitionState;
import mcv.facepass.types.FacePassTrackOptions;
import mcv.facepass.types.FacePassTrackResult;
import mcv.facepass.types.FacePassTrackedFace;
import mcv.testfacepass.camera.CameraManager;
import mcv.testfacepass.camera.CameraPreview;
import mcv.testfacepass.camera.CameraPreviewData;
import mcv.testfacepass.camera.ComplexFrameHelper;
import mcv.testfacepass.data.DatabaseHelper;
import mcv.testfacepass.utils.FacePassManager;

public class FacePassActivity extends Activity implements CameraManager.CameraListener {

    // ======================================================================
    // Intent Pass-through Params for Smart Retry
    // ======================================================================
    private String mServerUrl;
    private String mDeviceId;
    private String mPoliceId;

    // ======================================================================
    // 既存フィールド
    // ======================================================================

    private static final String DEBUG_TAG = "FacePassDemo";
    private static final String FD_DEBUG_TAG = "FeedFrameDemo";
    private static final String RG_DEBUG_TAG = "RecognizeDemo";


    /* 程序所需权限 ：相机 文件存储 网络访问 */
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_WRITE_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String PERMISSION_READ_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    private static final String PERMISSION_INTERNET = Manifest.permission.INTERNET;
    private static final String PERMISSION_ACCESS_NETWORK_STATE = Manifest.permission.ACCESS_NETWORK_STATE;
    private String[] Permission = new String[]{PERMISSION_CAMERA, PERMISSION_WRITE_STORAGE, PERMISSION_READ_STORAGE, PERMISSION_INTERNET, PERMISSION_ACCESS_NETWORK_STATE};


    /* 相机实例 */
    private CameraManager manager;
    private CameraManager mIRCameraManager;


    /* 显示faceId */
    private TextView faceEndTextView;

    /* 相机预览界面 */
    private CameraPreview cameraView;
    private CameraPreview mIRCameraView;


    /* 在预览界面圈出人脸 */
    private FaceView faceView;

    private ScrollView scrollView;

    /* 相机是否使用前置摄像头 */
    private static boolean cameraFacingFront = true;

    private int cameraRotation = 90; // Device-specific: 270 for original device, 90 for this RK3568 (fixes width>height sideways issue)

    private static final int cameraWidth = 1280;
    private static final int cameraHeight = 720;

    private int mSecretNumber = 0;
    private static final long CLICK_INTERVAL = 600;
    private long mLastClickTime;

    private int heightPixels;
    private int widthPixels;

    int screenState = 0;// 0 横 1 竖

    LinearLayout ll;
    FrameLayout frameLayout;
    private Button settingButton;
    /*Toast*/
    private Toast mRecoToast;

    /*DetectResult queue*/
    ArrayBlockingQueue<RecognizeData> mDetectResultQueue;

    /*recognize thread*/
    RecognizeThread mRecognizeThread;
    FeedFrameThread mFeedFrameThread;


    private Handler mAndroidHandler;

    private DatabaseHelper dbHelper;

    /* なりすまし判定の安定化用 */
    private long mLastPassTrackId = -1;
    private int mConsecutivePassCount = 0;
    private static final int REQUIRED_PASS_COUNT = 1; // 1 = immediate capture on first PASS. Relying on IRConfig=0.5 for anti-spoof.
    private static final float LIVENESS_SCORE_MINIMUM = 95f; // Secondary threshold: even if SDK says PASS, reject if score < this value

    /* リファクタリング: Broadcastアーキテクチャ追加 */
    private static final String ACTION_PROCESS_FACE = "com.bodycamera.ba.ACTION_PROCESS_FACE";
    private static final String ACTION_AUTH_RESULT = "com.bodycamera.ba.ACTION_AUTH_RESULT";
    private boolean mIsVerifying = false; // API応答待ち中はスキャンを一時停止するフラグ

    private final android.content.BroadcastReceiver authReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            String action = intent.getAction();
            if (ACTION_AUTH_RESULT.equals(action)) {
                boolean isSuccess = intent.getBooleanExtra("is_success", false);
                String message = intent.getStringExtra("message");
                
                Log.d(DEBUG_TAG, "★ [受信] ACTION_AUTH_RESULT: Success=" + isSuccess + ", Msg=" + message);
                
                if (isSuccess) {
                    Log.d(DEBUG_TAG, "★ [受信] 認証成功 → MakerApp終了");
                    Toast.makeText(FacePassActivity.this, "Authentication Success", Toast.LENGTH_SHORT).show();
                    finish(); // Maker App終了、以降はMain Appが処理する
                } else {
                    // 認証失敗 → リトライ
                    String displayMsg = message != null ? message : "Verification Failed";
                    final String finalMsg = displayMsg;
                    Log.d(DEBUG_TAG, "★ [受信] 認証失敗 → リトライ開始: " + finalMsg);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                             Toast.makeText(FacePassActivity.this, finalMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                    
                    // 状態リセット → 再スキャン許可
                    mIsVerifying = false;
                    mConsecutivePassCount = 0; // 連続PASS回数をリセット
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDetectResultQueue = new ArrayBlockingQueue<RecognizeData>(5);
        initAndroidHandler();

        // Intentからパラメータ取得
        Intent intent = getIntent();
        if (intent != null) {
            mServerUrl = intent.getStringExtra("server_url");
            mDeviceId = intent.getStringExtra("device_id");
            mPoliceId = intent.getStringExtra("police_id");
            Log.d(DEBUG_TAG, "★ [初期化] パラメータ受信: URL=" + mServerUrl + ", DeviceID=" + mDeviceId);
        }

        /* 初始化界面 */
        
        // BroadcastReceiver登録
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(ACTION_AUTH_RESULT);
        registerReceiver(authReceiver, filter);
        Log.d(DEBUG_TAG, "★ [初期化] BroadcastReceiver登録完了 (ACTION_AUTH_RESULT待機開始)");

        initView();


        mRecognizeThread = new RecognizeThread();
        mRecognizeThread.start();
        mFeedFrameThread = new FeedFrameThread();
        mFeedFrameThread.start();

        dbHelper = new DatabaseHelper(this);

        // 修正: 認証画面から直接起動された場合のSDK遅延初期化
        if (FacePassManager.mFacePassHandler == null) {
            Log.e(DEBUG_TAG, "!!! SDK not initialized. Initializing now from onCreate... !!!");
            FacePassManager.getInstance().init(this);
        } else {
            Log.d(DEBUG_TAG, "SDK already initialized. Handler: " + FacePassManager.mFacePassHandler);
            Log.d(DEBUG_TAG, "InitFinished State: " + FacePassManager.getInstance().isInitFinished);
            Log.d(DEBUG_TAG, "Group Status: isLocalGroupExist=" + FacePassManager.isLocalGroupExist);
        }
    }

    private void initAndroidHandler() {
        mAndroidHandler = new Handler();
    }


    @Override
    protected void onResume() {
        FacePassManager.getInstance().checkGroup(this);
        initToast();
        /* 打开相机 */
        if (hasPermission()) {
            manager.open(getWindowManager(), false, cameraWidth, cameraHeight);
            mIRCameraManager.open(getWindowManager(), true, cameraWidth, cameraHeight);
        } else {
            requestPermission();
        }
        adaptFrameLayout();
        super.onResume();
    }


    /* 判断程序是否有所需权限 android22以上需要自申请权限 */
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean result = checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_READ_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_WRITE_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_INTERNET) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED;
            return result;
        } else {
            return true;
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(Permission, PERMISSIONS_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    granted = false;
            }
            if (!granted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (!shouldShowRequestPermissionRationale(PERMISSION_CAMERA)
                            || !shouldShowRequestPermissionRationale(PERMISSION_READ_STORAGE)
                            || !shouldShowRequestPermissionRationale(PERMISSION_WRITE_STORAGE)
                            || !shouldShowRequestPermissionRationale(PERMISSION_INTERNET)
                            || !shouldShowRequestPermissionRationale(PERMISSION_ACCESS_NETWORK_STATE)) {
                         Toast.makeText(getApplicationContext(), "You need to enable permissions in settings", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                // Permissions granted, initialize SDK if needed or open camera
                if (manager != null) {
                    manager.open(getWindowManager(), false, cameraWidth, cameraHeight);
                    mIRCameraManager.open(getWindowManager(), true, cameraWidth, cameraHeight);
                }
                
                // Also retry init if it was skipped
                if (FacePassManager.mFacePassHandler == null) {
                     FacePassManager.getInstance().init(this);
                }
            }
        }
    }

    /* 相机回调函数 */
    @Override
    public void onPictureTaken(CameraPreviewData cameraPreviewData) {
        if (cameraPreviewData == null || cameraPreviewData.nv21Data == null) {
            return;
        }
        // 重要: カメラデータを直ちにクローンする
        byte[] clonedData = cameraPreviewData.nv21Data.clone();
        CameraPreviewData dataForHelper = new CameraPreviewData(
            clonedData, 
            cameraPreviewData.width, 
            cameraPreviewData.height, 
            cameraPreviewData.rotation, 
            cameraPreviewData.mirror
        );
        ComplexFrameHelper.addRgbFrame(dataForHelper);
    }

    private class FeedFrameThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                Pair<CameraPreviewData, CameraPreviewData> framePair;
                try {
                    framePair = ComplexFrameHelper.takeComplexFrame();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    continue;
                }

                if (FacePassManager.mFacePassHandler == null || !FacePassManager.getInstance().isInitFinished) {
                    if (System.currentTimeMillis() % 2000 < 50) { // Log occasionally to avoid spam
                        Log.e(DEBUG_TAG, "FeedFrameThread: Waiting for FacePassHandler and full initialization... isInitFinished=" + FacePassManager.getInstance().isInitFinished);
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    continue;
                }
                /* 将相机预览帧转成SDK算法所需帧的格式 FacePassImage */
                long startTime = System.currentTimeMillis(); //起始时间

                /* 将每一帧FacePassImage 送入SDK算法， 并得到返回结果 */
                FacePassTrackResult detectionResult = null;
                try {
                    FacePassConfig cfg = FacePassManager.mFacePassHandler.getConfig();
                    if (cfg.rgbIrLivenessEnabled) {
                        FacePassImage imageRGB = new FacePassImage(framePair.first.nv21Data, framePair.first.width, framePair.first.height, cameraRotation, FacePassImageType.NV21);
                        FacePassImage imageIR = new FacePassImage(framePair.second.nv21Data, framePair.second.width, framePair.second.height, cameraRotation, FacePassImageType.NV21);
                        detectionResult = FacePassManager.mFacePassHandler.feedFrameRGBIR(imageRGB, imageIR);
                    } else {
                        FacePassImage imageRGB = new FacePassImage(framePair.first.nv21Data, framePair.first.width, framePair.first.height, cameraRotation, FacePassImageType.NV21);
                        detectionResult = FacePassManager.mFacePassHandler.feedFrame(imageRGB);
                    }
                } catch (FacePassException e) {
                    e.printStackTrace();
                    continue;
                }


                if (detectionResult == null || detectionResult.trackedFaces.length == 0) {
                    /* 当前帧没有检出人脸 */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            faceView.clear();
                            faceView.invalidate();
                        }
                    });
                } else {
                    /* 将识别到的人脸在预览界面中圈出，并在上方显示人脸位置及角度信息 */
                    final FacePassTrackedFace[] bufferFaceList = detectionResult.trackedFaces;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (faceView != null) {
                                faceView.setVisibility(View.VISIBLE);
                                showFacePassFace(bufferFaceList);
                            }
                        }
                    });
                }

                if (FacePassManager.SDK_MODE == FacePassManager.FacePassSDKMode.MODE_OFFLINE) {
//                    Log.d(DEBUG_TAG, "detectionResult.message.length = " + detectionResult.message.length);
                    /*离线模式，将识别到人脸的，message不为空的result添加到处理队列中*/
                    if (detectionResult != null) {
                        /*所有检测到的人脸框的属性信息*/
                        for (int i = 0; i < detectionResult.trackedFaces.length; ++i) {
                            if (!detectionResult.trackedFaces[i].quality.facePassQualityCheck.isBlurPassed) {
                                Log.d(FD_DEBUG_TAG, "BlurScore = " + detectionResult.trackedFaces[i].quality.blur);
                            }
                            if (!detectionResult.trackedFaces[i].quality.facePassQualityCheck.isBrightnessPassd) {
                                Log.d(FD_DEBUG_TAG, "BrightnessScore = " + detectionResult.trackedFaces[i].quality.brightness);
                                Log.d(FD_DEBUG_TAG, "DeviationScore = " + detectionResult.trackedFaces[i].quality.deviation);
                            }
                            if (!detectionResult.trackedFaces[i].quality.facePassQualityCheck.isEdgefacePassed) {
                                Log.d(FD_DEBUG_TAG, "EdgefacePassedScore = " + detectionResult.trackedFaces[i].quality.edgefaceComp);
                            }
                            if (!detectionResult.trackedFaces[i].quality.facePassQualityCheck.isYawPassed ||
                                    !detectionResult.trackedFaces[i].quality.facePassQualityCheck.isPitchPassed ||
                                    !detectionResult.trackedFaces[i].quality.facePassQualityCheck.isRollPassed) {
                                Log.d(FD_DEBUG_TAG, "YawScore = " + detectionResult.trackedFaces[i].quality.pose.yaw);
                                Log.d(FD_DEBUG_TAG, "PitchScore = " + detectionResult.trackedFaces[i].quality.pose.pitch);
                                Log.d(FD_DEBUG_TAG, "RollScore = " + detectionResult.trackedFaces[i].quality.pose.roll);
                            }
                        }
                        Log.d(DEBUG_TAG, "--------------------------------------------------------------------------------------------------------------------------------------------------");
                        if (detectionResult.message.length != 0) {
                            /*送识别的人脸框的属性信息*/
                            FacePassTrackOptions[] trackOpts = new FacePassTrackOptions[detectionResult.images.length];
                            for (int i = 0; i < detectionResult.images.length; ++i) {
                                if (detectionResult.images[i].rcAttr.respiratorType != FacePassRCAttribute.FacePassRespiratorType.NO_RESPIRATOR) {
                                    float searchThreshold = 60f;
                                    float livenessThreshold = 88f; // -1.0f will not change the liveness threshold
                                    trackOpts[i] = new FacePassTrackOptions(detectionResult.images[i].trackId, searchThreshold, livenessThreshold);
                                } else {
                                    trackOpts[i] = new FacePassTrackOptions(detectionResult.images[i].trackId, -1f, -1f);
                                }
                                Log.d(DEBUG_TAG, String.format("rc attribute in FacePassImage, hairType: 0x%x beardType: 0x%x hatType: 0x%x respiratorType: 0x%x glassesType: 0x%x skinColorType: 0x%x",
                                        detectionResult.images[i].rcAttr.hairType.ordinal(),
                                        detectionResult.images[i].rcAttr.beardType.ordinal(),
                                        detectionResult.images[i].rcAttr.hatType.ordinal(),
                                        detectionResult.images[i].rcAttr.respiratorType.ordinal(),
                                        detectionResult.images[i].rcAttr.glassesType.ordinal(),
                                        detectionResult.images[i].rcAttr.skinColorType.ordinal()));
                            }
                            Log.d(DEBUG_TAG, "mRecognizeDataQueue.offer(mRecData);");
                            // Pass the NV21 data for potential capture
                            RecognizeData mRecData = new RecognizeData(detectionResult.message, trackOpts, framePair.first.nv21Data, framePair.first.width, framePair.first.height, startTime);
                            mDetectResultQueue.offer(mRecData);
                            Log.d(DEBUG_TAG, " startTime " + startTime);
                        }
                    }
                }
                long endTime = System.currentTimeMillis(); //结束时间
                long runTime = endTime - startTime;
                if (detectionResult == null) {
                    Log.d(DEBUG_TAG, "detectionResult == null");
                    continue;
                }
                for (int i = 0; i < detectionResult.trackedFaces.length; ++i) {
                    Log.i(DEBUG_TAG, "rect[" + i + "] = (" + detectionResult.trackedFaces[i].rect.left + ", " + detectionResult.trackedFaces[i].rect.top + ", " + detectionResult.trackedFaces[i].rect.right + ", " + detectionResult.trackedFaces[i].rect.bottom);
                }
                Log.i("]time", String.format("feedfream %d ms", runTime));
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }


    private class RecognizeThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                try {
                    // リファクタリング: API応答待ち中はスキャンを一時停止
                    if (mIsVerifying) {
                        Thread.sleep(100);
                        continue;
                    }
                    
                    RecognizeData recognizeData = mDetectResultQueue.take();
                    long startTime = recognizeData.startTime;
                    
                    // Wait if SDK is still initializing or group check not finished
                    while ((FacePassManager.mFacePassHandler == null || !FacePassManager.getInstance().isInitFinished) && !isInterrupt) {
                        Log.d(DEBUG_TAG, "RecognizeThread: SDK initialization not 100% finished, waiting...");
                        Thread.sleep(500);
                    }

                    Log.d(DEBUG_TAG, "RecognizeThread: Processing data. GroupExist=" + FacePassManager.isLocalGroupExist + ", Handler=" + (FacePassManager.mFacePassHandler != null));
                    
                    if (FacePassManager.mFacePassHandler != null && FacePassManager.isLocalGroupExist) {


					///////先活体再recognize
                        ////////////////////////// live ness test/ FP_REG_MODE_LIVENESS ////////////FP_REG_MODE_LIVENESSTRACK //////////
                        // Original livenessClassify call
                        boolean livenessOK = false; // Track if liveness passed for anti-spoof
                        FacePassLivenessResult[] livenessResult = FacePassManager.mFacePassHandler.livenessClassify(recognizeData.message, recognizeData.trackOpt[0].trackId, FacePassLivenessMode.FP_REG_MODE_LIVENESS, recognizeData.trackOpt[0].livenessThreshold);
                        if (null != livenessResult && livenessResult.length > 0) {
                            Log.d(DEBUG_TAG, "FacePassLivenessResult length = " + livenessResult.length);
                            for (FacePassLivenessResult result : livenessResult) {
                                String slivenessStat = " Unkonw";
                                switch (result.livenessState) {
                                    case 0:
                                        slivenessStat = "LIVENESS_PASS";
                                        
                                        // ★ Score log (printed BEFORE capture so it's always visible)
                                        Log.d(DEBUG_TAG, "★ LIVENESS_PASS Score: " + result.livenessScore + " (minimum: " + LIVENESS_SCORE_MINIMUM + ", threshold: " + result.livenessThreshold + ")");
                                        
                                        // Anti-spoof: スコアが最低基準を満たすかチェック
                                        if (result.livenessScore < LIVENESS_SCORE_MINIMUM) {
                                            Log.w(DEBUG_TAG, "★ Score TOO LOW: " + result.livenessScore + " < " + LIVENESS_SCORE_MINIMUM + " → REJECTED (possible photo)");
                                            break; // Treat as failed - don't increment pass count
                                        }
                                        
                                        livenessOK = true;
                                        
                                        // なりすまし安定化: trackIdに依存せず、連続PASS回数のみカウント
                                        mConsecutivePassCount++;
                                        
                                        if (mConsecutivePassCount < REQUIRED_PASS_COUNT) {
                                            Log.d(DEBUG_TAG, "Liveness: PASS (Count: " + mConsecutivePassCount + "/" + REQUIRED_PASS_COUNT + ") - Waiting for confirmation...");
                                            break; 
                                        }

                                        Log.d(DEBUG_TAG, "LIVENESS_PASS - 判定確定、画像キャプチャ開始");
                                        try {
                                            byte[] finalImageData = recognizeData.nv21Data;
                                            int finalWidth = recognizeData.width;
                                            int finalHeight = recognizeData.height;

                                            if (finalImageData == null || finalImageData.length < (finalWidth * finalHeight)) {
                                                Log.e(DEBUG_TAG, "Capture Error: データバッファが無効または空です");
                                                break;
                                            }

                                            // 1. YuvImageを使用してNV21からJPEGに変換
                                            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                                                    finalImageData,
                                                    android.graphics.ImageFormat.NV21,
                                                    finalWidth,
                                                    finalHeight,
                                                    null);
                                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                                            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, finalWidth, finalHeight), 100, out);
                                            byte[] jpegBytes = out.toByteArray();

                                            // 2. 回転処理のためにBitmapにデコード
                                            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
                                            if (bitmap == null) {
                                                Log.e(DEBUG_TAG, "Critical: JPEG Decode failed in memory!");
                                                break;
                                            }

                                            // 3. 回転処理 (270度)
                                            int finalRotation = 270;
                                            // 転送速度とタイムアウト防止のため、品質を80%に調整
                                            int jpegQuality = 100; // Maximized to avoid "Low Quality" API error

                                            if (finalRotation != 0) {
                                                android.graphics.Matrix matrix = new android.graphics.Matrix();
                                                matrix.postRotate(finalRotation);
                                                android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                                if (rotated != bitmap) {
                                                    bitmap.recycle();
                                                    bitmap = rotated;
                                                }
                                            }

                                            // 3.5 リサイズ処理 (最大1024px) - サーバー要件に合わせる
                                            int maxDim = 1024;
                                            int bw = bitmap.getWidth();
                                            int bh = bitmap.getHeight();
                                            if (bw > maxDim || bh > maxDim) {
                                                float ratio = (float) bw / (float) bh;
                                                int newW, newH;
                                                if (ratio > 1) {
                                                    newW = maxDim;
                                                    newH = (int) (maxDim / ratio);
                                                } else {
                                                    newH = maxDim;
                                                    newW = (int) (maxDim * ratio);
                                                }
                                                android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, newW, newH, true);
                                                if (scaled != bitmap) {
                                                    bitmap.recycle();
                                                    bitmap = scaled;
                                                }
                                                Log.d(DEBUG_TAG, "Resized: " + bw + "x" + bh + " → " + newW + "x" + newH);
                                            }

                                            // 4. 共有ディレクトリに保存 (Scoped Storage対策: 両アプリがアクセス可能な場所)
                                            String fileName = "face_" + System.currentTimeMillis() + ".jpg";
                                            java.io.File sharedDir = new java.io.File(
                                                android.os.Environment.getExternalStorageDirectory(), "FaceAuth");
                                            if (!sharedDir.exists()) {
                                                sharedDir.mkdirs();
                                            }
                                            
                                            java.io.File destFile = new java.io.File(sharedDir, fileName);
                                            
                                            java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                                            boolean compressOk = bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, jpegQuality, fos);
                                            fos.flush();
                                            fos.getFD().sync(); // 物理書き込みを確実に行う
                                            fos.close();

                                            if (!compressOk || destFile.length() == 0) {
                                                Log.e(DEBUG_TAG, "Failed to compress bitmap to file!");
                                                break;
                                            }

                                            // 5. FileProviderを介してコンテンツURIを返す (Android 11+ 対策)
                                            final String finalPath = destFile.getAbsolutePath();
                                            final int finalW = bitmap.getWidth();
                                            final int finalH = bitmap.getHeight();

                                            Log.d(DEBUG_TAG, "★ [キャプチャ成功] 保存先: " + finalPath + " [" + finalW + "x" + finalH + "] (" + (destFile.length()/1024) + "KB)");;

                                            final long finalFileSize = destFile.length();
                                            mAndroidHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(FacePassActivity.this, "FINALLY: " + finalW + "x" + finalH + " (" + (finalFileSize/1024) + "KB)", Toast.LENGTH_LONG).show();
                                                }
                                            });

                                            // ========================================
                                            // リファクタリング: Main Appへ画像パスをBroadcast送信
                                            // ========================================
                                            
                                            // 1. スキャン一時停止
                                            mIsVerifying = true;
                                            
                                            // 2. Broadcast送信
                                            Log.d(DEBUG_TAG, "★ [送信] ACTION_PROCESS_FACE → Main App: " + finalPath);
                                            android.content.Intent intent = new android.content.Intent(ACTION_PROCESS_FACE);
                                            intent.putExtra("image_path", finalPath);
                                            sendBroadcast(intent);
                                            
                                            // 3. UIフィードバック表示
                                            mAndroidHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(FacePassActivity.this, "Verifying...", Toast.LENGTH_SHORT).show();
                                                }
                                            });

                                            // 4. 検証結果待ち（mIsVerifyingがReceiverによりリセットされるまで待機）
                                            // ループ先頭のmIsVerifyingチェックでスキップされる
                                            
                                            // 古いフレームをくリアして再処理を防止
                                            mDetectResultQueue.clear(); 
                                            mConsecutivePassCount = 0; // 失敗時の次回に備えてリセット
                                            
                                            Log.d(DEBUG_TAG, "★ [待機] API応答待ち開始 (mIsVerifying=true)");
                                        } catch (Exception e) {
                                            Log.e(DEBUG_TAG, "Ultimate Capture Failure", e);
                                        }
                                        break;
                                    case 1:
                                        slivenessStat = "LIVENESS_RETRY";
                                        break;
                                    case 2:
                                        slivenessStat = "LIVENESS_RETRY_EXPIRED";
                                        break;
                                    case 3:
                                        slivenessStat = "LIVENESS_TRACK_MISSING";
                                        break;
                                    case 4:
                                        slivenessStat = "LIVENESS_UNPASS";
                                        if (mConsecutivePassCount > 0) mConsecutivePassCount--; // デクリメント（リセットではなく減算）
                                        break;
                                    default:
                                        break;
                                }

                                Log.d(DEBUG_TAG, "FacePassLivenessResult: trackId: " + result.trackId
                                        + ", livenessScore: " + result.livenessScore
                                        + ", livenessThreshold: " + result.livenessThreshold
                                        + ", livenessState: " + slivenessStat + ", livenessState: " + result.livenessState);
                            }
                        }

                        // 生体判定がパスした場合のみ認証実行（なりすまし防止・写真攻撃防止）
                        if (livenessOK) {
    						Log.d(DEBUG_TAG, "mDetectResultQueue.recognize");
                        	FacePassRecognitionResult[] recognizeResult = FacePassManager.mFacePassHandler.recognize(FacePassManager.group_name, recognizeData.message, 1, recognizeData.trackOpt[0].trackId, FacePassRecogMode.FP_REG_MODE_FEAT_COMP, -1.0F, -1.0F);

                        	if (recognizeResult != null && recognizeResult.length > 0) {
                        	Log.d(DEBUG_TAG, "FacePassRecognitionResult length = " + recognizeResult.length);
                            for (FacePassRecognitionResult result : recognizeResult) {
                            	if (null == result.faceToken) {
                            		Log.d(DEBUG_TAG, "result.faceToken is null.");
                                	continue;
                            	}
                                String faceToken = new String(result.faceToken);
                                Log.d(DEBUG_TAG, "FacePassRecognitionState.RECOGNITION_PASS = " + result.recognitionState);
                                if (FacePassRecognitionState.RECOGNITION_PASS == result.recognitionState) {
                                	getFaceImageByFaceToken(result.trackId, faceToken);
                                }
                                showRecognizeResult(result.trackId, result.detail.searchScore, result.detail.livenessScore, !TextUtils.isEmpty(faceToken));
                            }
                            }
                        } else {
                            Log.d(DEBUG_TAG, "Liveness FAILED - skipping recognize to prevent freeze. Continuing...");
                        }



                        ////////////////////////////////////////////////////////////
//                        FacePassRecognitionResult[] recognizeResult = FacePassManager.mFacePassHandler.recognize(FacePassManager.group_name, recognizeData.message, 1, recognizeData.trackOpt[0].trackId, FacePassRecogMode.FP_REG_MODE_DEFAULT, recognizeData.trackOpt[0].livenessThreshold, recognizeData.trackOpt[0].searchThreshold);
//                        long endTime = System.currentTimeMillis(); //结束时间
//                        long runTime = endTime - startTime;
//                        Log.i("]time", String.format("ppl time %d ms", runTime));
//                        if (recognizeResult != null && recognizeResult.length > 0) {
//                            Log.d(DEBUG_TAG, "FacePassRecognitionResult length = " + recognizeResult.length);
//                            for (FacePassRecognitionResult result : recognizeResult) {
//                                if (null == result.faceToken) {
//                                    Log.d(DEBUG_TAG, "result.faceToken is null.");
//                                    continue;
//                                }
//                                String faceToken = new String(result.faceToken);
//                                Log.d(DEBUG_TAG, "FacePassRecognitionState.RECOGNITION_PASS = " + result.recognitionState);
//                                if (FacePassRecognitionState.RECOGNITION_PASS == result.recognitionState) {
//                                    getFaceImageByFaceToken(result.trackId, faceToken);
//                                }
//                                showRecognizeResult(result.trackId, result.detail.searchScore, result.detail.livenessScore, result.recognitionState == 0);
//                            }
//                        }
                    }
                } catch (InterruptedException e) {
                    Log.e(DEBUG_TAG, "RecognizeThread Interrupted", e);
                } catch (FacePassException e) {
                    Log.e(DEBUG_TAG, "FacePassException in RecognizeThread", e);
                } catch (Exception e) {
                    Log.e(DEBUG_TAG, "Unexpected error in RecognizeThread", e);
                }
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    private void showRecognizeResult(final long trackId, final float searchScore, final float livenessScore, final boolean isRecognizeOK) {
        mAndroidHandler.post(new Runnable() {
            @Override
            public void run() {
                faceEndTextView.append("ID = " + trackId + (isRecognizeOK ? "识别成功" : "识别失败") + "\n");
                faceEndTextView.append("识别分 = " + searchScore + "\n");
                faceEndTextView.append("活体分 = " + livenessScore + "\n");
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });

    }


    private void adaptFrameLayout() {
        SettingVar.isButtonInvisible = false;
        SettingVar.iscameraNeedConfig = false;
    }

    private void initToast() {
        SettingVar.isButtonInvisible = false;
    }

    private void initView() {


//        int windowRotation = ((WindowManager) (getApplicationContext().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getRotation() * 90;
//        if (windowRotation == 0) {
//            cameraRotation = FacePassImageRotation.DEG90;
//        } else if (windowRotation == 90) {
//            cameraRotation = FacePassImageRotation.DEG0;
//        } else if (windowRotation == 270) {
//            cameraRotation = FacePassImageRotation.DEG180;
//        } else {
//            cameraRotation = FacePassImageRotation.DEG270;
//        }
        Log.i(DEBUG_TAG, "cameraRation: " + cameraRotation);
        cameraFacingFront = true;
        SharedPreferences preferences = getSharedPreferences(SettingVar.SharedPrefrence, Context.MODE_PRIVATE);
        SettingVar.isSettingAvailable = preferences.getBoolean("isSettingAvailable", SettingVar.isSettingAvailable);
        SettingVar.isCross = preferences.getBoolean("isCross", SettingVar.isCross);
        SettingVar.faceRotation = preferences.getInt("faceRotation", SettingVar.faceRotation);
        SettingVar.cameraPreviewRotation = preferences.getInt("cameraPreviewRotation", SettingVar.cameraPreviewRotation);
        SettingVar.cameraFacingFront = preferences.getBoolean("cameraFacingFront", SettingVar.cameraFacingFront);
        if (SettingVar.isSettingAvailable) {
            cameraRotation = SettingVar.faceRotation;
            cameraFacingFront = SettingVar.cameraFacingFront;
        }


//        Log.i("orientation", String.valueOf(windowRotation));
        final int mCurrentOrientation = getResources().getConfiguration().orientation;

        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            screenState = 1;
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            screenState = 0;
        }
        setContentView(R.layout.activity_facepass);


        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        heightPixels = displayMetrics.heightPixels;
        widthPixels = displayMetrics.widthPixels;
        SettingVar.mHeight = heightPixels;
        SettingVar.mWidth = widthPixels;
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        AssetManager mgr = getAssets();
        Typeface tf = Typeface.createFromAsset(mgr, "fonts/Univers LT 57 Condensed.ttf");
        /* 初始化界面 */
        faceEndTextView = (TextView) this.findViewById(R.id.tv_meg2);
        faceEndTextView.setTypeface(tf);
        faceView = (FaceView) this.findViewById(R.id.fcview);
        settingButton = (Button) this.findViewById(R.id.settingid);
        settingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long curTime = System.currentTimeMillis();
                long durTime = curTime - mLastClickTime;
                mLastClickTime = curTime;
                if (durTime < CLICK_INTERVAL) {
                    ++mSecretNumber;
                    if (mSecretNumber == 5) {
                        Intent intent = new Intent(FacePassActivity.this, SettingActivity.class);
                        startActivity(intent);
                        FacePassActivity.this.finish();
                    }
                } else {
                    mSecretNumber = 0;
                }
            }
        });
        SettingVar.cameraSettingOk = false;
        ll = (LinearLayout) this.findViewById(R.id.ll);
        ll.getBackground().setAlpha(100);

        manager = new CameraManager();
        mIRCameraManager = new CameraManager();

        cameraView = (CameraPreview) findViewById(R.id.preview);
        mIRCameraView = (CameraPreview) findViewById(R.id.preview2);

        manager.setPreviewDisplay(cameraView);
        mIRCameraManager.setPreviewDisplay(mIRCameraView);

        frameLayout = (FrameLayout) findViewById(R.id.frame);
        /* 注册相机回调函数 */
        manager.setListener(this);
        
        // Ensure faceView is on top and visible
        if (faceView != null) {
            faceView.bringToFront();
            faceView.setVisibility(View.VISIBLE);
        }
        
        mIRCameraManager.setListener(new CameraManager.CameraListener() {
            @Override
            public void onPictureTaken(CameraPreviewData cameraPreviewData) {
                if (cameraPreviewData == null || cameraPreviewData.nv21Data == null) {
                    return;
                }
                // 重要: 赤外線カメラデータもクローンする
                byte[] clonedData = cameraPreviewData.nv21Data.clone();
                CameraPreviewData dataForHelper = new CameraPreviewData(
                    clonedData, 
                    cameraPreviewData.width, 
                    cameraPreviewData.height, 
                    cameraPreviewData.rotation, 
                    cameraPreviewData.mirror
                );
                ComplexFrameHelper.addIRFrame(dataForHelper);
            }
        });

        findViewById(R.id.ivGoBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }


    @Override
    protected void onStop() {
        SettingVar.isButtonInvisible = false;
        mDetectResultQueue.clear();
        ComplexFrameHelper.clearBuffers();
        if (manager != null) {
            manager.release();
        }
        if (mIRCameraManager != null) {
            mIRCameraManager.release();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        mRecognizeThread.isInterrupt = true;
        mFeedFrameThread.isInterrupt = true;

        mRecognizeThread.interrupt();
        mFeedFrameThread.interrupt();

        if (manager != null) {
            manager.release();
        }
        if (mIRCameraManager != null) {
            mIRCameraManager.release();
        }
        try {
            unregisterReceiver(authReceiver);
        } catch (Exception e) {
            // Receiver might not be registered
        }

        if (mAndroidHandler != null) {
            mAndroidHandler.removeCallbacksAndMessages(null);
        }

        // Release SDK to prevent nativeHandle is null error on restart
        if (FacePassManager.mFacePassHandler != null) {
            FacePassManager.mFacePassHandler.release();
            FacePassManager.mFacePassHandler = null;
            Log.d(DEBUG_TAG, "FacePassActivity.onDestroy: Released SDK.");
        }
        FacePassManager.getInstance().isInitFinished = false;

        super.onDestroy();
    }


    private void showFacePassFace(FacePassTrackedFace[] detectResult) {
        faceView.clear();
        for (FacePassTrackedFace face : detectResult) {
            Log.d("facefacelist", "width " + (face.rect.right - face.rect.left) + " height " + (face.rect.bottom - face.rect.top));
//            Log.d("facefacelist", "smile " + face.smile);
            boolean mirror = cameraFacingFront; /* 前摄像头时mirror为true */
            StringBuilder faceIdString = new StringBuilder();
            String name = face.trackId + "";
            faceIdString.append("ID = ").append(name);
            SpannableString faceViewString = new SpannableString(faceIdString);
            faceViewString.setSpan(new TypefaceSpan("fonts/kai"), 0, faceViewString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            StringBuilder faceRollString = new StringBuilder();
            faceRollString.append("旋转: ").append((int) face.quality.pose.roll).append("°");
            StringBuilder facePitchString = new StringBuilder();
            facePitchString.append("上下: ").append((int) face.quality.pose.pitch).append("°");
            StringBuilder faceYawString = new StringBuilder();
            faceYawString.append("左右: ").append((int) face.quality.pose.yaw).append("°");
            StringBuilder faceBlurString = new StringBuilder();
            faceBlurString.append("模糊: ").append(face.quality.blur);
            StringBuilder smileString = new StringBuilder();
//            smileString.append("微笑: ").append(String.format("%.6f", face.smile));
            Matrix mat = new Matrix();
            int w = cameraView.getMeasuredWidth();
            int h = cameraView.getMeasuredHeight();

            int cameraHeight = manager.getCameraheight();
            int cameraWidth = manager.getCameraWidth();

            float left = 0;
            float top = 0;
            float right = 0;
            float bottom = 0;
            switch (cameraRotation) {
                case 0:
                    left = face.rect.left;
                    top = face.rect.top;
                    right = face.rect.right;
                    bottom = face.rect.bottom;
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraWidth : 0f, 0f);
                    mat.postScale((float) w / (float) cameraWidth, (float) h / (float) cameraHeight);
                    break;
                case 90:
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraHeight : 0f, 0f);
                    mat.postScale((float) w / (float) cameraHeight, (float) h / (float) cameraWidth);
                    left = face.rect.top;
                    top = cameraWidth - face.rect.right;
                    right = face.rect.bottom;
                    bottom = cameraWidth - face.rect.left;
                    break;
                case 180:
                    mat.setScale(1, mirror ? -1 : 1);
                    mat.postTranslate(0f, mirror ? (float) cameraHeight : 0f);
                    mat.postScale((float) w / (float) cameraWidth, (float) h / (float) cameraHeight);
                    left = face.rect.right;
                    top = face.rect.bottom;
                    right = face.rect.left;
                    bottom = face.rect.top;
                    break;
                case 270:
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraHeight : 0f, 0f);
                    mat.postScale((float) w / (float) cameraHeight, (float) h / (float) cameraWidth);
                    left = cameraHeight - face.rect.bottom;
                    top = face.rect.left;
                    right = cameraHeight - face.rect.top;
                    bottom = face.rect.right;
            }

            RectF drect = new RectF();
            RectF srect = new RectF(left, top, right, bottom);

            mat.mapRect(drect, srect);
            faceView.addRect(drect);
            faceView.addId(faceIdString.toString());
            faceView.addRoll(faceRollString.toString());
            faceView.addPitch(facePitchString.toString());
            faceView.addYaw(faceYawString.toString());
            faceView.addBlur(faceBlurString.toString());
            faceView.addSmile(smileString.toString());
        }
        faceView.invalidate();
    }

    public void showToast(CharSequence text, int duration, boolean isSuccess, Bitmap bitmap) {
        LayoutInflater inflater = getLayoutInflater();
        View toastView = inflater.inflate(R.layout.toast, null);
        LinearLayout toastLLayout = (LinearLayout) toastView.findViewById(R.id.toastll);
        if (toastLLayout == null) {
            return;
        }
        toastLLayout.getBackground().setAlpha(100);
        ImageView imageView = (ImageView) toastView.findViewById(R.id.toastImageView);
        TextView idTextView = (TextView) toastView.findViewById(R.id.toastTextView);
        TextView stateView = (TextView) toastView.findViewById(R.id.toastState);
        SpannableString s;
        if (isSuccess) {
            s = new SpannableString("验证成功");
            imageView.setImageResource(R.drawable.success);
        } else {
            s = new SpannableString("验证失败");
            imageView.setImageResource(R.drawable.success);
        }
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
        stateView.setText(s);
        idTextView.setText(text);

        if (mRecoToast == null) {
            mRecoToast = new Toast(getApplicationContext());
            mRecoToast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        }
        mRecoToast.setDuration(duration);
        mRecoToast.setView(toastView);

        mRecoToast.show();
    }

    private static final int REQUEST_CODE_CHOOSE_PICK = 1;




    private void getFaceImageByFaceToken(final long trackId, final String faceToken) {
        if (TextUtils.isEmpty(faceToken)) {
            return;
        }

        try {
            final Bitmap bitmap = FacePassManager.mFacePassHandler.getFaceImage(faceToken.getBytes());
            mAndroidHandler.post(new Runnable() {
                @Override
                public void run() {
                    String name = dbHelper.findName(faceToken);
                    Log.i(DEBUG_TAG, "getFaceImageByFaceToken:showToast");
                    showToast("姓名 = " + name, Toast.LENGTH_SHORT, true, bitmap);
                }
            });
            if (bitmap != null) {
                return;
            }
        } catch (FacePassException e) {
            e.printStackTrace();
        }
    }



    public class RecognizeData {
        public byte[] message;
        public FacePassTrackOptions[] trackOpt;
        public byte[] nv21Data;
        public int width;
        public int height;
        public long startTime;

        public RecognizeData(byte[] message) {
            this.message = message;
            this.trackOpt = null;
        }

        // Updated Constructor to include image data
        public RecognizeData(byte[] message, FacePassTrackOptions[] opt, byte[] nv21Data, int width, int height, long startTime) {
            this.message = message;
            this.trackOpt = opt;
            this.nv21Data = nv21Data;
            this.width = width;
            this.height = height;
            this.startTime = startTime;
        }

        public RecognizeData(byte[] message, FacePassTrackOptions[] opt) {
            this.message = message;
            this.trackOpt = opt;
        }
    }

}
