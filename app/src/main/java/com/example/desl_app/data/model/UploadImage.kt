package com.example.desl_app.data.model

import androidx.annotation.DrawableRes


data class UploadImage (
    val id: String,
    @DrawableRes val drawableRes: Int,
    val fileNameOnPi: String,
    val title: String
)