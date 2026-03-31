---
description: Guidelines and context for developing the Fujitsu Face & Palm Authentication Android application (RK3568, FacePass SDK).
---

# Fujitsu BodyCamera Project Guidelines

When working on this project, adhere to the following specific rules, context, and architectural patterns.

## 1. Project Architecture & Submodules

- **Main App (`ba` module)**: Core UI (`TopActivity`, `VeinResultActivity`, `SettingsActivity`), network (`DeviceApiClient`), orchestrates auth flow. Kotlin.
- **PalmSecure App (`PalmSecure_GUI_Sample`)**: Standalone palm vein auth module. Java + Kotlin. Communicates with Main App via Intents/Broadcasts.
- **Maker App (`maker_sdk_doc/original 20260120 Facepass/Facepass3568`)**: FacePass SDK integration (`FacePassActivity.java`). Face detection, liveness, 1:N recognition. Java.
- **Communication**: Main App ↔ Maker App via `ACTION_AUTH_RESULT`, `ACTION_CANDIDATE_LIST` broadcasts. Main App ↔ PalmSecure via Intents.

## 2. Hardware Specifics (RK3568 Board)

- **Display Orientation**: Screen may be mounted sideways. Check `cameraRotation` in `FacePassActivity.java` (typically `90` or `270`).
- **Cameras**: Dual camera (RGB + IR). IR is strictly for Liveness anti-spoofing (`FP_REG_MODE_LIVENESS`).
- **Performance**: Heavy Liveness checks and full-frame rendering can cause frame drops. Optimize loop speeds.

## 3. FacePass SDK Handling

- **Initialization**: Always check `FacePassManager.getInstance().isInitFinished` and `FacePassManager.isLocalGroupExist` before `feedFrame` or `recognize`.
- **Liveness vs Recognition**: `feedFrame` = bounding box + quality only. `livenessClassify` = anti-spoofing (expensive). `recognize` = 1:N matching.
- **TopK Mode**: Use `TopK` to return multiple candidates → filter by `searchScore` → map `faceToken` to `EmployeeId` via `DatabaseHelper`.
- **Do not** remove Liveness checks or alter core SDK flow without explicit review.

## 4. PalmSecure App — Architecture

### Module structure (`PalmSecure_GUI_Sample/app/src/main/`)
```
palmsecure_gui_sample/
  GUISample.kt          — Entry point, calls PsService methods
  GUIHelper.kt          — ThreadParam factory, reads INI config
  ThreadParam.kt        — data class(userId, numberOfRetry, sleepTime)
palmsecure_sample/
  service/
    PsService.java          — Starts auth threads
    PsThreadBase.java       — Base thread (notify helpers)
    PsThreadVerify.java     — 照合 (1:1 or 1:K TopK batch)
    PsThreadIdentify.java   — 識別 (1:N all users)
    PsThreadEnroll.java     — 登録
    PsThreadCancel.java     — Cancel
  data/
    PsDataManager.java      — SQLite CRUD, template conversion
    PsThreadResult.java     — Result container
  xml/
    PsFileAccessorIni.java  — Reads PalmSecureSample.ini config
```

### SDK (f3bc4and.jar — Fujitsu PalmSecure)
Key constants in `PalmSecureConstant`:
- `JAVA_BioAPI_OK` — success
- `JAVA_BioAPI_ERRCODE_FUNCTION_FAILED` — only error code available (NO timeout constant)
- `JAVA_BioAPI_PURPOSE_IDENTIFY` / `PURPOSE_VERIFY` / `PURPOSE_ENROLL`
- `JAVA_BioAPI_FULLBIR_INPUT` / `BIR_HANDLE_INPUT`
- `JAVA_PvAPI_MATCHING_LEVEL_NORMAL` (default matching level)
- `JAVA_BioAPI_ARRAY_TYPE` — population type for 1:N

## 5. PalmSecure App — Authentication Flows

### 照合 (Shougo / Verify) — `PsThreadVerify`
**Normal mode (1:1):** `JAVA_BioAPI_Verify` against specific userId's templates.
**Batch mode (1:K TopK):** `JAVA_BioAPI_Identify` against face-filtered candidate list only.
- Templates loaded once before retry loop.
- SDK error → break immediately (no retry).
- NG (OK but no match) → retry up to `numberOfRetry`.

