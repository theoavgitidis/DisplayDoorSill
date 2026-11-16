package com.example.desl_app.data.network

import com.example.desl_app.data.model.api.SendBrightnessRequest
import com.example.desl_app.data.model.api.SendBrightnessResponse
import com.example.desl_app.data.model.api.SendColorRequest
import com.example.desl_app.data.model.api.SendColorResponse
import com.example.desl_app.data.model.api.SendTextRequest
import com.example.desl_app.data.model.api.SendTextResponse
import com.example.desl_app.data.model.api.UploadImageResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface PiApi {

    @POST("send-text")
    suspend fun sendText(
        @Body request: SendTextRequest
    ): SendTextResponse

    @POST("set-color")
    suspend fun setColor(
        @Body request: SendColorRequest
    ): SendColorResponse

    @POST("set-brightness")
    suspend fun setBrightness(
        @Body request: SendBrightnessRequest
    ): SendBrightnessResponse

    @Multipart
    @POST("upload")
    suspend fun uploadImage(
        @Part("filename") filename: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<UploadImageResponse>

    @GET("status")
    suspend fun getStatus(): Response<okhttp3.ResponseBody>
}