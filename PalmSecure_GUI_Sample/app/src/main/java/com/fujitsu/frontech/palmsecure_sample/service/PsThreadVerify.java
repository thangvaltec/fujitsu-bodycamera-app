/*
 * PsThreadVerify.java
 *
 *	All Rights Reserved, Copyright(c) FUJITSU FRONTECH LIMITED 2021
 */

package com.fujitsu.frontech.palmsecure_sample.service;

import android.util.Log;

import com.fujitsu.frontech.palmsecure.JAVA_BioAPI_INPUT_BIR;
import com.fujitsu.frontech.palmsecure.JAVA_BioAPI_BIR;
import com.fujitsu.frontech.palmsecure.JAVA_sint32;
import com.fujitsu.frontech.palmsecure.JAVA_uint32;
import com.fujitsu.frontech.palmsecure.PalmSecureIf;
import com.fujitsu.frontech.palmsecure.util.PalmSecureConstant;
import com.fujitsu.frontech.palmsecure.util.PalmSecureException;
import com.fujitsu.frontech.palmsecure_sample.data.PsDataManager;
import com.fujitsu.frontech.palmsecure_sample.data.PsThreadResult;
import com.fujitsu.frontech.palmsecure_sample.exception.PsAplException;
import com.fujitsu.frontech.palmsecure_gui_sample.BuildConfig;
import com.fujitsu.frontech.palmsecure_gui_sample.R;

/**
 * 静脈認証スレッドクラス。
 *
 * 動作モード:
 *   - 通常モード: 指定した単一のユーザーIDに対して 1:1 認証（Verify）を実行する。
 *   - バッチモード（TopK）: 顔認証で絞り込まれた候補者リスト（K人）に対して
 *     1:K の照合（Identify）を実行し、合致したユーザーIDを返す。
 *     Flow3（顔＋静脈認証）で使用される。
 */
public class PsThreadVerify extends PsThreadBase {

	/** TopKバッチモード時の候補者IDリスト。通常モードでは null。 */
	private java.util.ArrayList<String> candidateList = null;

	/**
	 * 通常モード用コンストラクタ。
	 * 指定した userID に対して 1:1 静脈認証を実行する。
	 *
	 * @param service       PsService インスタンス
	 * @param palmsecureIf  PalmSecure APIインターフェース
	 * @param moduleHandle  モジュールハンドル
	 * @param userID        認証対象のユーザーID
	 * @param numberOfRetry 最大リトライ回数
	 * @param sleepTime     リトライ間隔（ミリ秒）
	 */
	public PsThreadVerify(PsService service, PalmSecureIf palmsecureIf, JAVA_uint32 moduleHandle, String userID,
			int numberOfRetry, int sleepTime) {
		super("PsThreadVerify", service, palmsecureIf, moduleHandle, userID, numberOfRetry, sleepTime, 0);
	}

	/**
	 * TopK バッチモード用コンストラクタ。
	 * 顔認証で絞り込まれた候補者リストに対して 1:K 照合を実行する。
	 * Flow3（顔＋静脈フロー）のみで使用される。
	 * 通常の 1:1 認証フローや Flow2（静脈のみ）には影響しない。
	 *
	 * @param service        PsService インスタンス
	 * @param palmsecureIf   PalmSecure APIインターフェース
	 * @param moduleHandle   モジュールハンドル
	 * @param candidateList  顔認証TopKで絞り込まれたユーザーIDのリスト
	 * @param numberOfRetry  最大リトライ回数
	 * @param sleepTime      リトライ間隔（ミリ秒）
	 */
	public PsThreadVerify(PsService service, PalmSecureIf palmsecureIf, JAVA_uint32 moduleHandle,
			java.util.ArrayList<String> candidateList, int numberOfRetry, int sleepTime) {
		super("PsThreadVerify", service, palmsecureIf, moduleHandle, null, numberOfRetry, sleepTime, 0);
		this.candidateList = candidateList;
	}

