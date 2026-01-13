import requests
import time
import os

# 1. サーバーのURL設定
url = "https://mot-recog.facet-cloud.com/recv"
device_id = "2222222"

# 2. 画像ファイルのパス
image_path = "park.jpg"

# 3. timestampを取得,unix変換
timestamp = str(int(time.time() * 1000))

# 4. 送信データの設定
payload = {
    "timestamp": timestamp,
    "deviceId": device_id,
    "policeId": "null"
}

try:
    # ファイルがあるかを確認
    if not os.path.exists(image_path):
        print(f"エラー: '{image_path}' がない。")
    else:
        with open(image_path, "rb") as f:
            files = {
                "image": ("image.jpg", f, "image/jpeg")
            }
            
            print(f"リクエスト")
            print(f"送信データ: {payload}")
            
            # リクエスト実行
            response = requests.post(url, data=payload, files=files)
            
            # 結果の表示
            print(f"response")
            print(f"status_code: {response.status_code}")
            print(f"response result: {response.text}")

except Exception as e:
    print(f"エラー ある: {e}")