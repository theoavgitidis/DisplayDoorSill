package com.example.desl_app.data.model.api

import com.google.gson.annotations.SerializedName

data class SendColorRequest(
    @SerializedName("color") val color: String
)