	@Override
	public void run() {

		PsThreadResult stResult = new PsThreadResult();

		try {
			PsDataManager dataMng = new PsDataManager(
					this.service.getBaseContext(),
					this.service.getUsingSensorType(),
					this.service.getUsingDataType());

			int waitTime = 0;

			// ─── 動作モード判定 ───────────────────────────────────────────────
			// candidateList が存在する場合は TopK バッチモード（Flow3専用）
			// それ以外は通常の 1:1 認証モード（Flow2 / 単体動作）
			boolean isBatchMode = (candidateList != null && !candidateList.isEmpty());

			// バッチモード用の識別母集団（Identify API へ渡す）
			com.fujitsu.frontech.palmsecure.JAVA_BioAPI_IDENTIFY_POPULATION population = null;
			// 通常モード用のテンプレート配列（Verify API へ渡す）
			JAVA_BioAPI_BIR[] templates = null;

			// ─── テンプレートの読み込み ─────────────────────────────────────────
			if (isBatchMode) {
				// [TopK バッチモード] 候補者リストに含まれるIDのテンプレートのみを読み込む
				Log.i(TAG, "★ TopKバッチ照合を開始します: 候補者数=" + candidateList.size() + "人");
				try {
					population = dataMng.convertDBToBioAPI_Data_Batch(candidateList);
					if (population == null || population.BIRArray == null || population.BIRArray.NumberOfMembers == 0) {
						// DBに該当するテンプレートが存在しない場合はNG扱い
						Log.w(TAG, "★ TopKバッチ: DBにテンプレートが見つかりませんでした");
						stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
						Ps_Sample_Apl_Java_NotifyResult_Verify(stResult);
						return;
					}
					Log.i(TAG, "★ TopKバッチ: DBから " + population.BIRArray.NumberOfMembers + "件のテンプレートを読み込みました");
				} catch (Exception e) {
					Log.e(TAG, "候補者リストのテンプレート読み込みに失敗しました", e);
					stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
					Ps_Sample_Apl_Java_NotifyResult_Verify(stResult);
					return;
				}
			} else {
				// [通常モード] 指定ユーザーIDの全テンプレートを読み込む（複数登録対応）
				stResult.userId.add(userID);
				try {
					templates = dataMng.convertDBToBioAPI_Data_AllForId(userID);
				} catch (PalmSecureException e) {
					if (BuildConfig.DEBUG) {
						Log.e(TAG, "テンプレート取得エラー (ユーザーID=" + userID + ")", e);
					}
					stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
					stResult.pseErrNumber = e.ErrNumber;
					Ps_Sample_Apl_Java_NotifyResult_Verify(stResult);
					return;
				} catch (PsAplException pae) {
					if (BuildConfig.DEBUG) {
						Log.e(TAG, "テンプレート取得エラー (アプリ例外)", pae);
					}
					stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
					stResult.messageKey = R.string.AplErrorSystemError;
					Ps_Sample_Apl_Java_NotifyResult_Verify(stResult);
					return;
				}
			}

			// センサーの安定待機（ストリームの準備完了を待つ）
			// 初回認証でスタート直後の不安定なフレームを回避するための短い待機
			try {
				Thread.sleep(200);
			} catch (InterruptedException ie) {
				// 割り込みは無視して処理継続
			}

			// ─── 認証ループ（numberOfRetry 回まで再試行） ──────────────────────
			int verifyCnt = 0;
			for (verifyCnt = 0; verifyCnt <= this.numberOfRetry; verifyCnt++) {
				Ps_Sample_Apl_Java_NotifyWorkMessage(R.string.WorkVerify);

				// リトライ時は待機メッセージを表示してから指定時間だけ待機
				if (verifyCnt > 0) {
					Ps_Sample_Apl_Java_NotifyGuidance(R.string.RetryTransaction, false);
					waitTime = 0;
					do {
						if (this.service.cancelFlg == true) break;
						if (waitTime < this.sleepTime) {
							Thread.sleep(100);
							waitTime = waitTime + 100;
						} else {
							break;
						}
					} while (true);
				}

				// キャンセルフラグの確認
				if (this.service.cancelFlg == true) break;
				stResult.retryCnt = verifyCnt;

				// ════════════════════════════════════════════════════════════
				// 認証コア処理: モードによって Identify か Verify かを切り替える
				// ════════════════════════════════════════════════════════════
				if (isBatchMode) {
					// ──────────────────────────────────────────────────────
					// [TopK バッチモード] JAVA_BioAPI_Identify を使用（1:K照合）
					// 顔認証TopKで絞り込んだ候補者のみを母集団として照合するため、
					// 静脈スキャンは1回で済む。通常の全件Identifyとは異なる点に注意。
					// ──────────────────────────────────────────────────────

					// 照合精度パラメータの設定
					JAVA_sint32 maxFRRRequested = new JAVA_sint32();
					maxFRRRequested.value = PalmSecureConstant.JAVA_PvAPI_MATCHING_LEVEL_NORMAL;
					JAVA_sint32 farRequested = new JAVA_sint32();
					farRequested.value = PalmSecureConstant.JAVA_BioAPI_FALSE;
					JAVA_uint32 farPrecedence = new JAVA_uint32();
					farPrecedence.value = PalmSecureConstant.JAVA_BioAPI_FALSE;

					// 照合対象件数と結果格納先の設定
					JAVA_uint32 totalNumberOfTemplates = new JAVA_uint32();
					totalNumberOfTemplates.value = candidateList.size();
					JAVA_uint32 maxNumberOfResults = new JAVA_uint32();
					maxNumberOfResults.value = 1; // 最も一致度の高い1件のみ返す
					JAVA_uint32 numberReturned = new JAVA_uint32();
					com.fujitsu.frontech.palmsecure.JAVA_BioAPI_CANDIDATE[] matchedCandidates =
							new com.fujitsu.frontech.palmsecure.JAVA_BioAPI_CANDIDATE[1];
					matchedCandidates[0] = new com.fujitsu.frontech.palmsecure.JAVA_BioAPI_CANDIDATE();

					JAVA_sint32 timeout = new JAVA_sint32();
					JAVA_sint32 templateUpdate = new JAVA_sint32();

					try {
						// 1:K 照合 API 呼び出し
						stResult.result = palmsecureIf.JAVA_BioAPI_Identify(
								moduleHandle,
								maxFRRRequested,
								farRequested,
								farPrecedence,
								population,            // TopK候補者のみの母集団
								totalNumberOfTemplates,
								maxNumberOfResults,
								numberReturned,
								matchedCandidates,
								timeout,
								templateUpdate);

						if (stResult.result == PalmSecureConstant.JAVA_BioAPI_OK && numberReturned.value > 0) {
							// 照合成功: 合致した候補者のインデックスをリフレクションで取得
							// SDK の JAVA_BioAPI_CANDIDATE クラスから照合インデックスを取得する
							int matchedIndex = -1;
							try {
								// まず "BIRIndex" フィールドを直接取得を試みる
								java.lang.reflect.Field field = matchedCandidates[0].getClass().getField("BIRIndex");
								matchedIndex = field.getInt(matchedCandidates[0]);
							} catch (Exception e) {
								// "BIRIndex" が存在しない場合は "index" を含むフィールドをスキャン（フォールバック）
								try {
									java.lang.reflect.Field[] fields = matchedCandidates[0].getClass().getFields();
									for (java.lang.reflect.Field f : fields) {
										if (f.getName().toLowerCase().contains("index")) {
											matchedIndex = ((Number) f.get(matchedCandidates[0])).intValue();
											break;
										}
									}
								} catch (Exception ignored) {
									// インデックス取得に失敗した場合は matchedIndex = -1 のまま継続
								}
							}

							if (BuildConfig.DEBUG) {
								Log.d(TAG, "Identify照合結果: matchedIndex=" + matchedIndex
										+ ", candidateCount=" + candidateList.size());
							}

							// インデックスが有効範囲内であれば認証成功
							if (matchedIndex >= 0 && matchedIndex < candidateList.size()) {
								stResult.authenticated = true;
								stResult.userId.clear();
								stResult.userId.add(candidateList.get(matchedIndex));
								Log.i(TAG, "★ TopKバッチ照合 成功: ユーザーID=" + candidateList.get(matchedIndex));
								break; // 認証成功のためループを抜ける
							}
							// インデックスが範囲外の場合は照合失敗扱いでリトライ
						}
						// numberReturned == 0 の場合も照合失敗（静脈一致なし）
					} catch (PalmSecureException e) {
						// SDK APIエラーが発生した場合はリトライを中止
						stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
						stResult.pseErrNumber = e.ErrNumber;
						break;
					}

				} else {
					// ──────────────────────────────────────────────────────
					// [通常モード] JAVA_BioAPI_Verify を使用（1:1照合）
					// Flow2（静脈のみ）および PalmSecure 単体動作で使用される。
					// このブロックは TopK の変更の影響を受けない。
					// ──────────────────────────────────────────────────────

					// 照合精度パラメータの設定
					JAVA_sint32 maxFRRRequested = new JAVA_sint32();
					maxFRRRequested.value = PalmSecureConstant.JAVA_BioAPI_FALSE;
					JAVA_uint32 farPrecedence = new JAVA_uint32();
					farPrecedence.value = PalmSecureConstant.JAVA_BioAPI_FALSE;
					JAVA_uint32 result = new JAVA_uint32();
					JAVA_sint32 farAchieved = new JAVA_sint32();
					JAVA_sint32 timeout = new JAVA_sint32();

					boolean matched = false;
					if (templates != null) {
						// 同一ユーザーに複数テンプレートが登録されている場合、全テンプレートと照合
						for (int t = 0; t < templates.length; t++) {
							JAVA_BioAPI_INPUT_BIR storedTemplate = new JAVA_BioAPI_INPUT_BIR();
							storedTemplate.Form = PalmSecureConstant.JAVA_BioAPI_FULLBIR_INPUT;
							storedTemplate.BIR = templates[t];
							try {
								stResult.result = palmsecureIf.JAVA_BioAPI_Verify(
										moduleHandle,
										null,
										maxFRRRequested,
										null,
										storedTemplate,
										null,
										result,
										farAchieved,
										null,
										null,
										timeout,
										null);
							} catch (PalmSecureException e) {
								stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
								stResult.pseErrNumber = e.ErrNumber;
								break;
							}
							// いずれかのテンプレートと一致した場合は照合成功
							if (stResult.result == PalmSecureConstant.JAVA_BioAPI_OK
									&& result.value == PalmSecureConstant.JAVA_BioAPI_TRUE) {
								matched = true;
								break;
							}
						}
					}

					// SDK APIエラーが発生した場合はリトライを中止
					if (stResult.result != PalmSecureConstant.JAVA_BioAPI_OK) break;

					if (matched) {
						stResult.authenticated = true;
						break; // 認証成功のためループを抜ける
					}
					// matched == false の場合はリトライ
				}

				// キャンセルフラグの再確認（照合処理後）
				if (this.service.cancelFlg == true) break;
			}
			// ─── 認証ループ終了 ──────────────────────────────────────────────

			// 認証結果をメインスレッドに通知する
			Ps_Sample_Apl_Java_NotifyResult_Verify(stResult);

		} catch (Exception e) {
			if (BuildConfig.DEBUG) Log.e(TAG, "run(): 予期しない例外が発生しました", e);
		}
	}
}
