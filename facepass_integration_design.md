# FacePass SDK 統合設計書

## 1. 概要
本文書は、標準のAndroidカメラを使用する方式（フェーズ1）から、専用の `FacePass SDK` を統合する方式（フェーズ3）へのアーキテクチャ移行について説明します。この変更により、リアルタイムの顔追跡、生体検知（Liveness Detection）、およびより高いセキュリティが可能になります。

## 2. アーキテクチャ比較

### 現在のフロー（フェーズ1 - テスト用）
現在は `MediaStore` を使用して単純な静止画を撮影しています。
```mermaid
graph LR
    A["ユーザーが'顔スキャン'をクリック"] --> B["システムカメラアプリを開く"]
    B --> C["写真撮影"]
    C --> D["ファイルパスを返却"]
    D --> E["サーバーへアップロード"]
```

### 新しいフロー（フェーズ3 - 本番用 SDK）
`FacePass SDK` をアプリに直接埋め込みます。アプリ自体がカメラの「ドライバー」として機能します。
```mermaid
graph TD
    User(["ユーザー"]) --> Start["顔認証開始"]
    Start --> Init["FacePass SDK初期化\n(AIモデルロード)"]
    Init --> Camera["カメラ起動 (カスタムビュー)"]
    
    subgraph "リアルタイムループ (30 FPS)"
        Camera -- "フレームデータ (NV21)" --> FeedFrame["FacePassHandler.feedFrame()"]
        FeedFrame -- "結果" --> Check{"顔検出 & 画質OK?"}
        Check -- No --> Camera
        Check -- Yes --> Liveness["生体検知\n(なりすまし防止)"]
    end
    
    Liveness -- "Pass" --> Recognize["ローカル認証\n(オプション)"]
    Recognize --> Capture["最適画像の切り出し"]
    Capture --> API["BodyCameraサーバーへアップロード"]
    API --> Finish(["認証成功"])
```

## 3. コンポーネント詳細連携

メーカー提供のデモにあるロジックを実装した新しいActivity `FacePassCameraActivity` を作成します。

```mermaid
sequenceDiagram
    participant UI as NewFaceAuthActivity
    participant Strategy as MakerAppCaptureStrategy
    participant CamActivity as FacePassCameraActivity
    participant SDK as FacePassHandler
    participant API as FaceRecognitionApi

    UI->>Strategy: launchCapture()
    Strategy->>CamActivity: Activity起動
    
    rect rgb(240, 248, 255)
    Note over CamActivity, SDK: 初期化フェーズ
    CamActivity->>SDK: initSDK(Context)
    CamActivity->>SDK: authDevice(License)
    CamActivity->>SDK: initHandle(Config, Models)
    end
    
    rect rgb(255, 250, 240)
    Note over CamActivity, SDK: カメラループ
    CamActivity->>CamActivity: カメラオープン (Camera1/Camera2)
    loop 毎フレーム処理
        CamActivity->>SDK: feedFrame(NV21 Image)
        SDK-->>CamActivity: FacePassTrackResult
        
        opt 顔検出 & 高品質
            CamActivity->>CamActivity: 生体検知 / スコア確認
            CamActivity->>CamActivity: 最適画像をファイル保存
            CamActivity-->>Strategy: ファイルパス返却 (RESULT_OK)
            Note over CamActivity: カメラ停止 & 終了
        end
    end
    end

    Strategy-->>UI: callback(File)
    UI->>API: サーバーへファイルアップロード
```

## 4. 実装ステップ (方針)

これを実現するために、以下の手順を実行します：

1.  **依存関係の追加**:
    *   `facepass.aar` を `libs/` に追加 (完了)
    *   `.bin` モデルとライセンスを `assets/` に追加 (完了)

2.  **ヘルパークラス (デモからの移植)**:
    *   `CameraManager.java` をコピー: ローレベルなカメラハードウェア制御。
    *   `ComplexFrameHelper.java` をコピー: フレームバッファの管理。
    *   `FaceConfig.java` / `SettingVar.java` をコピー: SDKパラメータ管理。

3.  **新しい画面 (`FacePassCameraActivity`)**:
    *   デモの `MainActivity` を「必要最小限」にしたバージョンを作成します。
    *   SDKを自動的に初期化します。
    *   画質が良い顔画像を検出したら、即座に自動キャプチャします（「登録」ボタンなどは不要）。

4.  **ストラテジーの接続**:
    *   外部アプリを待機するのではなく、この新しい `FacePassCameraActivity` を起動するように `MakerAppCaptureStrategy.kt` を更新します。
