// File: data/model/api/ConnectionResult.kt
package com.example.desl_app.data.model.api

import com.google.gson.annotations.SerializedName

data class ConnectionResult(
    @SerializedName("ip") val ip: String,
    @SerializedName("port") val port: Int
)