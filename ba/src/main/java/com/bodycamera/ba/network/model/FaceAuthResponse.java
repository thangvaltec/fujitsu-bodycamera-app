package com.bodycamera.ba.network.model;

import com.google.gson.annotations.SerializedName;

/**
 * 顔認証レスポンス用データモデル
 * サーバーからのJSONレスポンスをマッピングします。
 */
public class FaceAuthResponse {

    @SerializedName("status")
    public int status;

    @SerializedName("deviceId")
    public String deviceId;

    @SerializedName("policeId")
    public String policeId;

    @SerializedName("similarity")
    public String similarity;

    @SerializedName("name")
    public String name;

    @SerializedName("real_id")
    public String realId;

    @SerializedName("data")
    public String data;

    @SerializedName("message")
    public String message;
}
