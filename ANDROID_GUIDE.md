# Hướng dẫn áp dụng vào Android App

## 📋 Tóm tắt nhanh

### ✅ File cần COPY vào Android project:
- **`TestApiJava.java`** - Logic chính để gửi API

### 📖 File để THAM KHẢO (không copy):
- **`test.py`** - Tham khảo cách gửi request
- **`README.md`** - Tham khảo cách sử dụng
- **`HUONG_DAN_NHANH.md`** - Hướng dẫn chi tiết

---

## 🚀 Cách áp dụng vào Android App

### Bước 1: Copy logic từ `TestApiJava.java`

Mở file `TestApiJava.java` và copy các phần sau:

#### 1.1. Constants (Dòng 15-17)
```java
private static final String API_URL = "https://mot-recog.facet-cloud.com/recv";
private static final String DEVICE_ID = "2222222";
private static final String BOUNDARY = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
```

#### 1.2. Main logic (Dòng 28-108)
Copy toàn bộ logic từ:
- Đọc file ảnh
- Tạo multipart request
- Gửi request
- Nhận response

#### 1.3. Helper method (Dòng 111-120)
```java
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
```

---

### Bước 2: Tạo class mới trong Android project

Tạo file: `app/src/main/java/com/bodycamera/ba/network/FaceRecognitionApi.java`

```java
package com.bodycamera.ba.network;

import android.content.Context;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;

public class FaceRecognitionApi {
    
    private static final String TAG = "FaceRecognitionApi";
    private static final String API_URL = "https://mot-recog.facet-cloud.com/recv";
    private static final String DEVICE_ID = "2222222"; // TODO: Lấy từ config
    private static final String BOUNDARY = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
    
    /**
     * Gửi ảnh lên server để nhận diện khuôn mặt
     * @param imageFile File ảnh cần gửi
     * @param policeId ID của cảnh sát (có thể null)
     * @return Response từ server dạng JSON string
     */
    public static String sendFaceRecognition(File imageFile, String policeId) {
        try {
            // Kiểm tra file tồn tại
            if (!imageFile.exists()) {
                Log.e(TAG, "Image file not found: " + imageFile.getPath());
                return null;
            }
            
            // Timestamp
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            // Tạo connection
            URL url = new URL(API_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
            
            try (OutputStream outputStream = connection.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true)) {
                
                // Add timestamp field
                writer.append("--" + BOUNDARY + "\r\n");
                writer.append("Content-Disposition: form-data; name=\"timestamp\"\r\n\r\n");
                writer.append(timestamp + "\r\n");
                writer.flush();
                
                // Add deviceId field
                writer.append("--" + BOUNDARY + "\r\n");
                writer.append("Content-Disposition: form-data; name=\"deviceId\"\r\n\r\n");
                writer.append(DEVICE_ID + "\r\n");
                writer.flush();
                
                // Add policeId field
                writer.append("--" + BOUNDARY + "\r\n");
                writer.append("Content-Disposition: form-data; name=\"policeId\"\r\n\r\n");
                writer.append(policeId != null ? policeId : "null" + "\r\n");
                writer.flush();
                
                // Add image file
                writer.append("--" + BOUNDARY + "\r\n");
                writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"image.jpg\"\r\n");
                writer.append("Content-Type: image/jpeg\r\n\r\n");
                writer.flush();
                
                // Write image bytes
                FileInputStream fileInputStream = new FileInputStream(imageFile);
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                fileInputStream.close();
                outputStream.flush();
                
                writer.append("\r\n");
                writer.flush();
                
                // End of multipart
                writer.append("--" + BOUNDARY + "--\r\n");
                writer.flush();
            }
            
            // Get response
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
```

---

### Bước 3: Sử dụng trong Activity/Fragment

```java
// Trong Activity hoặc Fragment
new Thread(() -> {
    // Lấy file ảnh (ví dụ từ camera hoặc storage)
    File imageFile = new File(getFilesDir(), "captured_face.jpg");
    
    // Gửi request
    String response = FaceRecognitionApi.sendFaceRecognition(imageFile, "null");
    
    // Xử lý response trên UI thread
    runOnUiThread(() -> {
        if (response != null) {
            // Parse JSON và hiển thị kết quả
            Log.d("FaceRecognition", "Response: " + response);
            // TODO: Parse JSON và cập nhật UI
        } else {
            // Xử lý lỗi
            Log.e("FaceRecognition", "Failed to get response");
        }
    });
}).start();
```

---

### Bước 4: Parse JSON response

Thêm dependency vào `build.gradle`:
```gradle
dependencies {
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

Tạo model class:
```java
public class FaceRecognitionResponse {
    public int status;
    public String deviceId;
    public String policeId;
    public String similarity;
    public String name;
    public String real_id;
    public String data;
    public String message;
}
```

Parse response:
```java
Gson gson = new Gson();
FaceRecognitionResponse result = gson.fromJson(response, FaceRecognitionResponse.class);

if (result.status == 2) {
    // Nhận diện thành công
    Log.d(TAG, "Name: " + result.name);
    Log.d(TAG, "Similarity: " + result.similarity + "%");
}
```

---

## 🔧 Tùy chỉnh cho Android

### 1. Đọc ảnh từ Camera

```java
// Sau khi chụp ảnh từ camera
Bitmap bitmap = ...; // Bitmap từ camera

// Lưu vào file tạm
File tempFile = new File(getCacheDir(), "temp_face.jpg");
FileOutputStream fos = new FileOutputStream(tempFile);
bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
fos.close();

// Gửi lên server
String response = FaceRecognitionApi.sendFaceRecognition(tempFile, "null");
```

### 2. Lấy Device ID động

```java
// Thay vì hardcode DEVICE_ID
String deviceId = Settings.Secure.getString(
    context.getContentResolver(), 
    Settings.Secure.ANDROID_ID
);
```

### 3. Dùng AsyncTask hoặc Coroutine

**Với Coroutine (Kotlin):**
```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val response = FaceRecognitionApi.sendFaceRecognition(imageFile, "null")
    withContext(Dispatchers.Main) {
        // Update UI
    }
}
```

---

## ⚠️ Lưu ý quan trọng

### 1. Permissions trong AndroidManifest.xml
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

### 2. Network Security Config
Nếu dùng HTTP (không khuyến nghị), thêm vào `res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

### 3. Chạy trên background thread
**KHÔNG BAO GIỜ** gọi network request trên Main/UI thread!

---

## 📚 Files tham khảo

| File | Mục đích |
|------|----------|
| `TestApiJava.java` | ✅ Copy logic chính từ đây |
| `test.py` | 📖 Tham khảo cách gửi request |
| `README.md` | 📖 Tham khảo cách sử dụng |
| `HUONG_DAN_NHANH.md` | 📖 Hướng dẫn chi tiết |

---

## 🎯 Tóm tắt các bước

1. ✅ Copy logic từ `TestApiJava.java`
2. ✅ Tạo class `FaceRecognitionApi.java` trong Android project
3. ✅ Thêm permissions vào AndroidManifest.xml
4. ✅ Gọi API trong background thread
5. ✅ Parse JSON response
6. ✅ Cập nhật UI với kết quả

---

## 💡 Tips

- Dùng **OkHttp** thay vì HttpURLConnection để code đẹp hơn
- Dùng **Retrofit** nếu muốn code professional hơn
- Thêm **timeout** để tránh app bị treo
- Thêm **retry logic** khi network lỗi
- Cache response để xử lý offline

---

Chúc bạn thành công! 🎉
