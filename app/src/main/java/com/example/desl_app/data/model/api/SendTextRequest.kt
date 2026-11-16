package com.example.desl_app.data.model.api

import com.google.gson.annotations.SerializedName

data class SendTextRequest(
    @SerializedName("text") val text: String
)

