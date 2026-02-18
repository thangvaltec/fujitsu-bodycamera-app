package mcv.testfacepass.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * FacePassApiHelper
 * FacePassActivityから直接サーバーAPIを呼び出すためのヘルパークラス。
 * Multipart/form-dataによる画像アップロードを行います。
 * (BodyCameraのFaceRecognitionApi.javaを移植)
 */
public class FacePassApiHelper {

    private static final String TAG = "FacePassApiHelper";
    private static final String BOUNDARY = "----WebKitFormBoundary7MA4YWxkTrZu0gW";

    /**
     * 顔認証リクエストをサーバーに送信します
     *
     * @param imageFile アップロードする画像ファイル (JPEG)
     * @param deviceId  デバイス番号
     * @param policeId  警官ID (オプション)
     * @param serverUrl サーバーURL
     * @return 生のJSONレスポンス文字列、失敗した場合はnull
     */
    public static String sendFaceRecognition(File imageFile, String deviceId, String policeId, String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            Log.e(TAG, "Server URL is empty");
            return null;
        }

        try {
            // ファイルの検証
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file not found: " + imageFile.getPath());
                return null;
            }
            
            long fileSize = imageFile.length();
            Log.d(TAG, "★ Uploading image: " + imageFile.getName() + " (" + (fileSize/1024) + "KB)");

            // タイムスタンプ
            String timestamp = String.valueOf(System.currentTimeMillis());

            // 接続設定
            URL url = new URL(serverUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(10000); // 10秒タイムアウト
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);

            DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());

            // 1. timestamp
            addFormField(outputStream, "timestamp", timestamp);

            // 2. deviceId
            addFormField(outputStream, "deviceId", deviceId != null ? deviceId : "UNKNOWN");

            // 3. policeId
            addFormField(outputStream, "policeId", policeId != null && !policeId.isEmpty() ? policeId : "null");

            // 4. 画像ファイル
            outputStream.write(("--" + BOUNDARY + "\r\n").getBytes("UTF-8"));
            outputStream.write(("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\n").getBytes("UTF-8"));
            outputStream.write(("Content-Type: image/jpeg\r\n\r\n").getBytes("UTF-8"));
            outputStream.flush();

            // ファイルバイトデータの書き込み
            FileInputStream fileInputStream = new FileInputStream(imageFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            fileInputStream.close();
            outputStream.flush();

            outputStream.write(("\r\n").getBytes("UTF-8"));

            // マルチパートの終了
            outputStream.write(("--" + BOUNDARY + "--\r\n").getBytes("UTF-8"));
            outputStream.flush();
            outputStream.close();

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
                // エラー時もJSONが返る可能性があるので、nullではなくbodyを返す
                // (ただし呼び出し元で解析が必要)
            }

            connection.disconnect();
            
            // エラーコードでもレスポンスボディがあれば返す (API仕様による: status!=0 のJSONが返る場合があるため)
            if (responseBody != null && !responseBody.isEmpty()) {
                return responseBody;
            }
            
            return null; // 完全に失敗

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
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }
}
