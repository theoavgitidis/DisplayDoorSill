package com.example.desl_app.data.model.api

import com.google.gson.annotations.SerializedName

data class SendBrightnessRequest(
    @SerializedName("brightness") val brightness: Int  // 0-100
)