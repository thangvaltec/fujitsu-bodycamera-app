/*
 * PsThreadIdentify.java
 *
 *    All Rights Reserved, Copyright(c) FUJITSU FRONTECH LIMITED 2021
 */

package com.fujitsu.frontech.palmsecure_sample.service;

import android.util.Log;

import com.fujitsu.frontech.palmsecure.JAVA_BioAPI_BIR_ARRAY_POPULATION;
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

/**
 * Thread that performs identification (scan-first) against the stored population.
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

        try {
            // Delay 200ms for sensor stability
            try { Thread.sleep(200); } catch (InterruptedException e) {}

            PsDataManager dm = new PsDataManager(service.getActivity(),
                    service.getUsingSensorType(),
                    service.getUsingDataType());
                    
            java.util.ArrayList<String> allIds = dm.getRegisteredUserIDList();
            if (allIds == null || allIds.isEmpty()) {
                if (BuildConfig.DEBUG) Log.w(TAG, "No registered IDs found in DB");
                stResult.result = PalmSecureConstant.JAVA_BioAPI_OK;
                stResult.authenticated = false;
                Ps_Sample_Apl_Java_NotifyResult_Identify(stResult);
                return;
            }

            // The SDK consumes ~40MB per template during Identify. 
            // Chunked Identify prevents OOM by limiting memory pressure.
            final int CHUNK_SIZE = 10; 
            boolean matchFound = false;

            for (int chunkStart = 0; chunkStart < allIds.size(); chunkStart += CHUNK_SIZE) {
                if (this.service.cancelFlg) break;

                int chunkEnd = Math.min(chunkStart + CHUNK_SIZE, allIds.size());
                java.util.ArrayList<String> chunkIds = new java.util.ArrayList<>(allIds.subList(chunkStart, chunkEnd));
                
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Identifying chunk: " + chunkStart + " to " + (chunkEnd-1));
                }

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Searching next chunk: " + chunkStart + " to " + (chunkEnd-1));
                }

                JAVA_BioAPI_IDENTIFY_POPULATION population = dm.convertDBToBioAPI_Data_All(chunkIds);

                // Set up identify parameters
                JAVA_sint32 maxFRRRequested = new JAVA_sint32();
                maxFRRRequested.value = PalmSecureConstant.JAVA_PvAPI_MATCHING_LEVEL_NORMAL;
                JAVA_sint32 farRequested = new JAVA_sint32();
                farRequested.value = PalmSecureConstant.JAVA_BioAPI_FALSE;
                JAVA_uint32 farPrecedence = new JAVA_uint32();
                farPrecedence.value = PalmSecureConstant.JAVA_BioAPI_FALSE;
                JAVA_uint32 totalNumberOfTemplates = new JAVA_uint32();
                totalNumberOfTemplates.value = chunkIds.size();
                JAVA_uint32 maxNumberOfResults = new JAVA_uint32();
                maxNumberOfResults.value = 1;
                JAVA_uint32 numberReturned = new JAVA_uint32();
                JAVA_BioAPI_CANDIDATE[] candidates = new JAVA_BioAPI_CANDIDATE[1];
                candidates[0] = new JAVA_BioAPI_CANDIDATE();
                JAVA_sint32 timeout = new JAVA_sint32();
                timeout.value = 0;
                JAVA_sint32 templateUpdate = new JAVA_sint32();
                templateUpdate.value = PalmSecureConstant.JAVA_BioAPI_FALSE;

                try {
                    stResult.result = palmsecureIf.JAVA_BioAPI_Identify(
                        moduleHandle, maxFRRRequested, farRequested, farPrecedence,
                        population, totalNumberOfTemplates, maxNumberOfResults,
                        numberReturned, candidates, timeout, templateUpdate
                    );

                    if (stResult.result == PalmSecureConstant.JAVA_BioAPI_OK && 
                        numberReturned.value > 0 && candidates[0] != null) {
                        
                        int matchedIndex = -1;
                        try {
                            // Extract index using reflection (BIRInArray field)
                            java.lang.reflect.Field field = candidates[0].getClass().getField("BIRInArray");
                            matchedIndex = (int) field.getLong(candidates[0]);
                        } catch (Exception e) {
                            // Fallback scan for index fields
                            try {
                                for (java.lang.reflect.Field f : candidates[0].getClass().getFields()) {
                                    if (f.getName().toLowerCase().contains("index") || f.getName().toLowerCase().contains("bir")) {
                                        Object val = f.get(candidates[0]);
                                        if (val instanceof Number) {
                                            matchedIndex = ((Number)val).intValue();
                                            break;
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }

                        if (matchedIndex >= 0 && matchedIndex < chunkIds.size()) {
                            String matchedId = chunkIds.get(matchedIndex);
                            stResult.authenticated = true;
                            stResult.userId = new java.util.ArrayList<String>();
                            stResult.userId.add(matchedId);
                            matchFound = true;
                            if (BuildConfig.DEBUG) Log.d(TAG, "Match found in chunk: " + matchedId);
                            break; 
                        }
                    }
                } catch (PalmSecureException e) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "Identify API call failed", e);
                    stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
                    stResult.pseErrNumber = e.ErrNumber;
                    break; 
                }
                
                if (stResult.result != PalmSecureConstant.JAVA_BioAPI_OK) break;
            }

            if (!matchFound) {
                stResult.authenticated = false;
            }

        } catch (PsAplException | PalmSecureException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Identify failed", e);
            stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
            stResult.authenticated = false;
        } catch (Exception e) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Identify run error", e);
            stResult.result = PalmSecureConstant.JAVA_BioAPI_ERRCODE_FUNCTION_FAILED;
            stResult.authenticated = false;
        }

        Ps_Sample_Apl_Java_NotifyResult_Identify(stResult);
    }
}
