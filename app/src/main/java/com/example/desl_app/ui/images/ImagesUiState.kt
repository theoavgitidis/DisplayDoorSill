package com.example.desl_app.ui.images

import android.net.Uri
import com.example.desl_app.data.model.UploadImage


data class ImagesUiState(
    val selectedPreset: UploadImage? = null,
    val galleryImageUri: Uri? = null,
    val isUploading: Boolean = false,
    val message: String? = null
)
