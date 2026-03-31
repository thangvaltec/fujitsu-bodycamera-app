# Tóm tắt tiến độ dự án - Face Authentication (để đọc đầu conversation mới)

> Cập nhật lần cuối: 2026-03-31

---

## Mục tiêu dự án
Ứng dụng Android thân thể camera (BodyCamera) tích hợp nhận diện khuôn mặt (Megvii FacePass SDK) trên board RK3568, Android 11.

- **Package chính (ba)**: `c:\Users\thangpv\Palm\android-1125\ba`
- **Maker App SDK**: `c:\Users\thangpv\Palm\android-1125\maker_sdk_doc\original 20260120 Facepass\Facepass3568\testfacepass`

---

## Các luồng xác thực (Auth Flows)

| Flow | Tên | Mô tả |
|------|-----|--------|
| Flow 1 | Face Only | Chỉ quét mặt → Local Auth hoặc Cloud Auth |
| Flow 2 | Vein Only | Chỉ quét tĩnh mạch (PalmSecure) |
| Flow 3 | Face + Vein | Quét mặt trước → Quét tĩnh mạch |

### Phân biệt Local vs Cloud trong Flow 1:
- **Local (TopK)**: MakerApp nhận diện trực tiếp trên thiết bị → trả về `ACTION_CANDIDATE_LIST` qua Broadcast → hiển thị `顔認証完了 (Local)`. Tên/ID lấy từ SQLite DB local.
- **Cloud**: Chụp ảnh → gửi lên Server API → nhận kết quả → hiển thị `顔認証完了しました (Cloud)`. Ảnh được lưu ở `/sdcard/Download/FaceAuth/`.

---

## Các file đã sửa trong phiên này (2026-03-31)

### 1. `FacePassActivity.java` (MakerApp)
- **`safeFinish()`**: Kiểm tra `hasWindowFocus()` trước khi `finish()`, dùng `mPendingFinish` flag + `onWindowFocusChanged()` để tránh crash BufferQueueProducer khi chuyển màn hình quá nhanh.
- **Storage**: `FILE_ROOT_PATH` → `context.getExternalFilesDir(null)` (Scoped Storage, SDK models).
- **FaceAuth capture dir**: `/sdcard/Download/FaceAuth/` (phải dùng public để BA app đọc được cho Cloud Auth). **QUAN TRỌNG: không dùng `getExternalFilesDir` ở đây vì sẽ gây lỗi "Image file not found" cho Cloud Auth**.
- **Debug logs**: Thêm `★ [デバッグ]` logs cho TopK DB lookup (resultName, resultId).

### 2. `FacePassManager.java` (MakerApp utils)
- `FILE_ROOT_PATH` → `context.getExternalFilesDir(null).getAbsolutePath()` để tuân thủ Android 11 Scoped Storage.

### 3. `TopActivity.kt`
- **`triggerAutoLoop()`**: Thêm delay 1000ms (Hardware Cooldown) trước khi khởi động lại Camera. Giải quyết crash khi thời gian hiển thị màn hình kết quả = 0s hoặc 1s.
- **`onActivityResult()`**: Thêm debug log `★ [デバッグ]` để kiểm tra resultName/resultId nhận về.
- **Toast**: `"顔認証完了 (Local)"` cho Local, `"顔認証完了しました (Cloud)"` cho Cloud.

### 4. `NewFaceAuthActivity.kt`
- **`handleCandidateList()`**: Log rõ ràng `★ [デバッグ] PutExtra: ResultName=...` và `ResultID=...`. Cảnh báo `★ [警告]` nếu Name/ID rỗng.
- **`ResultMessage`**: Đã rút gọn chỉ còn `"認証成功"` (bỏ phần `Name: ..., ID: ...` thừa trên dòng message).

### 6. `VeinResultActivity.kt`
- **Phòng thủ**: Thêm check `isVeinOnlyMode`, nếu đang ở chế độ Vein Only thì ẩn hoàn toàn Name Label và Name text để tránh hiển thị thông tin cũ từ cache.

