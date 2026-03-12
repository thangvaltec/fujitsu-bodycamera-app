/*
 * PsThreadIdentify.java
 *
 *    All Rights Reserved, Copyright(c) FUJITSU FRONTECH LIMITED 2021
 */

package com.fujitsu.frontech.palmsecure_sample.service;

import android.util.Log;

import com.fujitsu.frontech.palmsecure.JAVA_BioAPI_CANDIDATE;
import com.fujitsu.frontech.palmsecure.JAVA_BioAPI_IDENTIFY_POPULATION;
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

import java.util.ArrayList;

/**
 * Thread that performs 1:N identification against all registered templates.
 *
 * Uses JAVA_BioAPI_Identify (same as PsThreadVerify batch mode) which handles
 * capture + match internally in one SDK call.
 *
 * Result semantics:
 *   - SDK error (capture failed, no hand placed, etc.) → result = FUNCTION_FAILED, authenticated = false
 *   - Scanned OK but no match found after all retries  → result = OK, authenticated = false
 *   - Match found                                      → result = OK, authenticated = true, userId set
 */
public class PsThreadIdentify extends PsThreadBase {

    public PsThreadIdentify(PsService service,
                            PalmSecureIf palmsecureIf,
                            JAVA_uint32 moduleHandle,
                            int numberOfRetry,
                            int sleepTime) {
        super("PsThreadIdentify", service, palmsecureIf, moduleHandle, null, numberOfRetry, sleepTime, 0);
    }

    @Override
    public void run() {
        PsThreadResult stResult = new PsThreadResult();
        stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
        stResult.authenticated = false;

        try {
            // ── Step 1: Load all registered templates once ────────────────────
            PsDataManager dm = new PsDataManager(service.getActivity(),
                    service.getUsingSensorType(),
                    service.getUsingDataType());

            ArrayList<String> nameList = new ArrayList<>();
            JAVA_BioAPI_IDENTIFY_POPULATION population;
            try {
                population = dm.convertDBToBioAPI_Data_All(nameList);
            } catch (PalmSecureException e) {
                stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
                stResult.pseErrNumber = e.ErrNumber;
                Ps_Sample_Apl_Java_NotifyResult_Identify(stResult);
                return;
            }

            if (nameList.isEmpty()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "No registered templates in DB");
                stResult.result = PalmSecureConstant.JAVA_BioAPI_OK;
                stResult.authenticated = false;
                Ps_Sample_Apl_Java_NotifyResult_Identify(stResult);
                return;
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Total templates loaded: " + nameList.size());

            // ── Step 2: Retry loop ────────────────────────────────────────────
            for (int attempt = 0; attempt <= this.numberOfRetry; attempt++) {
                if (service.cancelFlg) break;
                stResult.retryCnt = attempt;

                if (attempt > 0) {
                    Ps_Sample_Apl_Java_NotifyGuidance(R.string.RetryTransaction, false);
                    int waited = 0;
                    while (waited < this.sleepTime && !service.cancelFlg) {
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        waited += 100;
                    }
                    if (service.cancelFlg) break;
                }

                Ps_Sample_Apl_Java_NotifyWorkMessage(R.string.WorkVerify);

                JAVA_sint32 maxFRR = new JAVA_sint32();
                maxFRR.value = PalmSecureConstant.JAVA_PvAPI_MATCHING_LEVEL_NORMAL;
                JAVA_sint32 far = new JAVA_sint32();
                far.value = PalmSecureConstant.JAVA_BioAPI_FALSE;
                JAVA_uint32 farPrecedence = new JAVA_uint32();
                farPrecedence.value = PalmSecureConstant.JAVA_BioAPI_FALSE;
                JAVA_uint32 totalTemplates = new JAVA_uint32();
                totalTemplates.value = nameList.size();
                JAVA_uint32 maxResults = new JAVA_uint32();
                maxResults.value = 1;
                JAVA_uint32 numberReturned = new JAVA_uint32();
                JAVA_BioAPI_CANDIDATE[] candidates = new JAVA_BioAPI_CANDIDATE[1];
                candidates[0] = new JAVA_BioAPI_CANDIDATE();
                JAVA_sint32 timeout = new JAVA_sint32();
                JAVA_sint32 templateUpdate = new JAVA_sint32();

                long identifyResult;
                try {
                    identifyResult = palmsecureIf.JAVA_BioAPI_Identify(
                            moduleHandle, maxFRR, far, farPrecedence,
                            population, totalTemplates, maxResults,
                            numberReturned, candidates, timeout, templateUpdate);
                } catch (PalmSecureException e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Identify SDK error attempt=" + attempt, e);
                    stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
                    stResult.pseErrNumber = e.ErrNumber;
                    break; // Hard SDK error — stop retrying
                }

                stResult.result = identifyResult;
                if (BuildConfig.DEBUG) Log.d(TAG, "Identify attempt=" + attempt
                        + " result=" + identifyResult + " returned=" + numberReturned.value);

                if (identifyResult != PalmSecureConstant.JAVA_BioAPI_OK) {
                    // Capture or SDK failure — stop retrying, keep error result
                    break;
                }

                if (numberReturned.value > 0 && candidates[0] != null) {
                    int matchedIndex = extractMatchedIndex(candidates[0]);
                    if (matchedIndex >= 0 && matchedIndex < nameList.size()) {
                        stResult.authenticated = true;
                        stResult.userId = new ArrayList<>();
                        stResult.userId.add(nameList.get(matchedIndex));
                        if (BuildConfig.DEBUG) Log.d(TAG, "Match found: ID=" + nameList.get(matchedIndex));
                        break;
                    }
                }
                // OK but no match → retry
            }

        } catch (PsAplException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Identify error", e);
            stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
            stResult.authenticated = false;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Identify unexpected error", e);
            stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
            stResult.authenticated = false;
        }

        Ps_Sample_Apl_Java_NotifyResult_Identify(stResult);
    }

    /**
     * Extracts the matched index from a JAVA_BioAPI_CANDIDATE object.
     */
    private int extractMatchedIndex(JAVA_BioAPI_CANDIDATE candidate) {
        try {
            java.lang.reflect.Field field = candidate.getClass().getField("BIRInArray");
            return (int) field.getLong(candidate);
        } catch (Exception ignored) {}

        try {
            for (java.lang.reflect.Field f : candidate.getClass().getFields()) {
                Object val = f.get(candidate);
                if (val instanceof Number) {
                    int idx = ((Number) val).intValue();
                    if (BuildConfig.DEBUG) Log.w(TAG, "extractMatchedIndex fallback: field=" + f.getName() + " val=" + idx);
                    return idx;
                }
            }
        } catch (Exception ignored) {}

        return -1;
    }
}
