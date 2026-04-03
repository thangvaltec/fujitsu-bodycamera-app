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

    // Intent Pass-through Params for Smart Retry
    private String mServerUrl;
    private String mDeviceId;
    private String mPoliceId;

    // 既存フィールド

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
    private Toast mErrorToast; // エラーToast参照 (成功時にキャンセルするため)

    /*DetectResult queue*/
    ArrayBlockingQueue<RecognizeData> mDetectResultQueue;

    /*recognize thread*/
    RecognizeThread mRecognizeThread;
    FeedFrameThread mFeedFrameThread;


    private Handler mAndroidHandler;

    private DatabaseHelper dbHelper;

    /* なりすまし判定の安定化用 */
    // private long mLastPassTrackId = -1; // Unused for now
    // private int mConsecutivePassCount = 0;
    // private static final int REQUIRED_PASS_COUNT = 1; // 1 = immediate capture on first PASS. Relying on IRConfig=0.5 for anti-spoof.
    // private static final float LIVENESS_SCORE_MINIMUM = 95f; // Secondary threshold: even if SDK says PASS, reject if score < this value

    /* リファクタリング: Broadcastアーキテクチャ追加 */
    private static final String ACTION_PROCESS_FACE = "com.bodycamera.ba.ACTION_PROCESS_FACE";
    private static final String ACTION_AUTH_RESULT = "com.bodycamera.ba.ACTION_AUTH_RESULT";
    private boolean mIsVerifying = false; // API応答待ち中はスキャンを一時停止するフラグ
    private boolean mPendingFinish = false; // ★ UI kẹt 0s Fix: Cờ chờ đóng màn hình an toàn

    private void safeFinish() {
        if (hasWindowFocus()) {
            Log.d(DEBUG_TAG, "★ safeFinish: Window has focus. Finishing immediately.");
            finish();
        } else {
            Log.w(DEBUG_TAG, "★ safeFinish: Window NOT focused yet! Postponing finish() to prevent rapid transition crash.");
            mPendingFinish = true;
        }
    }

    private final android.content.BroadcastReceiver authReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            String action = intent.getAction();
            if (ACTION_AUTH_RESULT.equals(action)) {
                boolean isSuccess = intent.getBooleanExtra("is_success", false);
                String message = intent.getStringExtra("message");
                
                Log.d(DEBUG_TAG, "★ 受信 ACTION_AUTH_RESULT: Success=" + isSuccess + ", Message=" + message);
                
                // 追加: APIからの生JSONログを表示
                String rawJson = intent.getStringExtra("api_response_json");
                if (rawJson != null) {
                    Log.d(DEBUG_TAG, "★ 受信 API Raw Response: " + rawJson);
                }
                
                // Allow restart scan if failed
                if (!isSuccess) {
                     mIsVerifying = false;
                }
                
                if (isSuccess) {
                    Log.d(DEBUG_TAG, "★受信認証成功 → MakerApp終了");
                    // エラーToastが残っている場合はキャンセル
                    if (mErrorToast != null) {
                        mErrorToast.cancel();
                        mErrorToast = null;
                    }
                    // Toast.makeText(FacePassActivity.this, "Authentication Success", Toast.LENGTH_SHORT).show();
                    safeFinish(); // Maker App終了、以降はMain Appが処理する
                } else {
                    // 認証失敗 → リトライ
                    String displayMsg = message != null ? message : "Verification Failed";
                    final String finalMsg = displayMsg;
                    Log.d(DEBUG_TAG, "★受信認証失敗 → リトライ開始: " + finalMsg);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // カスタムエラーToast (大きい赤文字)
                            android.widget.LinearLayout layout = new android.widget.LinearLayout(FacePassActivity.this);
                            layout.setOrientation(android.widget.LinearLayout.VERTICAL);
                            layout.setPadding(50, 30, 50, 30);
                            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
                            bg.setColor(android.graphics.Color.argb(200, 30, 30, 30));
                            bg.setCornerRadius(20);
                            layout.setBackground(bg);

                            // タイトル「認証失敗」
                            TextView titleView = new TextView(FacePassActivity.this);
                            titleView.setText("認証失敗");
                            titleView.setTextSize(28);
                            titleView.setTextColor(android.graphics.Color.parseColor("#FF4444"));
                            titleView.setGravity(android.view.Gravity.CENTER);
                            titleView.setTypeface(null, android.graphics.Typeface.BOLD);
                            layout.addView(titleView);

                            // 詳細メッセージ (「認証失敗」重複を除去)
                            String detailMsg = finalMsg.replace("認証失敗", "").replace("\n", "").trim();
                            if (!detailMsg.isEmpty()) {
                                TextView msgView = new TextView(FacePassActivity.this);
                                msgView.setText(detailMsg);
                                msgView.setTextSize(22);
                                msgView.setTextColor(android.graphics.Color.parseColor("#FFFFFF"));
                                msgView.setGravity(android.view.Gravity.CENTER);
                                msgView.setPadding(20, 10, 20, 0);
                                layout.addView(msgView);
                            }

                            mErrorToast = new Toast(FacePassActivity.this);
                            mErrorToast.setGravity(android.view.Gravity.BOTTOM, 0, 150);
                            mErrorToast.setDuration(Toast.LENGTH_SHORT);
                            mErrorToast.setView(layout);
                            mErrorToast.show();

                            // カスタム表示時間 (1500ms = 1.5秒後に自動キャンセル)
                            mAndroidHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    if (mErrorToast != null) {
                                        mErrorToast.cancel();
                                        mErrorToast = null;
                                    }
                                }
                            }, 1500);
                        }
                    });
                    
                    // 状態リセット → 再スキャン許可
                    mIsVerifying = false;
                    // mConsecutivePassCount = 0; // 連続PASS回数をリセット
                }
            }
        }
    };


    private boolean mUseTopKMode = false; // Flag to enable TopK flow
    private int mTopKCount = 1;
    private float mRecognizeThreshold = 60.0f;
    private boolean mLivenessEnabled = true;

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
            mUseTopKMode = intent.getBooleanExtra("should_use_topk", false);
            mTopKCount = intent.getIntExtra("top_k_count", 1);
            mRecognizeThreshold = intent.getFloatExtra("recognition_threshold", 60.0f);
            
            float threshold = intent.getFloatExtra("liveness_threshold", 88.0f);
            FacePassManager.LIVENESS_THRESHOLD = threshold; // Apply global setting
            
            mLivenessEnabled = intent.getBooleanExtra("liveness_enabled", true);
            ComplexFrameHelper.isLivenessEnabled = mLivenessEnabled;
            
            int faceMinThreshold = intent.getIntExtra("face_min_threshold", 100);
            FacePassManager.FACE_MIN_THRESHOLD = faceMinThreshold;
            
            Log.d(DEBUG_TAG, "★ パラメータ受信: URL=" + mServerUrl + ", DeviceID=" + mDeviceId + ", UseTopK=" + mUseTopKMode + ", LivenessThresh=" + threshold + ", LivenessEnabled=" + mLivenessEnabled + ", FaceMinThresh=" + faceMinThreshold + ", TopKCount=" + mTopKCount + ", RecogThresh=" + mRecognizeThreshold);
        }

        /* 初始化界面 */
        
        // BroadcastReceiver登録
        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(ACTION_AUTH_RESULT);
        registerReceiver(authReceiver, filter);
        Log.d(DEBUG_TAG, "★  BroadcastReceiver登録完了 (ACTION_AUTH_RESULT待機開始)");

        initView();


        mRecognizeThread = new RecognizeThread();
        mRecognizeThread.start();
        mFeedFrameThread = new FeedFrameThread();
        mFeedFrameThread.start();

        dbHelper = new DatabaseHelper(this);

        // SDKの遅延初期化または設定更新
        if (FacePassManager.mFacePassHandler == null) {
            Log.e(DEBUG_TAG, "!!! SDK not initialized. Initializing now from onCreate... !!!");
            FacePassManager.getInstance().init(this);
        } else {
            Log.d(DEBUG_TAG, "SDK already initialized. Updating config...");
            // SDKが既に初期化されている場合、現在の設定（Liveness ON/OFF）をアルゴリズムに反映させる
            try {
                FacePassConfig config = FacePassManager.mFacePassHandler.getConfig();
                if (config != null) {
                    config.rgbIrLivenessEnabled = mLivenessEnabled;
                    FacePassManager.mFacePassHandler.setConfig(config);
                    Log.d(DEBUG_TAG, "SDK Config updated: rgbIrLivenessEnabled=" + mLivenessEnabled);
                }
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "Error updating SDK config: " + e.getMessage());
                e.printStackTrace();
            }
            Log.d(DEBUG_TAG, "InitFinished State: " + FacePassManager.getInstance().isInitFinished);
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
            if (mLivenessEnabled) {
                mIRCameraManager.open(getWindowManager(), true, cameraWidth, cameraHeight);
            } else {
                Log.d(DEBUG_TAG, "★ Liveness disabled: Skipping IR camera open.");
            }
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
                    if (mLivenessEnabled) {
                        mIRCameraManager.open(getWindowManager(), true, cameraWidth, cameraHeight);
                    }
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
                    if (mLivenessEnabled) {
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

                    /* ★ 手動距離フィルタリング (Identification Distance Setting) 
                     * SDKの初期化設定(FaceMinThreshold)はシングルトン保持のため、実行時に動的に変更できない場合がある。
                     * そのため、SDKから返された結果をこちらで手動でフィルタリングし、設定より遠い（小さい）顔を無視する。
                     * 注意：SDKが返した detectionResult の配列（images, trackedFaces）を直接書き換えると、
                     * JNI層のメモリ参照が壊れ Liveness 判定が失敗 (-1.0) するため、論理的なフィルタリングのみを行う。
                     */
                    java.util.List<FacePassTrackedFace> visibleFaces = new java.util.ArrayList<>();
                    boolean hasAcceptableFace = false;
                    
                    if (detectionResult == null) continue;

                    if (detectionResult.trackedFaces != null) {
                        for (FacePassTrackedFace face : detectionResult.trackedFaces) {
                            int faceWidth = (int) (face.rect.right - face.rect.left);
                            int faceHeight = (int) (face.rect.bottom - face.rect.top);
                            int faceSize = Math.max(faceWidth, faceHeight);

                            boolean isAccepted = faceSize >= FacePassManager.FACE_MIN_THRESHOLD;
                            if (isAccepted) {
                                visibleFaces.add(face);
                                hasAcceptableFace = true;
                            }
                            
                            Log.d("FaceDistanceDebug", String.format("trackId: %d, size: %d, threshold: %d -> %s", 
                                face.trackId, faceSize, FacePassManager.FACE_MIN_THRESHOLD, isAccepted ? "ACCEPT" : "REJECT"));
                        }
                    }

                    /* 1. UI表示の制御: 設定距離内の顔のみ枠を描画する */
                    if (visibleFaces.isEmpty()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (faceView != null) {
                                    faceView.clear();
                                    faceView.invalidate();
                                }
                            }
                        });
                    } else {
                        final FacePassTrackedFace[] bufferFaceList = visibleFaces.toArray(new FacePassTrackedFace[0]);
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

                    /* 2. 認識処理の制御: 設定距離内に顔がある場合のみキューへ追加する */
                    if (hasAcceptableFace && detectionResult.message.length != 0) {
                        FacePassTrackOptions[] trackOpts = new FacePassTrackOptions[detectionResult.images.length];
                        for (int i = 0; i < detectionResult.images.length; ++i) {
                            if (detectionResult.images[i].rcAttr.respiratorType != FacePassRCAttribute.FacePassRespiratorType.NO_RESPIRATOR) {
                                float searchThreshold = 60f;
                                trackOpts[i] = new FacePassTrackOptions(detectionResult.images[i].trackId, searchThreshold, FacePassManager.LIVENESS_THRESHOLD);
                            } else {
                                trackOpts[i] = new FacePassTrackOptions(detectionResult.images[i].trackId, -1f, FacePassManager.LIVENESS_THRESHOLD);
                            }
                        }
                        
                        RecognizeData mRecData = new RecognizeData(detectionResult.message, trackOpts, framePair.first.nv21Data, framePair.first.width, framePair.first.height, startTime);
                        mDetectResultQueue.offer(mRecData);
                        Log.d("FaceDistanceDebug", "Frame offered to queue (Face within distance)");
                    } else {
                        // 距離外の場合はキューに追加せずスキップ
                        if (detectionResult.trackedFaces != null && detectionResult.trackedFaces.length > 0) {
                           Log.d("FaceDistanceDebug", "Frame rejected (Face too far)");
                        }
                    }
                long endTime = System.currentTimeMillis(); //结束时间
                long runTime = endTime - startTime;
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
                        // ライブネス判定（なりすまし防止・写真攻撃防止）
                        // mLivenessEnabled=true の場合: SDKのlivenessClassifyを呼び出してスコア検証
                        // mLivenessEnabled=false の場合: 判定をスキップし、即時認証へ進む
                        boolean livenessOK = false; // ライブネス判定の通過フラグ

                        if (mLivenessEnabled) {
                            FacePassLivenessResult[] livenessResult = FacePassManager.mFacePassHandler.livenessClassify(recognizeData.message, recognizeData.trackOpt[0].trackId, FacePassLivenessMode.FP_REG_MODE_LIVENESS, recognizeData.trackOpt[0].livenessThreshold);
                            if (null != livenessResult && livenessResult.length > 0) {
                                Log.d(DEBUG_TAG, "FacePassLivenessResult length = " + livenessResult.length);
                                for (FacePassLivenessResult result : livenessResult) {
                                    String slivenessStat = " Unkonw";
                                    switch (result.livenessState) {
                                        case 0:
                                            slivenessStat = "LIVENESS_PASS";
                                            
                                            // ★ Score log (printed BEFORE capture so it's always visible)
                                            Log.d(DEBUG_TAG, "★ LIVENESS_PASS Score: " + result.livenessScore + " (threshold: " + result.livenessThreshold + ")");
                                            livenessOK = true;
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
                        } else {
                            // ライブネス無効設定: スコアチェックをスキップして即時OK扱い
                            Log.d(DEBUG_TAG, "★ ライブネス無効: スコアチェックをスキップします。");
                            livenessOK = true;
                        }

                        // 生体判定がパスした場合のみ認証実行（なりすまし防止・写真攻撃防止）
                        if (livenessOK) {
                            Log.d(DEBUG_TAG, "═══════════════════════════════════════════");
                            Log.d(DEBUG_TAG, "★ [TopK] Liveness PASSED → Recognize...");

                            // 認証モード切替: Top-Kモード（ローカルサーバ）またはクラウドサーバ
                            if (mUseTopKMode) {
                                // Top-Kモード: ローカルデータベースから上位K人の候補を取得
                                Log.d(DEBUG_TAG, "★ [TopKモード] ローカル識別を開始...");

                                int topK = mTopKCount;
                                float scoreFilter = mRecognizeThreshold;
                                Log.d(DEBUG_TAG, "  TopK=" + topK + ", ScoreFilter>=" + scoreFilter);

                                // Maker SDK準拠: trackOptから閾値を取得（マスク検知対応）
                                FacePassRecognitionResult[] candidates = FacePassManager.mFacePassHandler.recognize(
                                        FacePassManager.group_name,
                                        recognizeData.message,
                                        topK,
                                        recognizeData.trackOpt[0].trackId,
                                        FacePassRecogMode.FP_REG_MODE_DEFAULT,
                                        recognizeData.trackOpt[0].livenessThreshold,
                                        recognizeData.trackOpt[0].searchThreshold
                                );

                                java.util.ArrayList<String> candidateList = new java.util.ArrayList<>();
                                // ★ Bug2修正: 候補IDリストと同順で名前を格納するリスト（TopActivityでのID→名前解決に使用）
                                java.util.ArrayList<String> candidateNames = new java.util.ArrayList<>();
                                
                                if (candidates != null && candidates.length > 0) {
                                    Log.d(DEBUG_TAG, "★ [TopK] SDK Return: " + candidates.length + " candidates");
                                    int idx = 0;
                                // ★ Bug1修正: Toastキューによる名前ズレを防ぐため、Top1候補にのみToastを表示するフラグ
                                boolean isToastShown = false;

                                    for (FacePassRecognitionResult candidate : candidates) {
                                        idx++;
                                        if (candidate.faceToken == null) {
                                            Log.d(DEBUG_TAG, "  [" + idx + "] Token=NULL (skipped)");
                                            continue;
                                        }

                                        String faceToken = new String(candidate.faceToken);
                                        float searchScore = candidate.detail.searchScore;
                                        float livenessScore = candidate.detail.livenessScore;
                                        String state = (candidate.recognitionState == 0) ? "PASS" : "FAIL(" + candidate.recognitionState + ")";
                                        
                                        Log.d(DEBUG_TAG, "  [" + idx + "] Token=" + faceToken 
                                            + ", SearchScore=" + searchScore 
                                            + ", LivenessScore=" + livenessScore 
                                            + ", State=" + state
                                            + (searchScore >= scoreFilter ? " ✓ ACCEPTED" : " ✗ REJECTED (< " + scoreFilter + ")"));
                                        
                                        if (searchScore >= scoreFilter) {
                                            // faceToken → employeeId変換（PalmSecureはemployeeIdを使用するため）
                                            String userId = dbHelper.findEmployeeId(faceToken);
                                            String candidateId = (userId != null && !userId.isEmpty()) ? userId : faceToken;
                                            // ★ Bug2修正: 候補の名前を取得し、IDリストと同順でcandidateNamesに追加
                                            String candidateName = dbHelper.findName(faceToken);
                                            if (candidateName == null) candidateName = "";

                                            Log.d(DEBUG_TAG, "    → Token→EmployeeId: " + faceToken + " → " + candidateId + ", Name: " + candidateName);
                                            candidateList.add(candidateId);
                                            candidateNames.add(candidateName);

                                            // ★ Bug1修正: 最初に閾値を超えたTop1候補にのみToastを表示（後続候補はスキップ）
                                            // Android ToastはQueueで処理されるため、全候補にToastを出すと最後の候補名が
                                            // 画面に残り続け、最終結果(Top1)と食い違いが発生する。
                                            if (!isToastShown && FacePassRecognitionState.RECOGNITION_PASS == candidate.recognitionState) {
                                                getFaceImageByFaceToken(candidate.trackId, faceToken);
                                                isToastShown = true;
                                            }
                                            showRecognizeResult(candidate.trackId, searchScore, livenessScore, !TextUtils.isEmpty(faceToken));
                                        }
                                    }
                                    Log.d(DEBUG_TAG, "★ [TopK] Summary: " + candidateList.size() + "/" + candidates.length + " passed filter (score >= " + scoreFilter + ")");
                                }

                                if (!candidateList.isEmpty()) {
                                    Log.d(DEBUG_TAG, "═══════════════════════════════════════════");
                                    Log.d(DEBUG_TAG, "★ [TopK] Broadcasting ACTION_CANDIDATE_LIST");
                                    Log.d(DEBUG_TAG, "  Candidate count: " + candidateList.size());
                                    
                                    // 連続キャプチャ防止のためフラグを早期セット
                                    mIsVerifying = true;
                                    mDetectResultQueue.clear();
                                    
                                    // 1番目の候補（最有力）の名前とIDを取得（★ローカル認証用）
                                    String resultName = "";
                                    String resultId = "";
                                    try {
                                        String topFaceToken = (candidates[0].faceToken != null) ? new String(candidates[0].faceToken) : "";
                                        Log.d(DEBUG_TAG, "★ [デバッグ] Local DB検索開始: 対象Token=" + topFaceToken);
                                        
                                        resultName = dbHelper.findName(topFaceToken);
                                        resultId = dbHelper.findEmployeeId(topFaceToken);
                                        
                                        Log.d(DEBUG_TAG, "★ [デバッグ] DB抽出結果: Name=" + resultName + ", EmployeeID=" + resultId);

                                        // ★ Null安全性（NullSafety）を確保するOOP設計
                                        if (resultId == null || resultId.isEmpty()) {
                                            resultId = topFaceToken; // IDが見つからない場合はTokenをフォールバックとして使用
                                            Log.w(DEBUG_TAG, "★ [警告] Employee IDが空です。TokenをIDとして代用します: " + resultId);
                                        }
                                        if (resultName == null) {
                                            resultName = "";
                                            Log.e(DEBUG_TAG, "★ [重大] 氏名(resultName)が見つかりません。DBが空、または登録データが未同期である可能性があります。");
                                        }
                                    } catch (Exception e) {
                                        Log.e(DEBUG_TAG, "★ [エラー] トップ候補のメタデータ検索中に例外が発生しました", e);
                                    }

                                    Log.d(DEBUG_TAG, "  → Result Name: " + resultName);
                                    Log.d(DEBUG_TAG, "  → Result ID: " + resultId);
                                    
                                    for (int ci = 0; ci < candidateList.size(); ci++) {
                                        Log.d(DEBUG_TAG, "  → List[" + ci + "]: " + candidateList.get(ci));
                                    }
                                    Log.d(DEBUG_TAG, "═══════════════════════════════════════════");
                                    
                                    android.content.Intent candidatesIntent = new android.content.Intent("com.bodycamera.ba.ACTION_CANDIDATE_LIST");
                                    candidatesIntent.putStringArrayListExtra("candidate_list", candidateList);
                                    candidatesIntent.putExtra("result_name", resultName);
                                    candidatesIntent.putExtra("result_id", resultId);
                                    // ★ Bug2修正: 候補名リスト(candidateListと同順)をBroadcastに含める
                                    // TopActivity側でVein認証のIDをキーに正確な名前を引き出すために使用
                                    candidatesIntent.putStringArrayListExtra("candidate_names", candidateNames);
                                    sendBroadcast(candidatesIntent);
                                    
                                    
                                    // Auto-close camera so NewFaceAuthActivity can return to TopActivity
                                    mAndroidHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d(DEBUG_TAG, "★ [TopK] Calling safeFinish() to close camera");
                                            safeFinish();
                                        }
                                    });
                                    return; // Exit RecognizeThread
                                } else {
                                    Log.d(DEBUG_TAG, "★ [TopK] No valid candidates → Continue scanning (noAPI branch)");
                                    // No API fallback - keep scanning for a better face
                                }

                            } else {
                                // MODE: Legacy (Flow 1 or Flow 3 with "Use TopK" disabled)
                                Log.d(DEBUG_TAG, "★ [Legacy Mode] Skipping TopK. Executing Capture directly.");
                                executeFaceCapture(recognizeData);
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

        /*
        int windowRotation = ((WindowManager) (getApplicationContext().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getRotation() * 90;
        if (windowRotation == 0) {
            cameraRotation = FacePassImageRotation.DEG90;
        } else if (windowRotation == 90) {
            cameraRotation = FacePassImageRotation.DEG0;
        } else if (windowRotation == 270) {
            cameraRotation = FacePassImageRotation.DEG180;
        } else {
            cameraRotation = FacePassImageRotation.DEG270;
        }
        */
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
                Log.d(DEBUG_TAG, "★ Back On-screen Back button clicked at " + System.currentTimeMillis());
                finish();
            }
        });
    }


    @Override
    public void onBackPressed() {
        Log.d(DEBUG_TAG, "★ Back Hardware Back button pressed at " + System.currentTimeMillis());
        super.onBackPressed();
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
        // 認識スレッドと映像供給スレッドを停止する
        mRecognizeThread.isInterrupt = true;
        mFeedFrameThread.isInterrupt = true;

        mRecognizeThread.interrupt();
        mFeedFrameThread.interrupt();

        // カメラリソースを解放する
        if (manager != null) {
            manager.release();
        }
        if (mIRCameraManager != null) {
            mIRCameraManager.release();
        }

        // Broadcast Receiverの登録を解除する
        try {
            unregisterReceiver(authReceiver);
        } catch (Exception e) {
            // Receiverが登録されていない場合は無視する
        }

        // UIスレッドのタスクをすべてキャンセルする
        if (mAndroidHandler != null) {
            mAndroidHandler.removeCallbacksAndMessages(null);
        }

        // ★ DBコネクションを解放する（onDestroy()のみで行う）
        // 注意: 各DBメソッド内では db.close() を呼ばず、ここで一括解放する。
        //      これにより RecognizeThread 実行中に接続が切断される問題を防ぐ。
        if (dbHelper != null) {
            dbHelper.close();
        }

        /* ★ SDK解放をコメントアウト（パフォーマンス最適化）
         理由: FacePassActivity終了時にSDKを解放すると、次回起動時に再初期化（1-2秒）が必要になる。
         SDKをプロセス内に保持することで、2回目以降のカメラ起動が即時になる。
         SDKの解放はMainActivity.onDestroy()でのみ行う（アプリ完全終了時）。
         if (FacePassManager.mFacePassHandler != null) {
             FacePassManager.mFacePassHandler.release();
             FacePassManager.mFacePassHandler = null;
             Log.d(DEBUG_TAG, "FacePassActivity.onDestroy: Released SDK.");
         }
         FacePassManager.getInstance().isInitFinished = false;
        */
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
            s = new SpannableString("認証成功");
            imageView.setImageResource(R.drawable.success);
        } else {
            s = new SpannableString("認証失敗");
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
                    String employeeId = dbHelper.findEmployeeId(faceToken);
                    Log.i(DEBUG_TAG, "getFaceImageByFaceToken:showToast");
                    showToast("氏名 = " + name, Toast.LENGTH_SHORT, true, bitmap);
                    //ID を表示する場合
                    // showToast("姓名 = " + name + "\nID = " + (employeeId != null ? employeeId : ""), Toast.LENGTH_SHORT, true, bitmap);
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


    // Added for modular capture logic
    private void executeFaceCapture(RecognizeData recognizeData) {
        Log.d(DEBUG_TAG, "★ executing Legacy Face Capture...");
        try {
            byte[] finalImageData = recognizeData.nv21Data;
            int finalWidth = recognizeData.width;
            int finalHeight = recognizeData.height;

            if (finalImageData == null || finalImageData.length < (finalWidth * finalHeight)) {
                Log.e(DEBUG_TAG, "Capture Error: データバッファが無効または空です");
                return;
            }

            // 1. キャプチャ用ディレクトリの準備とクリーンアップ
            String fileName = "face_" + System.currentTimeMillis() + ".jpg";
            // ★ Sửa lỗi "Image file not found": Sử dụng thư mục Download để ứng dụng BA có thể truy cập được ảnh cho luồng Cloud Auth
            java.io.File sharedDir = new java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "FaceAuth");
            
            // ストレージ肥大化を防ぐため、新しい写真を保存する前に古いファイルをすべて削除します
            if (sharedDir.exists()) {
                mcv.testfacepass.utils.FileUtil.deleteContents(sharedDir);
            } else {
                sharedDir.mkdirs();
            }

            // 2. YuvImageを使用してNV21からJPEGに変換
            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                    finalImageData,
                    android.graphics.ImageFormat.NV21,
                    finalWidth,
                    finalHeight,
                    null);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, finalWidth, finalHeight), 50, out);
            byte[] jpegBytes = out.toByteArray();
            try { out.close(); } catch (Exception ignored) {}

            // 3. 回転処理のためにBitmapにデコード
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
            if (bitmap == null) {
                Log.e(DEBUG_TAG, "Critical: JPEG Decode failed in memory!");
                return;
            }

            // 4. 回転処理 (270度)
            int finalRotation = 270;
            int jpegQuality = 80;

            if (finalRotation != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.postRotate(finalRotation);
                android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                if (rotated != bitmap) {
                    bitmap.recycle();
                    bitmap = rotated;
                }
            }

            // 5. リサイズ処理 (最大1024px)
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

            // 6. ファイルへの最終保存
            java.io.File destFile = new java.io.File(sharedDir, fileName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
            boolean compressOk = bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, jpegQuality, fos);
            fos.flush();
            fos.close();

            if (!compressOk || destFile.length() == 0) {
                Log.e(DEBUG_TAG, "Failed to compress bitmap to file!");
                if (bitmap != null) bitmap.recycle();
                return;
            }

            // 7. FileProviderを介してコンテンツURIを返す (Android 11+ 対策)
            final String finalPath = destFile.getAbsolutePath();
            final int finalW = bitmap.getWidth();
            final int finalH = bitmap.getHeight();

            Log.d(DEBUG_TAG, "★ キャプチャ成功 保存先: " + finalPath + " [" + finalW + "x" + finalH + "] (" + (destFile.length()/1024) + "KB)");;

            final long finalFileSize = destFile.length();
            mAndroidHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Toast.makeText(FacePassActivity.this, "FINALLY: " + finalW + "x" + finalH + " (" + (finalFileSize/1024) + "KB)", Toast.LENGTH_LONG).show();
                }
            });

            // リファクタリング: Main Appへ画像パスをBroadcast送信
            
            // 1. スキャン一時停止
            mIsVerifying = true;
            
            // 2. Broadcast送信
            Log.d(DEBUG_TAG, "★ 送信ACTION_PROCESS_FACE → Main App: " + finalPath);
            android.content.Intent intent = new android.content.Intent(ACTION_PROCESS_FACE);
            intent.putExtra("image_path", finalPath);
            sendBroadcast(intent);
            
            // 3. UIフィードバック表示
            mAndroidHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Toast.makeText(FacePassActivity.this, "Verifying...", Toast.LENGTH_SHORT).show();
                }
            });

            // 4. 検証結果待ち（mIsVerifyingがReceiverによりリセットされるまで待機）
            // ループ先頭のmIsVerifyingチェックでスキップされる
            
            // 古いフレームをクリアして再処理を防止
            mDetectResultQueue.clear(); 
            
            Log.d(DEBUG_TAG, "★ 待機 API応答待ち開始 (mIsVerifying=true)");
            
            // 8. メモリ解放
            if (bitmap != null) {
                bitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(DEBUG_TAG, "Ultimate Capture Failure", e);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(DEBUG_TAG, "★ onWindowFocusChanged: hasFocus=" + hasFocus + ", mPendingFinish=" + mPendingFinish);
        if (hasFocus && mPendingFinish) {
            Log.d(DEBUG_TAG, "★ onWindowFocusChanged: Executing postponed finish() safely.");
            mPendingFinish = false;
            finish();
        }
    }
}
