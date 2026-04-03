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

    // Cache constants as bytes to avoid repeated conversion
    private static final byte[] LINE_FEED = "\r\n".getBytes();
    private static final byte[] TWO_HYPHENS = "--".getBytes();
    private static final byte[] BOUNDARY_BYTES = BOUNDARY.getBytes();

    /**
     * 顔認証リクエストをサーバーに送信します
     */
    public static String sendFaceRecognition(File imageFile, String deviceId, String policeId, String serverUrl) {
        HttpURLConnection connection = null;
        try {
            if (!imageFile.exists()) {
                // ログ: 分析向けにフルパスを記録
                Log.e(TAG, "★ [API ERROR] Image file not found: " + imageFile.getPath());
                return "{\"status\":-1, \"message\":\"[エラー] 画像ファイルが見つかりません\"}";
            }

            String timestamp = String.valueOf(System.currentTimeMillis());
            URL url = new java.net.URI(serverUrl).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            try (DataOutputStream outputStream = new DataOutputStream(
                    new BufferedOutputStream(connection.getOutputStream()))) {
                // 1. timestamp
                writeField(outputStream, "timestamp", timestamp);

                // 2. deviceId
                writeField(outputStream, "deviceId", deviceId != null ? deviceId : "UNKNOWN");

                // 3. policeId
                writeField(outputStream, "policeId", policeId != null && !policeId.isEmpty() ? policeId : "null");

                // 4. 画像ファイル
                outputStream.write(TWO_HYPHENS);
                outputStream.write(BOUNDARY_BYTES);
                outputStream.write(LINE_FEED);
                outputStream.write("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"".getBytes());
                outputStream.write(LINE_FEED);
                outputStream.write("Content-Type: image/jpeg".getBytes());
                outputStream.write(LINE_FEED);
                outputStream.write(LINE_FEED);

                try (FileInputStream fileInputStream = new FileInputStream(imageFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }

                outputStream.write(LINE_FEED);
                outputStream.write(TWO_HYPHENS);
                outputStream.write(BOUNDARY_BYTES);
                outputStream.write(TWO_HYPHENS);
                outputStream.write(LINE_FEED);

                // Final flush once at the end
                outputStream.flush();
            }

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

            return responseBody;

        } catch (IllegalArgumentException e) {
            // URLの構成（スキームが不正です
            Log.e(TAG, "★ [API ERROR] URL Argument Error | URL: " + serverUrl + " | " + e.toString(), e);
            return "{\"status\":-1, \"message\":\"[エラー] URL形式不正\"}";
        } catch (java.net.URISyntaxException | java.net.MalformedURLException e) {
            Log.e(TAG, "★ [API ERROR] Invalid URL Configuration | URL: " + serverUrl + " | " + e.toString(), e);
            return "{\"status\":-1, \"message\":\"[エラー] URL形式不正\"}";
        } catch (java.net.SocketTimeoutException e) {
            Log.e(TAG, "★ [API ERROR] Connection Timeout | URL: " + serverUrl + " | " + e.toString(), e);
            return "{\"status\":-1, \"message\":\"[エラー] タイムアウト: サーバー無応答\"}";
        } catch (java.net.UnknownHostException e) {
            Log.e(TAG, "★ [API ERROR] DNS Resolution Failed | URL: " + serverUrl + " | " + e.toString(), e);
            return "{\"status\":-1, \"message\":\"[エラー] DNS: サーバーが見つかりません\"}";
        } catch (javax.net.ssl.SSLHandshakeException e) {
            Log.e(TAG, "★ [API ERROR] SSL Handshake Failed | URL: " + serverUrl + " | " + e.toString(), e);
            return "{\"status\":-1, \"message\":\"[エラー] SSL証明書エラー\"}";
        } catch (java.io.FileNotFoundException e) {
            Log.e(TAG, "★ [API ERROR] File Access Denied | " + e.toString(), e);
            return "{\"status\":-1, \"message\":\"[エラー] 画像アクセス権限なし\"}";
        } catch (NullPointerException e) {
            Log.e(TAG, "★ [API ERROR] Null Pointer | " + e.toString(), e);
            return "{\"status\":-1, \"message\":\"[エラー] 設定項目が不足しています\"}";
        } catch (Exception e) {
            Log.e(TAG, "★ [API ERROR] Unexpected Error | URL: " + serverUrl + " | " + e.toString(), e);

            // ユーザー向けのメッセージを整理
            String simpleName = e.getClass().getSimpleName();
            String displayMsg = simpleName;

            // 特定の難解なエラーを親切な日本語に変換
            if ("IllegalArgumentException".equals(simpleName)) {
                displayMsg = "URL形式不正 (設定を再確認してください)";
            }

            return "{\"status\":-1, \"message\":\"[エラー] " + displayMsg + "\"}";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void writeField(DataOutputStream os, String name, String value) throws IOException {
        os.write(TWO_HYPHENS);
        os.write(BOUNDARY_BYTES);
        os.write(LINE_FEED);
        os.write(("Content-Disposition: form-data; name=\"" + name + "\"").getBytes());
        os.write(LINE_FEED);
        os.write(LINE_FEED);
        os.write(value.getBytes());
        os.write(LINE_FEED);
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
