// File: ui/images/ImagesViewModel.kt
package com.example.desl_app.ui.images

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.desl_app.data.model.UploadImage
import com.example.desl_app.data.repository.ApiResult
import com.example.desl_app.data.repository.PiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImagesViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = PiRepository()

    private val _state = MutableStateFlow(ImagesUiState())
    val state: StateFlow<ImagesUiState> = _state.asStateFlow()

    fun selectPreset(image: UploadImage) {
        _state.value = _state.value.copy(
            selectedPreset = image,
            galleryImageUri = null,  // Clear gallery selection
            message = null
        )
    }

    fun selectGalleryImage(uri: Uri) {
        _state.value = _state.value.copy(
            galleryImageUri = uri,
            selectedPreset = null,  // Clear preset selection
            message = null
        )
    }

    fun uploadSelected() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploading = true, message = null)

            val result = when {
                // Upload gallery image
                _state.value.galleryImageUri != null -> {
                    repository.uploadUriImage(
                        context = getApplication(),
                        uri = _state.value.galleryImageUri!!,
                        fileName = "gallery_image.jpg"
                    )
                }
                // Upload preset image
                _state.value.selectedPreset != null -> {
                    val selected = _state.value.selectedPreset!!
                    repository.uploadDrawableImage(
                        context = getApplication(),
                        drawableRes = selected.drawableRes,
                        fileName = selected.fileNameOnPi
                    )
                }
                else -> ApiResult.Error("No image selected")
            }

            _state.value = _state.value.copy(
                isUploading = false,
                message = when (result) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Error -> result.message
                    is ApiResult.NotConnected -> "Nicht mit Pi verbunden"
                }
            )
        }
    }
}