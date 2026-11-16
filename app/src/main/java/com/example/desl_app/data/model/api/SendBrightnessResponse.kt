package com.example.desl_app.data.model.api

import com.google.gson.annotations.SerializedName

data class SendBrightnessResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String? = null
)