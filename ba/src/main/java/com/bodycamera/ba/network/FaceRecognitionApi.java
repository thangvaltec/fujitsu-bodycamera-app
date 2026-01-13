package com.bodycamera.ba.network;

import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * FaceRecognitionApi
 * 顔認証サーバーとの通信を処理します。
 * Multipart/form-dataによる画像アップロードをサポートします。
 */
public class FaceRecognitionApi {

    private static final String TAG = "FaceRecognitionApi";
    private static final String BOUNDARY = "----WebKitFormBoundary7MA4YWxkTrZu0gW";

    /**
     * 顔認証リクエストをサーバーに送信します
     * 
     * @param imageFile アップロードする画像ファイル (JPEG)
     * @param deviceId  デバイス番号(Settingsから取得)
     * @param policeId  警官ID (オプション、null可)
     * @param serverUrl サーバーURL (Settingsから取得)
     * @return 生のJSONレスポンス文字列、失敗した場合はnull
     */
    public static String sendFaceRecognition(File imageFile, String deviceId, String policeId, String serverUrl) {
        try {
            // ファイルの検証
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file not found: " + imageFile.getPath());
                return null;
            }

            // タイムスタンプ
            String timestamp = String.valueOf(System.currentTimeMillis());

            // 接続設定
            URL url = new URL(serverUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {

                // 1. timestamp
                addFormField(outputStream, "timestamp", timestamp);

                // 2. deviceId
                addFormField(outputStream, "deviceId", deviceId != null ? deviceId : "UNKNOWN");

                // 3. policeId
                addFormField(outputStream, "policeId", policeId != null && !policeId.isEmpty() ? policeId : "null");

                // 4. 画像ファイル
                outputStream.write(("--" + BOUNDARY + "\r\n").getBytes("UTF-8"));
                outputStream.write(("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\n")
                        .getBytes("UTF-8"));
                outputStream.write(("Content-Type: image/jpeg\r\n\r\n").getBytes("UTF-8"));
                outputStream.flush();

                // ファイルバイトデータの書き込み
                try (FileInputStream fileInputStream = new FileInputStream(imageFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                outputStream.flush();

                outputStream.write(("\r\n").getBytes("UTF-8"));

                // マルチパートの終了
                outputStream.write(("--" + BOUNDARY + "--\r\n").getBytes("UTF-8"));
                outputStream.flush();
            }

            // レスポンスの取得
            int responseCode = connection.getResponseCode();
            String responseBody;

            if (responseCode >= 200 && responseCode < 300) {
                responseBody = readStream(connection.getInputStream());
                Log.d(TAG, "Success: " + responseBody);
            } else {
                InputStream errorStream = connection.getErrorStream();
                responseBody = errorStream != null ? readStream(errorStream) : "No response body";
                Log.e(TAG, "Error " + responseCode + ": " + responseBody);
            }

            connection.disconnect();
            return responseBody;

        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e.getMessage(), e);
            return null;
        }
    }

    private static void addFormField(DataOutputStream outputStream, String name, String value) throws IOException {
        outputStream.write(("--" + BOUNDARY + "\r\n").getBytes("UTF-8"));
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
        outputStream.write((value + "\r\n").getBytes("UTF-8"));
        outputStream.flush();
    }

    private static String readStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }
}
