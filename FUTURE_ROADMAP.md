# デバイス管理システム連携ロードマップ

## 概要
将来的に、本アプリを「デバイス管理システム」と連携させ、端末の管理やログ収集を効率化します。
現在は `build.gradle` にハードコードされている接続設定を、`SettingsActivity` に移行し、動的に設定可能にすることを目標とします。

---

## 現状
現在、デバイス管理サーバーへの接続情報は `build.gradle` (Module: ba) の `defaultConfig` 内に `buildConfigField` として定義されています。

```groovy
// build.gradle (現状)
buildConfigField "String", "DEVICE_API_BASE_URL", "\"http://10.200.2.46:5000\""
buildConfigField "String", "DEVICE_API_AUTHMODE_PATH", "\"/api/device/getAuthMode\""
buildConfigField "String", "DEVICE_API_AUTHLOG_PATH", "\"/api/auth/logs\""
```

## 将来の要件 (Phase 4)

### 1. 設定画面への項目追加
`SettingsActivity` に以下の設定項目を追加します。

*   **管理サーバーURL**
    *   用途: 端末管理APIのエンドポイントベースURL。
    *   例: `http://10.200.2.46:5000`
*   **テナントID**
    *   用途: マルチテナント環境での識別子。
    *   機能: サーバー側で端末がどの組織に属するかを識別するために使用。

### 2. 設定のスマート化
*   **初期設定フロー**: 初回起動時にQRコード読み取りなどで一括設定できる機能などの検討（スマート設定）。
*   **動的反映**: アプリを再ビルドすることなく、接続先環境（開発・ステージング・本番）を切り替えられるようにする。

### 3. 実装予定コンポーネント
*   **SettingsActivity**: UI追加 (EditText x 2)。
*   **SharedPreferences**: キー追加 (`KEY_MGMT_SERVER_URL`, `KEY_TENANT_ID`)。
*   **Network Module**: `BuildConfig` の参照を廃止し、`SharedPreferences` から値を取得するように変更。

---

## 備考
この機能は、現在の「顔認証サーバー設定 (Phase 2)」とは別のサーバー設定となります。混同しないよう、UI上でもセクションを分けて配置することを推奨します。
