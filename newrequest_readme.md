import React, { useState } from 'react';

interface DeviceSettingsPageProps {
  onNavigateBack: () => void;
}

const DeviceSettingsPage: React.FC<DeviceSettingsPageProps> = ({ onNavigateBack }) => {
  const [serverUrl, setServerUrl] = useState('https://mot-recog.facet-cloud.com/recv');
  const [deviceId, setDeviceId] = useState('2222222');
  const [distance, setDistance] = useState('2m');
  const [liveness, setLiveness] = useState('88.0');
  const [authMethod, setAuthMethod] = useState<'cloud' | 'local'>('cloud');
  const [faceVeinAuthMethod, setFaceVeinAuthMethod] = useState<'cloud' | 'local'>('cloud');
  const [autoAuthMethod, setAutoAuthMethod] = useState<'none' | 'face' | 'vein' | 'both'>('none');
  const [topK, setTopK] = useState('1');
  const [recognitionThreshold, setRecognitionThreshold] = useState('60');

  const handleSave = () => {
    // In a real app, we would save these settings to local storage or via a bridge
    console.log('Saving settings:', { serverUrl, deviceId, distance, liveness, authMethod, faceVeinAuthMethod, autoAuthMethod, topK, recognitionThreshold });
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
