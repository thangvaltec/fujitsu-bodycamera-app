import React, { useState } from 'react';

interface DeviceSettingsPageProps {
  onNavigateBack: () => void;
}

const DeviceSettingsPage: React.FC<DeviceSettingsPageProps> = ({ onNavigateBack }) => {
  const [serverUrl, setServerUrl] = useState('https://mot-recog.facet-cloud.com/recv');
  const [deviceId, setDeviceId] = useState('2222222');
  const [distance, setDistance] = useState('2m');
  const [liveness, setLiveness] = useState('88.0');
  const [identificationLevel, setIdentificationLevel] = useState<'none' | 'partial'>('none');
  const [authMethod, setAuthMethod] = useState<'cloud' | 'local'>('cloud');
  const [faceVeinAuthMethod, setFaceVeinAuthMethod] = useState<'cloud' | 'local'>('cloud');
  const [autoAuthMethod, setAutoAuthMethod] = useState<'none' | 'face' | 'vein' | 'both'>('none');
  const [topK, setTopK] = useState('1');
  const [recognitionThreshold, setRecognitionThreshold] = useState('60');
  const [resultDisplayTime, setResultDisplayTime] = useState('2');
  const [messageDisplayTime, setMessageDisplayTime] = useState('1');

  const handleSave = () => {
    // In a real app, we would save these settings to local storage or via a bridge
    console.log('Saving settings:', { serverUrl, deviceId, distance, liveness, identificationLevel, authMethod, faceVeinAuthMethod, autoAuthMethod, topK, recognitionThreshold, resultDisplayTime, messageDisplayTime });
    onNavigateBack();
  };

  return (
    <main className="bg-[#0a0f1d] text-white h-screen overflow-y-auto flex flex-col p-6 font-sans">
      <div className="w-full max-w-md mx-auto pb-12">
        <h1 className="text-3xl font-bold text-center text-[#38bdf8] mb-8 tracking-wide">
          設定
        </h1>

        <div className="space-y-6">
          {/* Server URL */}
          <div>
            <label className="block text-gray-400 text-sm mb-2">Server URL</label>
            <input
              type="text"
              value={serverUrl}
              onChange={(e) => setServerUrl(e.target.value)}
              className="w-full bg-[#1e293b] border border-transparent focus:border-[#38bdf8] rounded-sm py-3 px-4 text-white outline-none transition duration-200"
            />
          </div>

          {/* Device ID */}
          <div>
            <label className="block text-gray-400 text-sm mb-2">Device ID</label>
            <input
              type="text"
              value={deviceId}
              onChange={(e) => setDeviceId(e.target.value)}
              className="w-full bg-[#1e293b] border border-transparent focus:border-[#38bdf8] rounded-sm py-3 px-4 text-white outline-none transition duration-200"
            />
          </div>

          {/* Identification Distance */}
          <div>
            <label className="block text-gray-400 text-sm mb-4">識別距離</label>
            <div className="flex justify-between items-center">
              {['0.5m', '1m', '1.5m', '2m'].map((d) => (
                <label key={d} className="flex items-center space-x-2 cursor-pointer group">
                  <div className="relative flex items-center justify-center">
                    <input
                      type="radio"
                      name="distance"
                      value={d}
                      checked={distance === d}
                      onChange={(e) => setDistance(e.target.value)}
                      className="sr-only"
                    />
                    <div className={`w-5 h-5 rounded-full border-2 transition-colors duration-200 ${distance === d ? 'border-[#38bdf8]' : 'border-gray-500'}`}></div>
                    {distance === d && (
                      <div className="absolute w-2.5 h-2.5 rounded-full bg-[#38bdf8]"></div>
                    )}
                  </div>
                  <span className="text-white text-sm">{d}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Identification Level */}
          <div>
            <label className="block text-gray-400 text-sm mb-4">識別レベル（ライブネス判定ON/OFF）</label>
            <div className="flex space-x-6">
              {[
                { id: 'none', label: '写真を判別しない' },
                { id: 'partial', label: '写真を判別する' },
              ].map((item) => (
                <label key={item.id} className="flex items-center space-x-3 cursor-pointer">
                  <div className="relative flex items-center justify-center">
                    <input
                      type="radio"
                      name="identificationLevel"
                      checked={identificationLevel === item.id}
                      onChange={() => setIdentificationLevel(item.id as any)}
                      className="sr-only"
                    />
                    <div className={`w-5 h-5 rounded-full border-2 transition-colors duration-200 ${identificationLevel === item.id ? 'border-[#38bdf8]' : 'border-gray-500'}`}></div>
                    {identificationLevel === item.id && (
                      <div className="absolute w-2.5 h-2.5 rounded-full bg-[#38bdf8]"></div>
                    )}
                  </div>
                  <span className="text-white text-sm">{item.label}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Liveness Threshold */}
          <div>
            <label className="block text-gray-400 text-sm mb-2">なりすまし防止閾値 (Liveness)</label>
            <input
              type="text"
              value={liveness}
              onChange={(e) => setLiveness(e.target.value)}
              className="w-full bg-[#1e293b] border border-transparent focus:border-[#38bdf8] rounded-sm py-3 px-4 text-white outline-none transition duration-200"
            />
          </div>

          {/* Auto Authentication Method Setting */}
          <div>
            <label className="block text-gray-400 text-sm mb-4">
              自動認証設定
            </label>
            <div className="grid grid-cols-2 gap-y-4 gap-x-2 items-center">
              {[
                { id: 'none', label: '無効' },
                { id: 'face', label: '顔認証' },
                { id: 'vein', label: '静脈認証' },
                { id: 'both', label: '顔と静脈認証' },
              ].map((item) => (
                <label key={item.id} className="flex items-center space-x-3 cursor-pointer">
                  <div className="relative flex items-center justify-center">
                    <input
                      type="radio"
                      name="autoAuthMethod"
                      checked={autoAuthMethod === item.id}
                      onChange={() => setAutoAuthMethod(item.id as any)}
                      className="sr-only"
                    />
                    <div className={`w-5 h-5 rounded-sm border-2 transition-colors duration-200 flex items-center justify-center ${autoAuthMethod === item.id ? 'bg-[#0ea5e9] border-[#0ea5e9]' : 'border-gray-500'}`}>
                      {autoAuthMethod === item.id && (
                        <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                        </svg>
                      )}
                    </div>
                  </div>
                  <span className="text-white text-sm">{item.label}</span>
                </label>
              ))}
            </div>
            <p className="text-xs text-gray-400 mt-3 leading-relaxed">
              ※設定すると認証結果の表示後、自動で次の認証を開始します。変更はメイン画面に戻ってからデバイス設定画面で行ってください。
            </p>
          </div>

          {/* Result Screen Display Time */}
          <div>
            <label className="block text-gray-400 text-sm mb-2">結果画面表示設定時間(単位：s）</label>
            <input
              type="text"
              value={resultDisplayTime}
              onChange={(e) => setResultDisplayTime(e.target.value)}
              className="w-full bg-[#1e293b] border border-transparent focus:border-[#38bdf8] rounded-sm py-3 px-4 text-white outline-none transition duration-200"
            />
          </div>

          {/* Face Auth Method */}
          <div>
            <label className="block text-gray-400 text-sm mb-4">顔認証方法</label>
            <div className="flex space-x-6 items-center">
              <label className="flex items-center space-x-3 cursor-pointer">
                <div className="relative flex items-center justify-center">
                  <input
                    type="radio"
                    name="authMethod"
                    checked={authMethod === 'cloud'}
                    onChange={() => setAuthMethod('cloud')}
                    className="sr-only"
                  />
                  <div className={`w-5 h-5 rounded-sm border-2 transition-colors duration-200 flex items-center justify-center ${authMethod === 'cloud' ? 'bg-[#0ea5e9] border-[#0ea5e9]' : 'border-gray-500'}`}>
                    {authMethod === 'cloud' && (
                      <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                      </svg>
                    )}
                  </div>
                </div>
                <span className="text-white text-sm">クラウドサーバ認証</span>
              </label>
              <label className="flex items-center space-x-3 cursor-pointer">
                <div className="relative flex items-center justify-center">
                  <input
                    type="radio"
                    name="authMethod"
                    checked={authMethod === 'local'}
                    onChange={() => setAuthMethod('local')}
                    className="sr-only"
                  />
                  <div className={`w-5 h-5 rounded-sm border-2 transition-colors duration-200 flex items-center justify-center ${authMethod === 'local' ? 'bg-[#0ea5e9] border-[#0ea5e9]' : 'border-gray-500'}`}>
                    {authMethod === 'local' && (
                      <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                      </svg>
                    )}
                  </div>
                </div>
                <span className="text-white text-sm">ローカルサーバ認証</span>
              </label>
            </div>
            <p className="text-xs text-gray-400 mt-3 leading-relaxed">
              ※ローカルサーバ認証を利用する場合、あらかじめ「顔認証」アプリで顔登録を行う必要があります。
            </p>
          </div>

          {/* Face and Vein Auth Method */}
          <div>
            <label className="block text-gray-400 text-sm mb-4">顔と静脈認証方法</label>
            <div className="flex space-x-6 items-center">
              <label className="flex items-center space-x-3 cursor-pointer">
                <div className="relative flex items-center justify-center">
                  <input
                    type="radio"
                    name="faceVeinAuthMethod"
                    checked={faceVeinAuthMethod === 'cloud'}
                    onChange={() => setFaceVeinAuthMethod('cloud')}
                    className="sr-only"
                  />
                  <div className={`w-5 h-5 rounded-sm border-2 transition-colors duration-200 flex items-center justify-center ${faceVeinAuthMethod === 'cloud' ? 'bg-[#0ea5e9] border-[#0ea5e9]' : 'border-gray-500'}`}>
                    {faceVeinAuthMethod === 'cloud' && (
                      <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                      </svg>
                    )}
                  </div>
                </div>
                <span className="text-white text-sm">クラウドサーバ認証</span>
              </label>
              <label className="flex items-center space-x-3 cursor-pointer">
                <div className="relative flex items-center justify-center">
                  <input
                    type="radio"
                    name="faceVeinAuthMethod"
                    checked={faceVeinAuthMethod === 'local'}
                    onChange={() => setFaceVeinAuthMethod('local')}
                    className="sr-only"
                  />
                  <div className={`w-5 h-5 rounded-sm border-2 transition-colors duration-200 flex items-center justify-center ${faceVeinAuthMethod === 'local' ? 'bg-[#0ea5e9] border-[#0ea5e9]' : 'border-gray-500'}`}>
                    {faceVeinAuthMethod === 'local' && (
                      <svg className="w-3.5 h-3.5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="3" d="M5 13l4 4L19 7" />
                      </svg>
                    )}
                  </div>
                </div>
                <span className="text-white text-sm">ローカルサーバ認証</span>
              </label>
            </div>
            <p className="text-xs text-gray-400 mt-3 leading-relaxed">
              ※ローカルサーバ認証を利用する場合、あらかじめ「顔認証」アプリで顔登録を行う必要があります。
            </p>
          </div>

          {/* Top-K Count and Recognition Threshold side-by-side */}
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-gray-400 text-sm mb-2">Top-Kの人数</label>
              <input
                type="text"
                value={topK}
                onChange={(e) => setTopK(e.target.value)}
                className="w-full bg-[#1e293b] border border-transparent focus:border-[#38bdf8] rounded-sm py-3 px-4 text-white outline-none transition duration-200"
              />
            </div>
            <div>
              <label className="block text-gray-400 text-sm mb-2">認識比較閾値</label>
              <input
                type="text"
                value={recognitionThreshold}
                onChange={(e) => setRecognitionThreshold(e.target.value)}
                className="w-full bg-[#1e293b] border border-transparent focus:border-[#38bdf8] rounded-sm py-3 px-4 text-white outline-none transition duration-200"
              />
            </div>
          </div>

          {/* Message Display Time Setting */}
          <div>
            <label className="block text-gray-400 text-sm mb-2">メッセージ表示の時間設定(単位：s）</label>
            <input
              type="text"
              value={messageDisplayTime}
              onChange={(e) => setMessageDisplayTime(e.target.value)}
              className="w-full bg-[#1e293b] border border-transparent focus:border-[#38bdf8] rounded-sm py-3 px-4 text-white outline-none transition duration-200"
            />
          </div>

          {/* Save Button */}
          <button
            type="button"
            onClick={handleSave}
            className="w-full bg-[#0ea5e9] hover:bg-[#0284c7] text-white font-medium py-3 px-4 rounded-sm text-xl shadow-md transition duration-200 mt-4"
          >
            保存
          </button>
        </div>
      </div>
    </main>
  );
};

export default DeviceSettingsPage;

4. 動的な待機時間設定の追加 (New Requirement)
現在ハードコードされている以下の2つの待機時間を、設定画面 (SettingsActivity) から動的に変更できるように変更する。

対象の待機時間:
Face認証完了からVein認証開始までの待機時間 (Transition Delay):
場所: TopActivity.kt (現在 1000ms / 500ms)
目的: 顔認証成功後、ユーザーが手をかざす準備をするための時間を調整可能にする。
結果画面表示から自動的に閉じるまでの待機時間 (Auto-Close Delay):
場所: VeinResultActivity.kt (現在 2000ms)
目的: 認証結果（OK/NG）を確認する時間の長さをユーザーが調整可能にする。
仕様:
SettingsActivity にこれらの値を入力する項目を追加する。
数値（ミリ秒）で入力し、SharedPreferences に保存する。
保存された値がない場合は、現在のデフォルト値（500ms / 2000ms）を使用する。
--
ここから
---
### 【Lưu ý sửa đổi sau này - Debug & Logic Auth】

#### 1. Lỗi Logic xác thực sai (Status = 2)
- **Vấn đề:** Hiện tại trong `NewFaceAuthActivity.kt`, code đang coi `status == 0` (Thành công) và `status == 2` (Thiết bị chưa đăng ký) đều là **Thành công**.
- **Hậu quả:** Server trả về "デバイスID未登録" nhưng App vẫn hiện thông báo "認証成功".
- **Cần sửa:** Chỉ chấp nhận `status == 0` là thành công. Các mã khác phải báo lỗi theo `message` từ Server.

#### 2. Cải thiện thông báo lỗi Network (Tránh báo lỗi mạng giả)
- **Vấn đề:** Khi `FaceRecognitionApi` gặp bất kỳ lỗi nào (Sai URL, Lỗi SSL, Lỗi kết nối), nó đều trả về `null` dẫn đến App hiện "Network Error". Điều này khiến việc debug tại máy khách hàng cực kỳ khó khăn.
- **Cần sửa:**
    - Bổ sung `try-catch` chi tiết để bắt riêng `MalformedURLException`, `SSLHandshakeException`, `ConnectTimeoutException`.
    - Hiển thị thông báo lỗi cụ thể lên UI thay vì chỉ hiện "Network Error". Điều này giúp xác định ngay lập tức lý do Máy 1 (Khách hàng) bị lỗi dù Máy 2 (Dev) vẫn chạy.
    - Kiểm tra và yêu cầu quyền `MANAGE_EXTERNAL_STORAGE` (All Files Access) một cách chủ động để tránh lỗi không đọc được ảnh trên Android 11+.

--------- beginning of system
04-02 17:49:51.109 12107 12107 D NewFaceAuthActivity: ★ [終了] BroadcastReceiver登録解除完了
04-02 17:49:53.222 12107 12107 D NewFaceAuthActivity: ★ BroadcastReceiver登録完了 (ACTION_PROCESS_FACE & ACTION_CANDIDATE_LIST 待機開始)
04-02 17:49:53.223 12107 12107 D NewFaceAuthActivity: ★ Settings Pre-fetched: URL=OK, ID=OK, Liveness=88.0, DistanceIndex: 2 -> FaceMinThreshold: 60
04-02 17:49:53.223 12107 12107 D NewFaceAuthActivity: キャプチャ戦略を開始します
04-02 17:50:56.108 12107 12107 D NewFaceAuthActivity: ★受信ACTION_PROCESS_FACE: imagePath=/storage/emulated/0/Download/FaceAuth/face_1775119855571.jpg
04-02 17:50:56.109 12107 12107 D NewFaceAuthActivity: ★受信ファイル確認OK: size=48KB, path=/storage/emulated/0/Download/FaceAuth/face_1775119855571.jpg
04-02 17:50:56.111 12107 12107 D NewFaceAuthActivity: ★ API処理 開始 - ファイル: /storage/emulated/0/Download/FaceAuth/face_1775119855571.jpg (48KB)
04-02 17:50:56.111 12107 12107 D NewFaceAuthActivity: ★ API送信 送信先URL: https://mot-recog.facet-cloud.com/
04-02 17:50:56.111 12107 12107 D NewFaceAuthActivity: ★ API送信 デバイスID: 33333 | policeId: null
04-02 17:50:56.111 12107 12107 D NewFaceAuthActivity: ★ API送信 送信ファイル: face_1775119855571.jpg (48KB)
04-02 17:50:56.223 12107 12253 E FaceRecognitionApi: Error 405: {"detail":"Method Not Allowed"}
04-02 17:50:56.224 12107 12107 D NewFaceAuthActivity: ★ API応答 応答時間: 113ms
04-02 17:50:56.225 12107 12107 D NewFaceAuthActivity: ★ API応答 レスポンスJSON: {"detail":"Method Not Allowed"}
04-02 17:50:56.225 12107 12107 D NewFaceAuthActivity: ★ API結果 status=0, name=null, similarity=null, message=null
04-02 17:50:56.225 12107 12107 D NewFaceAuthActivity: ★ API結果 判定: 成功
04-02 17:50:56.230 12107 12107 D NewFaceAuthActivity: ★ 送信ACTION_AUTH_RESULT → Maker App: Success=true, message=認証成功
04-02 17:50:56.279 12107 12107 D NewFaceAuthActivity: onActivityResult: req=7777, res=0
04-02 17:50:56.279 12107 12107 D NewFaceAuthActivity: ★ Broadcast経由の認証結果あり → 結果画面へ遷移
04-02 17:50:56.700 12107 12107 D NewFaceAuthActivity: ★ [終了] BroadcastReceiver登録解除完了
04-02 17:50:58.806 12107 12107 D NewFaceAuthActivity: ★ BroadcastReceiver登録完了 (ACTION_PROCESS_FACE & ACTION_CANDIDATE_LIST 待機開始)
04-02 17:50:58.806 12107 12107 D NewFaceAuthActivity: ★ Settings Pre-fetched: URL=OK, ID=OK, Liveness=88.0, DistanceIndex: 2 -> FaceMinThreshold: 60
04-02 17:50:58.806 12107 12107 D NewFaceAuthActivity: キャプチャ戦略を開始します
04-02 17:51:01.201 12107 12107 D NewFaceAuthActivity: onActivityResult: req=7777, res=0
04-02 17:51:01.201 12107 12107 D NewFaceAuthActivity: ★ ユーザーがバックボタンを押しました → トップ画面に戻ります (Time: 1775119861201)
04-02 17:51:01.437 12107 12107 D NewFaceAuthActivity: ★ [終了] BroadcastReceiver登録解除完了
04-02 17:51:08.879 12107 12107 D NewFaceAuthActivity: ★ BroadcastReceiver登録完了 (ACTION_PROCESS_FACE & ACTION_CANDIDATE_LIST 待機開始)
04-02 17:51:08.880 12107 12107 D NewFaceAuthActivity: ★ Settings Pre-fetched: URL=OK, ID=OK, Liveness=88.0, DistanceIndex: 2 -> FaceMinThreshold: 60
04-02 17:51:08.880 12107 12107 D NewFaceAuthActivity: キャプチャ戦略を開始します
04-02 17:51:11.045 12107 12107 D NewFaceAuthActivity: ★受信ACTION_PROCESS_FACE: imagePath=/storage/emulated/0/Download/FaceAuth/face_1775119870601.jpg
04-02 17:51:11.046 12107 12107 D NewFaceAuthActivity: ★受信ファイル確認OK: size=55KB, path=/storage/emulated/0/Download/FaceAuth/face_1775119870601.jpg
04-02 17:51:11.049 12107 12107 D NewFaceAuthActivity: ★ API処理 開始 - ファイル: /storage/emulated/0/Download/FaceAuth/face_1775119870601.jpg (55KB)
04-02 17:51:11.049 12107 12107 D NewFaceAuthActivity: ★ API送信 送信先URL: https://mot-recog.facet-cloud.com/
04-02 17:51:11.049 12107 12107 D NewFaceAuthActivity: ★ API送信 デバイスID: 33333 | policeId: null
04-02 17:51:11.050 12107 12107 D NewFaceAuthActivity: ★ API送信 送信ファイル: face_1775119870601.jpg (55KB)
04-02 17:51:11.121 12107 12253 E FaceRecognitionApi: Error 405: {"detail":"Method Not Allowed"}
04-02 17:51:11.122 12107 12107 D NewFaceAuthActivity: ★ API応答 応答時間: 72ms
04-02 17:51:11.122 12107 12107 D NewFaceAuthActivity: ★ API応答 レスポンスJSON: {"detail":"Method Not Allowed"}
04-02 17:51:11.123 12107 12107 D NewFaceAuthActivity: ★ API結果 status=0, name=null, similarity=null, message=null
04-02 17:51:11.123 12107 12107 D NewFaceAuthActivity: ★ API結果 判定: 成功
04-02 17:51:11.126 12107 12107 D NewFaceAuthActivity: ★ 送信ACTION_AUTH_RESULT → Maker App: Success=true, message=認証成功
04-02 17:51:11.181 12107 12107 D NewFaceAuthActivity: onActivityResult: req=7777, res=0
04-02 17:51:11.182 12107 12107 D NewFaceAuthActivity: ★ Broadcast経由の認証結果あり → 結果画面へ遷移
04-02 17:51:11.777 12107 12107 D NewFaceAuthActivity: ★ [終了] BroadcastReceiver登録解除完了
04-02 17:53:21.343 12107 12107 D NewFaceAuthActivity: ★ BroadcastReceiver登録完了 (ACTION_PROCESS_FACE & ACTION_CANDIDATE_LIST 待機開始)
04-02 17:53:21.343 12107 12107 D NewFaceAuthActivity: ★ Settings Pre-fetched: URL=OK, ID=OK, Liveness=88.0, DistanceIndex: 2 -> FaceMinThreshold: 60
04-02 17:53:21.343 12107 12107 D NewFaceAuthActivity: キャプチャ戦略を開始します
04-02 17:53:24.124 12107 12107 D NewFaceAuthActivity: ★受信ACTION_PROCESS_FACE: imagePath=/storage/emulated/0/Download/FaceAuth/face_1775120003624.jpg
04-02 17:53:24.130 12107 12107 D NewFaceAuthActivity: ★受信ファイル確認OK: size=49KB, path=/storage/emulated/0/Download/FaceAuth/face_1775120003624.jpg
04-02 17:53:24.132 12107 12107 D NewFaceAuthActivity: ★ API処理 開始 - ファイル: /storage/emulated/0/Download/FaceAuth/face_1775120003624.jpg (49KB)
04-02 17:53:24.132 12107 12107 D NewFaceAuthActivity: ★ API送信 送信先URL: https://mot-recog.facet-cloud.com/recv
04-02 17:53:24.132 12107 12107 D NewFaceAuthActivity: ★ API送信 デバイスID: 333333333 | policeId: null
04-02 17:53:24.132 12107 12107 D NewFaceAuthActivity: ★ API送信 送信ファイル: face_1775120003624.jpg (49KB)
04-02 17:53:24.275 12107 12253 D FaceRecognitionApi: Success: {"status":2,"deviceId":"333333333","policeId":"null","similarity":"0","name":"","real_id":"","data":"","message":"デバイスID未登録"}
04-02 17:53:24.275 12107 12107 D NewFaceAuthActivity: ★ API応答 応答時間: 143ms
04-02 17:53:24.275 12107 12107 D NewFaceAuthActivity: ★ API応答 レスポンスJSON: {"status":2,"deviceId":"333333333","policeId":"null","similarity":"0","name":"","real_id":"","data":"","message":"デバイスID未登録"}
04-02 17:53:24.276 12107 12107 D NewFaceAuthActivity: ★ API結果 status=2, name=, similarity=0, message=デバイスID未登録
04-02 17:53:24.276 12107 12107 D NewFaceAuthActivity: ★ API結果 判定: 成功
04-02 17:53:24.279 12107 12107 D NewFaceAuthActivity: ★ 送信ACTION_AUTH_RESULT → Maker App: Success=true, message=認証成功
04-02 17:53:24.336 12107 12107 D NewFaceAuthActivity: onActivityResult: req=7777, res=0
04-02 17:53:24.336 12107 12107 D NewFaceAuthActivity: ★ Broadcast経由の認証結果あり → 結果画面へ遷移
04-02 17:53:24.731 12107 12107 D NewFaceAuthActivity: ★ [終了] BroadcastReceiver登録解除完了


Log này tiếp tục khẳng định một vấn đề nghiêm trọng về logic trong bản APK hiện tại mà tôi đã phân tích lúc nãy:

1. Tại sao App báo "認証成功" (Thành công) dù Device ID chưa đăng ký?
Hãy nhìn vào kết quả phản hồi từ Server: {"status":2, "message":"デバイスID未登録"} (Mã trạng thái: 2, Tin nhắn: Device ID chưa đăng ký).

Lỗi nằm ở dòng code này trong NewFaceAuthActivity.kt (Dòng 289):

kotlin
val isSuccess = (result.status == 0 || result.status == 2)
Vấn đề: Code của bạn đang mặc định coi cả status = 0 (Thành công) và status = 2 (Lỗi ID chưa đăng ký) đều là THÀNH CÔNG (isSuccess = true).
Hậu quả: Server trả về lỗi rõ ràng là "Chưa đăng ký thiết bị", nhưng App vẫn thản nhiên báo cho người dùng là "認証成功" (Xác thực thành công).
2. Sự khác biệt cực kỳ quan trọng giúp bạn Debug Máy 1:
Máy 2 (Của bạn): Bạn nhận được log FaceRecognitionApi: Success: .... Điều này chứng tỏ Mạng và SSL của Máy 2 hoàn toàn bình thường. Máy 2 đã gửi được ảnh lên Server và Server đã trả về dữ liệu (mặc dù dữ liệu là báo lỗi chưa đăng ký ID).
Máy 1 (Khách hàng): Máy 1 báo "Network Error" (trả về null). Điều này có nghĩa là Máy 1 thậm chí còn chưa gửi được ảnh lên tới Server (bị chặn DNS, chặn SSL, chặn Port hoặc lỗi quyền đọc ảnh).
Kết luận cho bạn:
1. Về Logic App: Bạn cần phải sửa code để status = 2 phải là Thất bại, không được để là Thành công. Nếu không, khách hàng sẽ thấy rất vô lý khi thiết bị chưa đăng ký mà vẫn báo "Thành công".

2. Về lỗi "Network Error" của Máy 1: Bây giờ bạn đã biết cách xem log chuẩn, hãy làm đúng bước cuối cùng:

Chạy adb logcat trên Máy 1 khi dùng URL chuẩn.
Nếu Máy 1 hiện log Exception: ... (giống như các ví dụ bạn làm nãy giờ), hãy gửi cho tôi. Đó chính là "chìa khóa" cuối cùng để mở ra nguyên nhân Máy 1 bị lỗi mạng.
Tôi tin rằng Máy 1 sẽ hiện lỗi SSL hoặc Connection Timeout do mạng của khách hàng chặn!_

ở 2 lỗi nên hãy log lai lỗi vấn đề de sau này tôi sẽ sửa vào file C:\Users\thangpv\Palm\android-1125\newrequest_readme.md tu dong 332
Tôi đã cập nhật xong các phát hiện quan trọng vào file newrequest_readme.md.

Tóm tắt các vấn đề đã xác định:
Lỗi Logic xác thực giả: App đang coi status = 2 (Chưa đăng ký thiết bị) là thành công. Điều này cần được sửa trong NewFaceAuthActivity.kt sau này.
Lỗi thông báo "Mạng" chung chung: Hiện tại mọi lỗi (Sai URL, Lỗi SSL, Không đọc được file) đều báo là "Network Error", gây khó khăn cho việc debug tại hiện trường.
Hiện tại bạn đã có đầy đủ "vũ khí" để debug Máy 1:
Lệnh logcat chuẩn: adb logcat -s FaceRecognitionApi NewFaceAuthActivity
Cách đọc lỗi: Bạn đã biết cách phân biệt lỗi nhập sai URL (MalformedURLException) và lỗi DNS (Unable to resolve host).
Việc còn lại: Khi nào bạn có điều kiện truy cập vào Máy 1 (Khách hàng), hãy chạy lệnh logcat và thực hiện quét mặt. Dòng log E FaceRecognitionApi: Exception: ... sẽ cho bạn biết chính xác tại sao máy đó bị lỗi (do SSL, do chặn mạng, hay do quyền đọc file).
