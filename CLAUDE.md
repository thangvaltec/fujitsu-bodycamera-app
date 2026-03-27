# Project Summary: Authentication Flow & Identity Propagation

## 1. Key Improvements
- **Identity Display (Name/ID)**: Successfully implemented full identity propagation from the Maker App to the final Result screen for both **Face-only** and **Face+Vein (Hybrid)** flows.
    - **Maker App**: Broadcasts `result_name` and `result_id` upon successful recognition.
    - **Main App**: `TopActivity` caches these values during the Face step and forwards them to `VeinResultActivity` after the Palm scan.
- **Transition Optimization**: Reduced the delay between Face and Palm authentication steps to **500ms** for a smoother user experience.
- **UI Refinement**: Widened buttons in the result screen to accommodate longer Japanese text like "終了 (自動継続中...)".
- **Error Handling**: Added auto-retry/loop logic for NG (failure) results to enable hands-free operation.

## 2. Technical Context
- **Communication**: Uses `ACTION_CANDIDATE_LIST` broadcast with extras `result_name` and `result_id`.
- **Primary Activities**:
    - `ba/TopActivity.kt`: Orchestrator.
    - `ba/NewFaceAuthActivity.kt`: Face recognition receiver.
    - `ba/VeinResultActivity.kt`: Final UI.
    - `maker_sdk_doc/.../FacePassActivity.java`: Face SDK logic.

## 3. Current State
- **Build**: Requires **JDK 21**.
- **Restoration**: All legacy components (`MainActivity`, `Face3Activity`, etc.) have been restored and original project structure is intact.
- **Documentation**: `README.md` contains the full architecture overview in Japanese.

## 4. Pending/Future Tasks
- Further optimization of NG (No-match) latency in PalmSecure (currently ~1.7s).
- Investigating total removal of legacy code (approved cleanup was reverted).
