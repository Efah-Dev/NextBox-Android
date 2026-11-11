package dev.efah.nextbox.ui.screens

import dev.efah.nextbox.ui.T
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.efah.nextbox.ui.BarIcon
import dev.efah.nextbox.ui.LocalBottomInset
import dev.efah.nextbox.ui.LocalToast
import dev.efah.nextbox.ui.NbAlertDialog
import dev.efah.nextbox.ui.NbIcons
import dev.efah.nextbox.ui.TopBar
import dev.efah.nextbox.ui.theme.Nb
import dev.efah.nextbox.ui.theme.NbType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val decodeHints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")

private fun decode(source: LuminanceSource): String? {
    val reader = QRCodeReader()
    // normal first, then inverted (light code on a dark background)
    for (s in listOf(source, source.invert())) {
        try {
            return reader.decode(BinaryBitmap(HybridBinarizer(s)), decodeHints).text
        } catch (_: Exception) {
            reader.reset()
        }
    }
    return null
}

/** Decodes the QR code in one camera frame, or returns null. */
private fun decodeFrame(image: ImageProxy): String? {
    val plane = image.planes[0]
    val w = image.width
    val h = image.height
    val buffer = plane.buffer
    val data = ByteArray(w * h)
    if (plane.rowStride == w) {
        buffer.get(data, 0, minOf(buffer.remaining(), data.size))
    } else {
        // rows are padded: copy them one by one
        for (row in 0 until h) {
            buffer.position(row * plane.rowStride)
            buffer.get(data, row * w, minOf(w, buffer.remaining()))
        }
    }
    return decode(PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false))
}

/** Scans a QR code with the camera, or reads one from a picture, and reports its text. */
@Composable
fun ScanScreen(onResult: (String) -> Unit, onClose: () -> Unit) {
    val c = Nb.c
    val ctx = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val result by rememberUpdatedState(onResult)
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    // large photos are downsampled: QR codes survive it and decoding gets faster
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    decode(RGBLuminanceSource(bmp.width, bmp.height, px))
                }.getOrNull()
            }
            if (text == null) toast(T("没有在图片里找到二维码")) else result(text)
        }
    }

    Column(Modifier.fillMaxSize().padding(bottom = LocalBottomInset.current)) {
        TopBar(T("扫描二维码"), leading = { BarIcon(NbIcons.Back, T("返回"), onClose) })
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (granted) {
                    val done = remember { AtomicBoolean(false) }
                    val executor = remember { Executors.newSingleThreadExecutor() }
                    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
                    DisposableEffect(owner) {
                        val future = ProcessCameraProvider.getInstance(ctx)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { image ->
                                val text = if (done.get()) null else runCatching { decodeFrame(image) }.getOrNull()
                                image.close()
                                if (text != null && done.compareAndSet(false, true)) {
                                    previewView.post { result(text) }
                                }
                            }
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            }.onFailure { toast(T("无法打开相机")) }
                        }, ContextCompat.getMainExecutor(ctx))
                        onDispose {
                            runCatching { future.get().unbindAll() }
                            executor.shutdown()
                        }
                    }
                    AndroidView({ previewView }, Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize().padding(36.dp)) {
                        // corner marks of the scan area
                        val len = size.minDimension * 0.16f
                        val stroke = Stroke(4.dp.toPx())
                        for ((x, y) in listOf(0f to 0f, size.width to 0f, 0f to size.height, size.width to size.height)) {
                            val dx = if (x == 0f) len else -len
                            val dy = if (y == 0f) len else -len
                            drawLine(Color.White, Offset(x, y), Offset(x + dx, y), stroke.width)
                            drawLine(Color.White, Offset(x, y), Offset(x, y + dy), stroke.width)
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(T("需要相机权限才能扫码"), color = Color.White, style = NbType.body)
                        Button(
                            onClick = { permission.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = c.accentStrong),
                        ) { Text(T("允许使用相机")) }
                    }
                }
            }
            Text(
                T("把节点或订阅的二维码放进取景框"), style = NbType.body, color = c.muted, textAlign = TextAlign.Center,
            )
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(T("从相册选择图片"), color = c.accentInk)
            }
        }
    }
}

/** Shows a node's share link as a QR code. */
@Composable
fun QrDialog(name: String, link: String, onDismiss: () -> Unit) {
    val toast = LocalToast.current
    val matrix: BitMatrix? = remember(link) {
        runCatching {
            QRCodeWriter().encode(
                link, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8"),
            )
        }.getOrNull()
    }
    NbAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (matrix == null) {
                    Text(T("这个节点的分享链接太长，无法生成二维码"))
                } else {
                    // always dark on white, whatever the theme: scanners expect it
                    Canvas(Modifier.size(248.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
                        val cell = size.width / matrix.width
                        for (y in 0 until matrix.height) {
                            var x = 0
                            while (x < matrix.width) {
                                if (matrix[x, y]) {
                                    var run = 1
                                    while (x + run < matrix.width && matrix[x + run, y]) run++
                                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell * run + 0.5f, cell + 0.5f))
                                    x += run
                                } else {
                                    x++
                                }
                            }
                        }
                    }
                    Text(T("二维码里包含节点的密码，只分享给信任的人"), style = NbType.small, color = Nb.c.muted, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = { TextButton(onClick = { copyToClipboard(link); toast(T("已复制分享链接")); onDismiss() }) { Text(T("复制链接")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(T("关闭")) } },
    )
}
