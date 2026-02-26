package com.fujitsu.frontech.palmsecure_sample.data;

import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.Call;

public interface ApiService {
    @POST("api/device/insertVeinData")
    Call<ResponseBody> send(@Body Map<String, Object> body);
}
