#  Project Overview

## 1. (3-App Architecture)

プロジェクトフォルダ内には以下の3つの主要なアプリケーションが含まれています。

### ① 認証デモアプリ (Main App)
*   **パス**: `ba/`
*   **役割**: ユーザーインターフェース（TopActivity）、設定管理（Settings）、および最終的な認証結果を表示（VeinResultActivity）します。
*   **特徴**: 
    *   `TopActivity` から認証フローを開始します。
    *   Faceのみ、Veinのみ、または「Face + Vein」認証を制御します。
    *   ローカルサーバーおよびクラウドサーバーとの選択する。

### ② 顔認証 (FacePass)
*   **パス**: `maker_sdk_doc\original 20260120 Facepass\Facepass3568`
*   **役割**: 顔認識および生体検知
*   **特徴**:
    *   FacePass SDK を使用しています。
    *   メインアプリからのインテントを受け取り、カメラを起動して顔を照合します。
    *   照合成功時、氏名やIDをブロードキャスト（`ACTION_CANDIDATE_LIST`）でメインアプリに返します。

### ③ 静脈認証( PalmSecure)
*   **パス**: `PalmSecure_GUI_Sample\app`
*   **役割**: 手のひら静脈による本人特定を行います。
*   **特徴**:
    *   Fujitsu PalmSecure SDK を使用しています。
    *   メインアプリからの要求に応じて静脈スキャン画面を起動し、1:N（Local）照合を行います。
    *   結果をメインアプリ（TopActivity）に返します。

---

## 2. 認証処理の仕組み (Mechanism)

1.  **TopActivity**: 設定されたフロー（例：Face + Vein）に基づき、まず顔認証アプリを呼び出します。
2.  **FacePass (Maker App)**: 顔を認識し、氏名・IDを取得してメインアプリに戻します。
3.  **TopActivity**: 顔認証の結果を保持したまま、次に静脈認証アプリを呼び出します。
4.  **PalmSecure (Vein App)**: 静脈をスキャンして本人を特定し、結果をメインアプリに戻します。
5.  **VeinResultActivity**: 両方の結果（または単一の結果）をまとめ、ユーザーの氏名・ID・成功/失敗の状態を最終画面に表示します。

---

## 3. ビルド方法 (Build Instructions)

Android Studio を使用してプロジェクトをビルド・実行する手順は以下の通りです。

### 前提条件
*   **Android Studio**: 最新バージョンを推奨。
*   **JDK**: **JDK 21** が必要です（特に `ba` モジュールのビルドに必須）。
*   **SDK/NDK**: Android SDK API Level 31以上。

### 手順
1.  **プロジェクトのインポート**:
    *   Android Studio を開き、`C:\Users\thangpv\Palm\android-1125` フォルダを選択してインポートします。
2.  **JDK の設定**:
    *   `File` -> `Settings` -> `Build, Execution, Deployment` -> `Build Tools` -> `Gradle` を開きます。
    *   `Gradle JDK` を **JDK 21** に設定してください。
3.  **各モジュールの選択**:
    *   ツールバーの実行構成ドロップダウンから、ビルドしたいモジュール（`ba`, `testfacepass`, または `PalmSecure_GUI_Sample`）を選択します。
4.  **ビルドと実行**:
    *   `Build` -> `Make Project` を実行し、エラーがないことを確認します。
    *   実機（RK3568 搭載デバイス等）を接続し、実行ボタンを押してインストールします。
