package com.example.desl_app.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object PiApiService {

    private var currentBaseUrl: String? = null
    private var apiInstance: PiApi? = null

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun getApi(ip: String, port: Int = 5000): PiApi {
        val baseUrl = "http://$ip:$port/"

        // Reuse instance if URL hasn't changed
        if (baseUrl == currentBaseUrl && apiInstance != null) {
            return apiInstance!!
        }

        currentBaseUrl = baseUrl
        apiInstance = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PiApi::class.java)

        return apiInstance!!
    }
}