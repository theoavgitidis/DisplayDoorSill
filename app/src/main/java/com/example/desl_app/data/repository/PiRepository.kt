package com.example.desl_app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.desl_app.data.model.api.SendBrightnessRequest
import com.example.desl_app.data.model.api.SendColorRequest
import com.example.desl_app.data.model.api.SendTextRequest
import com.example.desl_app.data.network.PiApiService
import com.example.desl_app.data.network.PiConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.ByteArrayOutputStream

class PiRepository {

    private fun markDisconnected() {
        PiConnectionState.clearConnection()
    }

    /**
     * Tries to reach the Pi at the given IP via HTTP GET /status.
     * Returns true if the Pi responds (any 2xx), false otherwise.
     */
    suspend fun pingPi(ip: String, port: Int = 5000): Boolean = withContext(Dispatchers.IO) {
        try {
            val api = PiApiService.getApi(ip, port)
            val response = api.getStatus()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendText(text: String): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val ip = PiConnectionState.targetIp.value
                ?: return@withContext ApiResult.NotConnected
            val port = PiConnectionState.targetPort.value

            val api = PiApiService.getApi(ip, port)
            val request = SendTextRequest(text = text)

            val res = api.sendText(request)

            if (res.success) {
                ApiResult.Success(res.message ?: "Text sent successfully")
            } else {
                ApiResult.Error(res.message ?: "Unknown error")
            }
        } catch (e: java.net.UnknownHostException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: java.net.ConnectException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: java.net.SocketTimeoutException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: HttpException) {
            ApiResult.Error("HTTP ${e.code()}: ${e.message()}", e.code())
        } catch (e: Exception) {
            ApiResult.Error("Connection error: ${e.message}")
        }
    }

    suspend fun sendColor(hexColor: String): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val ip = PiConnectionState.targetIp.value
                ?: return@withContext ApiResult.NotConnected
            val port = PiConnectionState.targetPort.value

            val api = PiApiService.getApi(ip, port)
            val request = SendColorRequest(color = hexColor)

            val res = api.setColor(request)

            if (res.success) {
                ApiResult.Success(res.message ?: "Color set successfully")
            } else {
                ApiResult.Error(res.message ?: "Unknown error")
            }
        } catch (e: java.net.UnknownHostException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: java.net.ConnectException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: java.net.SocketTimeoutException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: HttpException) {
            ApiResult.Error("HTTP ${e.code()}: ${e.message()}", e.code())
        } catch (e: Exception) {
            ApiResult.Error("Connection error: ${e.message}")
        }
    }

    suspend fun sendBrightness(brightness: Int): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val ip = PiConnectionState.targetIp.value
                ?: return@withContext ApiResult.NotConnected
            val port = PiConnectionState.targetPort.value

            val api = PiApiService.getApi(ip, port)
            val request = SendBrightnessRequest(brightness = brightness.coerceIn(0, 100))

            val res = api.setBrightness(request)

            if (res.success) {
                ApiResult.Success(res.message ?: "Brightness set successfully")
            } else {
                ApiResult.Error(res.message ?: "Unknown error")
            }
        } catch (e: java.net.UnknownHostException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: java.net.ConnectException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: java.net.SocketTimeoutException) {
            markDisconnected()
            ApiResult.NotConnected
        } catch (e: HttpException) {
            ApiResult.Error("HTTP ${e.code()}: ${e.message()}", e.code())
        } catch (e: Exception) {
            ApiResult.Error("Connection error: ${e.message}")
        }
    }

    suspend fun uploadDrawableImage(
        context: Context,
        drawableRes: Int,
        fileName: String
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val ip = PiConnectionState.targetIp.value
                ?: return@withContext ApiResult.NotConnected
            val port = PiConnectionState.targetPort.value

            val bitmap = BitmapFactory.decodeResource(context.resources, drawableRes)
                ?: return@withContext ApiResult.Error("Failed to load image")

            uploadBitmapInternal(ip, port, bitmap, fileName)
        } catch (e: Exception) {
            ApiResult.Error("Upload error: ${e.message}")
        }
    }

    suspend fun uploadUriImage(
        context: Context,
        uri: Uri,
        fileName: String
    ): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val ip = PiConnectionState.targetIp.value
                ?: return@withContext ApiResult.NotConnected
            val port = PiConnectionState.targetPort.value

            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ApiResult.Error("Cannot open image")

            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) {
                return@withContext ApiResult.Error("Failed to decode image")
            }

            uploadBitmapInternal(ip, port, bitmap, fileName)
        } catch (e: Exception) {
            ApiResult.Error("Upload error: ${e.message}")
        }
    }

    private suspend fun uploadBitmapInternal(
        ip: String,
        port: Int,
        bitmap: Bitmap,
        fileName: String
    ): ApiResult<String> {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
        val bytes = baos.toByteArray()

        val api = PiApiService.getApi(ip, port)

        val jpegMediaType = "image/jpeg".toMediaType()
        val fileBody = bytes.toRequestBody(jpegMediaType)
        val filePart = MultipartBody.Part.createFormData("file", fileName, fileBody)
        val filenamePart = fileName.toRequestBody("text/plain".toMediaType())

        val response = api.uploadImage(filenamePart, filePart)

        return if (response.isSuccessful) {
            val body = response.body()
            if (body?.success == true) {
                ApiResult.Success(body.message ?: "Upload successful: $fileName")
            } else {
                ApiResult.Error(body?.message ?: "Upload failed", response.code())
            }
        } else {
            ApiResult.Error("Upload failed: HTTP ${response.code()}", response.code())
        }
    }
}