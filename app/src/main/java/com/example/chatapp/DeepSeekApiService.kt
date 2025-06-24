package com.example.chatapp

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

// DeepSeek API 服务接口
interface DeepSeekApiService {
    @Streaming
    @POST("v1/chat/completions")
    fun getChatResponse(
        @Header("Authorization") authHeader: String,
        @Body request: ChatRequest
    ): Call<ResponseBody>
}