### 識別 (Shikibetsu / Identify) — `PsThreadIdentify`
**1:N all users:** `JAVA_BioAPI_Identify` against ALL registered templates.
- Uses `convertDBToBioAPI_Data_All(nameList)` — loads all templates in one DB query.
- Same retry/error handling pattern as Verify.
- **DO NOT use** `JAVA_BioAPI_Capture` + `GetBIRFromHandle` + `IdentifyMatch` — this was unreliable. Use `JAVA_BioAPI_Identify` which handles capture internally.
- Matched index from `JAVA_BioAPI_CANDIDATE.BIRInArray` field (via reflection).

### Result semantics (both threads)
| Situation | result | authenticated |
|---|---|---|
| SDK error / capture failed | `ERRCODE_FUNCTION_FAILED` | false |
| Scan OK but no match (after all retries) | `JAVA_BioAPI_OK` | false |
| Match found | `JAVA_BioAPI_OK` | true |

**Important**: Do NOT overwrite `ERRCODE_FUNCTION_FAILED` with `OK` at the end. Keep error vs NG distinguishable.

## 6. PalmSecure App — Database

- File: `PsVeinData.sqlite3` (internal app storage)
- Schema version: 2 (autoincrement `recid` PK, multiple templates per user supported)
- Table: `veindata_table(recid, sensortype, datatype, id, veindata BLOB)`
- Multiple templates per user: always take `max(recid)` per `id` as latest

Key `PsDataManager` methods:
- `convertDBToBioAPI_Data_All(nameList)` — load all templates, populate nameList in sync with array index
- `convertDBToBioAPI_Data_Batch(targetIds)` — load only specified IDs (TopK use)
- `convertDBToBioAPI_Data_AllForId(name)` — all templates for one user (Verify normal mode)
- `getAllRegisteredTemplates()` — raw blob fetch (kept for future chunking use)
- `convertRecordsToBioAPI_Data(records)` — must set `population.Type`, `NumberOfMembers`, `Members`

## 7. PalmSecure App — Configuration

Config file: `PalmSecureSample.ini` (device internal storage, NOT in source)
Defaults in `PsFileAccessorIni.java`:

| Key | Default | Notes |
|---|---|---|
| `NumberOfRetry` | 0 | 0 = 1 attempt only. Was 2 (3 attempts, ~5s NG) → now 0 (~1.7s NG) |
| `SleepTime` | 2000ms | Wait between retries |
| `GuideMode` | 0 | |
| `GExtendedMode` | 2 | |
| `MaxResults` | 2 | |

**INI file overrides defaults.** If device has old INI with `NumberOfRetry=2`, reinstall app or modify file via adb.

## 8. Known Pitfalls & Design Decisions

- **`JAVA_BioAPI_ERRCODE_TIMEOUT` does not exist** in this SDK. There is only `ERRCODE_FUNCTION_FAILED`. Do not add timeout-specific constant references.
- **OK (50ms) vs NG (1.7s) asymmetry is expected**: OK exits early on match. NG must complete full scan cycle. Cannot easily reduce below SDK internal capture time.
- **`JAVA_BioAPI_Identify` timeout param = 0** means SDK default. Consider tuning this to reduce NG latency in future.
- **`convertRecordsToBioAPI_Data`** must set both `population.Type = JAVA_BioAPI_ARRAY_TYPE` and `population.BIRArray.NumberOfMembers` — omitting either causes SDK issues.
- **`numberOfRetry` is shared** between Verify and Identify — both read from same INI key via `createThreadParam()` in `GUIHelper.kt`.
- **Retry in Identify**: currently log for retry only appears when `attempt > 0` — if SDK error on first attempt, no retry log is shown even though time was spent.

## 9. UI & State Management

- **Settings**: Persisted in `SharedPreferences` (`SettingsActivity.PREFS_NAME`).
- **ViewBinding**: Use ViewBinding (e.g., `ActivityFace3Binding.inflate`). No `findViewById` in new code.
- **Main Thread**: Auth callbacks are on background threads. Use `runOnUiThread {}` or `Handler(Looper.getMainLooper()).post {}` for UI updates.
- **Delays**: Use `Handler(Looper.getMainLooper()).postDelayed` for UX transitions.

## 10. Development Workflow

- **Reference UI**: Use `newrequest_readme.md` or user-provided mockups as source of truth for labels/values.
- **Dynamic Settings**: Never hardcode thresholds, URLs, or toggles. Always wire to Settings GUI + SharedPreferences.
- **Before modifying auth flow**: Read both `PsThreadVerify` and `PsThreadIdentify` to understand the established pattern. Keep them consistent.
- **Before adding new DB methods to PsDataManager**: Check if similar method already exists to avoid duplication.
