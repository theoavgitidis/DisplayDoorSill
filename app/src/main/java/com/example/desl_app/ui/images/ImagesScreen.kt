// ImagesScreen.kt
package com.example.desl_app.ui.images

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.desl_app.R
import com.example.desl_app.data.model.UploadImage
import com.example.desl_app.ui.theme.GreyBorder
import com.example.desl_app.ui.theme.DisplayBlue
import com.example.desl_app.ui.theme.DisplayButton
import com.example.desl_app.ui.theme.DisplayHeader
import com.example.desl_app.ui.theme.DisplayPrimaryText
import com.example.desl_app.ui.theme.DisplaySecondaryText
import com.example.desl_app.ui.theme.SquareShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

private val selectableImages = listOf(
    UploadImage("aurora", R.drawable.aurora, "aurora.jpg", "Aurora"),
    UploadImage("grid", R.drawable.grid, "grid.jpg", "Display Grid"),
)

// Sealed class to represent all button items
private sealed class ImageButtonItem {
    data class Upload(val title: String = "Custom Image") : ImageButtonItem()
    data class Preset(val image: UploadImage) : ImageButtonItem()
}

@Composable
fun ImagesScreen(
    viewModel: ImagesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val state = viewModel.state.collectAsState().value
    val configuration = LocalConfiguration.current

    val screenHeightDp = configuration.screenHeightDp.dp

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectGalleryImage(it) }
    }

    // Create list of all items
    val allItems = remember {
        listOf(ImageButtonItem.Upload()) + selectableImages.map { ImageButtonItem.Preset(it) }
    }

    val pagerState = rememberPagerState(pageCount = { allItems.size })

    // Auto-select when page snaps
    LaunchedEffect(pagerState.currentPage) {
        when (val item = allItems[pagerState.currentPage]) {
            is ImageButtonItem.Upload -> { }
            is ImageButtonItem.Preset -> {
                viewModel.selectPreset(item.image)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DisplayHeader("Choose Background Image")

        // Calculate dimensions
        val reservedHeight = 500.dp
        val availableHeight = screenHeightDp - reservedHeight

        val buttonAspectRatio = 100f / 1024f
        val imageHeight = availableHeight * 0.70f  // Reduced for title space
        val imageWidth = imageHeight * buttonAspectRatio
        val cardHeight = availableHeight * 0.85f  // Total card height including title

        // Current item title
        val currentTitle = when (val item = allItems[pagerState.currentPage]) {
            is ImageButtonItem.Upload -> item.title
            is ImageButtonItem.Preset -> item.image.title
        }

        // Title above the carousel
        DisplayPrimaryText(
            text = currentTitle,
            modifier = Modifier.fillMaxWidth(),
            color = Color.Black,

        )

        Spacer(modifier = Modifier.height(8.dp))

        // Horizontal Pager
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
        ) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = (configuration.screenWidthDp.dp - imageWidth) / 2),
                pageSpacing = 68.dp,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

                when (val item = allItems[page]) {
                    is ImageButtonItem.Upload -> {
                        VerticalImageButton(
                            isUploadButton = true,
                            isSelected = state.galleryImageUri != null,
                            onClick = { galleryLauncher.launch("image/*") },
                            galleryImageUri = state.galleryImageUri,
                            buttonWidth = imageWidth,
                            buttonHeight = imageHeight,
                            pageOffset = pageOffset
                        )
                    }
                    is ImageButtonItem.Preset -> {
                        val selected = state.selectedPreset?.id == item.image.id &&
                                state.galleryImageUri == null

                        VerticalImageButton(
                            isUploadButton = false,
                            isSelected = selected,
                            onClick = { viewModel.selectPreset(item.image) },
                            drawableRes = item.image.drawableRes,
                            contentDescription = item.image.title,
                            buttonWidth = imageWidth,
                            buttonHeight = imageHeight,
                            pageOffset = pageOffset
                        )
                    }
                }
            }
        }

        // Scroll indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(allItems.size) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (index == pagerState.currentPage) 12.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == pagerState.currentPage) DisplayBlue else GreyBorder
                        )
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        DisplayButton(
            text = if (state.isUploading) "Uploading..." else "Set Background",
            onClick = { viewModel.uploadSelected() },
            enabled = (state.selectedPreset != null ||
                    state.galleryImageUri != null) &&
                    !state.isUploading,
            modifier = Modifier.fillMaxWidth()
        )

        state.message?.let {
            Spacer(modifier = Modifier.height(8.dp))
            DisplaySecondaryText(it, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun VerticalImageButton(
    isUploadButton: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    buttonWidth: androidx.compose.ui.unit.Dp,
    buttonHeight: androidx.compose.ui.unit.Dp,
    pageOffset: Float,
    modifier: Modifier = Modifier,
    galleryImageUri: Uri? = null,
    drawableRes: Int? = null,
    contentDescription: String? = null
) {

    val scale = lerp(
        start = 0.85f,
        stop = 1f,
        fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
    )

    val alpha = lerp(
        start = 0.5f,
        stop = 1f,
        fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .requiredWidth(buttonWidth)
            .requiredHeight(buttonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        shape = SquareShape,
        border = if (isSelected) BorderStroke(3.dp, DisplayBlue) else BorderStroke(1.dp, GreyBorder)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(SquareShape),
            contentAlignment = Alignment.Center
        ) {
            when {
                isUploadButton && galleryImageUri != null -> {
                    RotatedImage(
                        model = galleryImageUri,
                        modifier = Modifier.fillMaxSize()
                    )
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "selected",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .size(40.dp)
                    )
                }

                isUploadButton -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = "Upload from gallery",
                            modifier = Modifier.size(64.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        DisplaySecondaryText(
                            "Upload",
                            color = Color.Black
                        )
                    }
                }

                drawableRes != null -> {
                    RotatedImage(
                        drawableRes = drawableRes,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "selected",
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RotatedImage(
    modifier: Modifier = Modifier,
    model: Any? = null,
    drawableRes: Int? = null,
    contentDescription: String? = null
) {
    val context = LocalContext.current

    val bitmap = remember(drawableRes) {
        if (drawableRes != null) {
            BitmapFactory.decodeResource(context.resources, drawableRes)
        } else null
    }

    var uriBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(model) {
        if (model is Uri) {
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(model)
                    uriBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val finalBitmap = bitmap ?: uriBitmap

    if (finalBitmap != null) {
        Canvas(modifier = modifier) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val imageWidth = finalBitmap.width.toFloat()
            val imageHeight = finalBitmap.height.toFloat()

            val rotatedImageWidth = imageHeight
            val rotatedImageHeight = imageWidth

            val scaleFactorX = canvasWidth / rotatedImageWidth
            val scaleFactorY = canvasHeight / rotatedImageHeight
            val scaleFactor = maxOf(scaleFactorX, scaleFactorY)

            drawContext.canvas.nativeCanvas.apply {
                save()
                translate(canvasWidth / 2, canvasHeight / 2)
                rotate(90f)
                scale(scaleFactor, scaleFactor)
                drawBitmap(
                    finalBitmap,
                    -imageWidth / 2,
                    -imageHeight / 2,
                    null
                )
                restore()
            }
        }
    }
}