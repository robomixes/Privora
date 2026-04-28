package com.privateai.camera.ui.vault

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.privateai.camera.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-app PDF viewer using Android's built-in PdfRenderer.
 *
 * Reads from a decrypted temp file the caller has placed in the app's private
 * cacheDir. The file never leaves the app sandbox — no FileProvider grant, no
 * external viewer. Caller is responsible for deleting the temp file when the
 * user backs out.
 *
 * Pages are rendered lazily as they scroll into view. Pinch-to-zoom applies
 * uniformly to the page column via graphicsLayer. PdfRenderer requires
 * single-page-at-a-time access, so all renders are serialised through a Mutex.
 */
@Composable
fun PdfViewerScreen(
    pdfFile: File,
    title: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // Open PdfRenderer for the lifetime of the screen.
    val renderer = remember(pdfFile) {
        try {
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd) to pfd
        } catch (e: Exception) {
            null
        }
    }
    DisposableEffect(renderer) {
        onDispose {
            try { renderer?.first?.close() } catch (_: Exception) {}
            try { renderer?.second?.close() } catch (_: Exception) {}
        }
    }

    val pageCount = renderer?.first?.pageCount ?: 0
    val renderMutex = remember { Mutex() }
    // Cache of rendered page bitmaps keyed by page index.
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    // Cached width/height aspect per page so the placeholder reserves space
    // before the bitmap is rendered (avoids scroll jumps).
    val pageAspects = remember { mutableStateMapOf<Int, Float>() }

    // Read aspect ratios up front (cheap — only opens metadata, no rendering).
    LaunchedEffect(renderer, pageCount) {
        val r = renderer?.first ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            for (i in 0 until pageCount) {
                if (pageAspects.containsKey(i)) continue
                renderMutex.withLock {
                    try {
                        val p = r.openPage(i)
                        pageAspects[i] = p.width.toFloat() / p.height.toFloat()
                        p.close()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    var scale by remember { mutableStateOf(1f) }
    val listState = rememberLazyListState()
    val currentPage by remember {
        derivedStateOf { (listState.firstVisibleItemIndex + 1).coerceAtMost(pageCount.coerceAtLeast(1)) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF202020))) {
        if (renderer == null || pageCount == 0) {
            Text(
                stringResource(R.string.failed_to_open_pdf, ""),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        } else {
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = statusBarTop + 64.dp, bottom = 16.dp)
            ) {
                itemsIndexed((0 until pageCount).toList()) { _, pageIndex ->
                    PdfPage(
                        renderer = renderer.first,
                        renderMutex = renderMutex,
                        pageIndex = pageIndex,
                        aspect = pageAspects[pageIndex],
                        existingBitmap = pageBitmaps[pageIndex],
                        onRendered = { bmp -> pageBitmaps[pageIndex] = bmp },
                        targetWidthPx = with(density) { 1080.dp.toPx() }.toInt() // render at ~1080px wide for sharpness
                    )
                }
            }
        }

        // Top bar overlay: back + page counter + share
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            if (pageCount > 0) {
                Text(
                    text = stringResource(R.string.pdf_page_counter, currentPage, pageCount),
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            IconButton(
                onClick = {
                    try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_pdf)))
                    } catch (_: Exception) {}
                },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.IosShare, stringResource(R.string.share_pdf), tint = Color.White)
            }
        }
    }
}

@Composable
private fun PdfPage(
    renderer: PdfRenderer,
    renderMutex: Mutex,
    pageIndex: Int,
    aspect: Float?,
    existingBitmap: Bitmap?,
    onRendered: (Bitmap) -> Unit,
    targetWidthPx: Int
) {
    LaunchedEffect(pageIndex, targetWidthPx) {
        if (existingBitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            renderMutex.withLock {
                try {
                    val page = renderer.openPage(pageIndex)
                    val w = targetWidthPx
                    val h = (w * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(AColor.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    onRendered(bmp)
                } catch (_: Exception) {}
            }
        }
    }

    val ratio = aspect ?: existingBitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 0.707f // A4 portrait fallback
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (existingBitmap != null) {
            Image(
                bitmap = existingBitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(8.dp))
        }
    }
}