### 7. Core Fix: Identity Leakage
- **TopActivity.kt**: 
    - Reset `faceResultName` và `faceResultId` = null khi bắt đầu luồng Vein Only (3 vị trí: Click nút, Auto-loop, API trigger).
    - Trong `onActivityResult`, chỉ cho phép pass Name/ID sang kết quả nếu KHÔNG PHẢI là Vein Only mode.

---

## Vấn đề đã giải quyết

| Vấn đề | Nguyên nhân | Giải pháp |
|--------|------------|-----------|
| App crash (crack) khi auth liên tục với delay ngắn | SurfaceFlinger chưa giải phóng trước khi Camera mới mở | `safeFinish()` + 1000ms cooldown trong `triggerAutoLoop()` |
| "Get local group info error" | SDK models lưu ở `/sdcard/Download` bị Scoped Storage chặn | Di chuyển sang `getExternalFilesDir(null)` |
| "Image file not found" (Cloud Auth) | Ảnh chụp lưu vào vùng private của MakerApp, BA app không đọc được | Dùng `/sdcard/Download/FaceAuth/` (public) cho ảnh chụp tạm |
| Màn hình kết quả hiển thị tên/ID thất thường | Dữ liệu có khi ở key cũ (`ResultName`), có khi key mới (`EXTRA_NEW_NAME`) | Đã đồng bộ key, luôn set cả hai, UI luôn hiển thị |
| Dòng message thừa "ローカル認証成功 (Name:..., ID:...)" | Message cũ chứa cả thông tin đã hiển thị ở khung bên dưới | Rút gọn message chỉ còn `"認証成功"` |
| Lộ tên cũ khi quét Vein Only | Cache `faceResultName` trong TopActivity không được reset | Reset cache khi vào Vein mode và thêm check phòng thủ tại UI |

---

## Các vấn đề còn lại / Cần kiểm tra

1. **Test thực tế**: Cần chạy trên thiết bị RK3568 để xác nhận:
   - Cloud Auth: hết lỗi "Image file not found"
   - Local Auth: Tên và ID hiển thị đúng (nếu vẫn `★氏名未登録` → DB chưa đồng bộ, cần đăng ký lại)
   - App không crash khi delay = 0s

2. **Đăng ký lại user**: Vì đã đổi `FILE_ROOT_PATH` của SDK từ `/sdcard/Download` sang `getExternalFilesDir`, dữ liệu face cũ bị mất. **Cần vào Maker App đăng ký lại các khuôn mặt từ đầu.**

3. **Cleanup ảnh cũ**: Chưa implement `cleanupOldImages()` tự động xóa ảnh cũ trong `/sdcard/Download/FaceAuth/`.

---

## Kiến trúc Broadcast (Local Auth flow)

```
[FacePassActivity - MakerApp]
  → SDK nhận diện TopK
  → DB lookup: faceToken → (name, employeeId)
  → sendBroadcast(ACTION_CANDIDATE_LIST) với result_name, result_id
  → safeFinish()

[NewFaceAuthActivity - BA App]
  ← nhận ACTION_CANDIDATE_LIST
  → handleCandidateList(candidates, resultName, resultId)
  → setResult(RESULT_OK) với ResultName, ResultID, ResultStatus=2
  → finish()

[TopActivity]
  ← onActivityResult(REQUEST_FACE)
  → nếu candidateList != null → Local → showFullScreenMessage ("Local")
  → nếu candidateList == null → Cloud → showFullScreenMessage ("Cloud")
  → forwardFaceResultToVeinResultWithDetails(...)

[VeinResultActivity]
  ← handleNewFaceAuthIntent() hoặc handleIntent()
  → Hiển thị Tên + ID
```

---

## Quy tắc code

- **Comment**: Tiếng Nhật
- **Style**: OOP
- **Log debug**: Thêm dấu `★` để dễ filter trong Logcat
- **Build**: `$env:JAVA_HOME="C:\Program Files\Java\jdk-21"; .\gradlew assembleDebug`
