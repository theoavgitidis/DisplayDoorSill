// File: data/model/api/UploadImageResponse.kt
package com.example.desl_app.data.model.api

import com.google.gson.annotations.SerializedName

data class UploadImageResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("filename") val filename: String? = null,
    @SerializedName("message") val message: String?
)