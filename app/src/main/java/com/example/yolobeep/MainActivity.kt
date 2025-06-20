@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.yolobeep

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.yolobeep.model.AStarPathfinder
import com.example.yolobeep.model.GridMapLoader
import com.example.yolobeep.ui.GridMapView
import com.example.yolobeep.ui.LabeledMapView
import com.example.yolobeep.ui.theme.YoloBeepTheme
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.AssistWalker
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.example.yolobeep.ui.theme.MainBlue
import com.example.yolobeep.ui.theme.LightBlue
import com.example.yolobeep.ui.theme.AccentTeal
import com.example.yolobeep.ui.theme.BgGray
import com.example.yolobeep.ui.theme.DarkGray
import com.example.yolobeep.ui.theme.CardWhite
import com.example.yolobeep.ui.theme.CardWhiteTranslucent

sealed class Screen {
    object Home : Screen()
    object Detection : Screen()
    object Mapping : Screen()
}

class MainActivity : ComponentActivity() {
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        setContent {
            var screen by remember { mutableStateOf<Screen>(Screen.Home) }
            YoloBeepTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    when (screen) {
                        is Screen.Home -> VaviWelcomeScreen(
                            onDetection = { screen = Screen.Detection },
                            onMapping = { screen = Screen.Mapping }
                        )
                        is Screen.Detection -> CameraPreviewView(
                            modifier = Modifier.padding(innerPadding),
                            onBack = { screen = Screen.Home }
                        )
                        is Screen.Mapping -> GridNavigationScreen(
                            modifier = Modifier.padding(innerPadding),
                            tts = tts,
                            onBack = { screen = Screen.Home }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

@Composable
fun VaviWelcomeScreen(
    onDetection: () -> Unit,
    onMapping: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(MainBlue, LightBlue, BgGray)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhiteTranslucent)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 40.dp, vertical = 48.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentTeal, MainBlue.copy(alpha = 0.7f)),
                                radius = 120f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Explore,
                        contentDescription = "VAVI Logo",
                        tint = CardWhite,
                        modifier = Modifier.size(60.dp)
                    )
                }
                Spacer(Modifier.height(36.dp))
                // App name
                Text(
                    text = "VAVI",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 38.sp
                    ),
                    color = MainBlue,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(18.dp))
                // One-sentence summary
                Text(
                    text = "Empowering independence through AI-powered navigation for the visually impaired.",
                    style = MaterialTheme.typography.titleMedium,
                    color = DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .align(Alignment.CenterHorizontally),
                    lineHeight = 22.sp
                )
                Spacer(Modifier.height(24.dp))
                // Two-line description
                Text(
                    text = "VAVI uses your phone's camera and smart indoor maps to help you safely explore and navigate any environment.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = DarkGray.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .align(Alignment.CenterHorizontally),
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(44.dp))
                // Buttons
                VaviActionButton(
                    text = "Real-Time Detection",
                    icon = Icons.Filled.Visibility,
                    bgColor = AccentTeal,
                    onClick = onDetection
                )
                Spacer(Modifier.height(32.dp))
                VaviActionButton(
                    text = "Indoor Mapping",
                    icon = Icons.Filled.Map,
                    bgColor = MainBlue,
                    onClick = onMapping
                )
            }
        }
    }
}

@Composable
fun VaviActionButton(
    text: String,
    icon: ImageVector,
    bgColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onClickLabel = text
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = CardWhite,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = CardWhite,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

lateinit var tflite: Interpreter

@Composable
fun GridNavigationScreen(modifier: Modifier = Modifier, tts: TextToSpeech?, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val (rows, cols) = remember {
        LabeledMapView(context).loadGridSizeFromJson(context)
    }
    val grid = remember { GridMapLoader.loadGridMap(context) }
    var start by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var end by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var path by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var currentIdx by remember { mutableStateOf(0) }
    val current: Pair<Int, Int>? = if (path.isNotEmpty() && currentIdx in path.indices) path[currentIdx] else null
    var setStartMode by remember { mutableStateOf(true) } // true: set start, false: set end
    var isPathfinding by remember { mutableStateOf(false) }
    var lastPathJob by remember { mutableStateOf<Job?>(null) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

    // Debounced pathfinding
    LaunchedEffect(start, end) {
        if (start != null && end != null && start != end) {
            isPathfinding = true
            lastPathJob?.cancel()
            val job = coroutineScope.launch(Dispatchers.Default) {
                val result = AStarPathfinder.findPath(grid, start!!, end!!)
                withContext(Dispatchers.Main) {
                    path = result
                    currentIdx = 0
                    isPathfinding = false
                    println("Path calculated: ${result.size} steps, from ${result.firstOrNull()} to ${result.lastOrNull()}")
                }
            }
            lastPathJob = job
        }
    }

    BackHandler(enabled = true) {
        onBack?.invoke()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgGray),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Modern App Bar
        CenterAlignedTopAppBar(
            title = { Text("A Block 3rd Floor", style = MaterialTheme.typography.titleLarge, color = MainBlue) },
            navigationIcon = {
                IconButton(onClick = { onBack?.invoke() }) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = MainBlue)
                }
            },
            colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = CardWhite,
                titleContentColor = MainBlue
            )
        )
        Spacer(Modifier.height(8.dp))

        // Confirmation dialog for reset
        var showDialog by remember { mutableStateOf(false) }
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Reset Path", color = MainBlue) },
                text = { Text("Are you sure you want to reset the start/end points and path?", color = DarkGray) },
                confirmButton = {
                    TextButton(onClick = {
                        start = null
                        end = null
                        path = emptyList()
                        currentIdx = 0
                        showDialog = false
                    }) { Text("Yes", color = AccentTeal) }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("No", color = MainBlue) }
                },
                containerColor = CardWhite
            )
        }

        // Map in a card with shadow and rounded corners
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = CardWhite)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        LabeledMapView(ctx).apply {
                            this.gridRows = rows
                            this.gridCols = cols
                            this.showGridOverlay = false // Disable overlay for normal use
                            this.grid = grid
                            this.startPosition = start
                            this.endPosition = end
                            this.onCellTapped = { row, col ->
                                val now = System.currentTimeMillis()
                                if (now - lastTapTime < 300) {
                                    // debounce: ignore rapid taps
                                } else {
                                    lastTapTime = now
                                    if (start == null) {
                                        start = row to col
                                        setStartMode = false // Switch to Set End
                                    } else if (end == null && (row to col) != start) {
                                        end = row to col
                                    } else if (start != null && end != null) {
                                        // If tap on end, move start; else, move end
                                        if (row to col == end) {
                                            start = row to col
                                            if (end == start) end = null
                                            setStartMode = false
                                        } else {
                                            end = row to col
                                            if (start == end) start = null
                                            setStartMode = true
                                        }
                                    }
                                    // path will be computed by LaunchedEffect
                                }
                            }
                        }
                    },
                    update = { view ->
                        view.userPosition = current ?: Pair(rows / 2, cols / 2)
                        view.path = path
                        view.targetPosition = end
                        view.startPosition = start
                        view.endPosition = end
                        view.showGridOverlay = false // Disable overlay for normal use
                        view.grid = grid
                        view.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Loading indicator
        if (isPathfinding) {
            Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                Text("Calculating path...", style = MaterialTheme.typography.bodyMedium, color = MainBlue)
            }
        }

        // Floating Action Button for Reset
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MainBlue,
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = CardWhite)
            }
        }
    }
}

fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(modelFileName)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
}

class YOLOAnalyzer(private val interpreter: Interpreter) : ImageAnalysis.Analyzer {
    override fun analyze(image: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(image)
            val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
            val inputBuffer = convertBitmapToByteBuffer(resized)
            val outputBuffer = Array(1) { Array(25200) { FloatArray(85) } }

            interpreter.run(inputBuffer, outputBuffer)

            val predictions = outputBuffer[0]
            val personDetected = predictions.any { it[4] > 0.5 && it[5].toInt() == 0 }

            if (personDetected) {
                Log.d("YOLO", "Person detected!")
                playStereoBeep(0.3f)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}

fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 100, out)
    val imageBytes = out.toByteArray()
    return android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
    val inputSize = 640
    val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
    byteBuffer.order(ByteOrder.nativeOrder())

    val intValues = IntArray(inputSize * inputSize)
    bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    var pixel = 0
    for (i in 0 until inputSize) {
        for (j in 0 until inputSize) {
            val value = intValues[pixel++]
            byteBuffer.putFloat(((value shr 16 and 0xFF) / 255.0f))
            byteBuffer.putFloat(((value shr 8 and 0xFF) / 255.0f))
            byteBuffer.putFloat(((value and 0xFF) / 255.0f))
        }
    }
    return byteBuffer
}

fun playStereoBeep(pan: Float) {
    val durationMs = 100
    val sampleRate = 44100
    val numSamples = sampleRate * durationMs / 1000
    val buffer = ShortArray(numSamples * 2)
    val freq = 1000

    for (i in 0 until numSamples) {
        val sample = (sin(2 * PI * i * freq / sampleRate) * 32767).toInt().toShort()
        buffer[i * 2] = (sample * (1 - pan)).toInt().toShort()
        buffer[i * 2 + 1] = (sample * pan).toInt().toShort()
    }

    val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        sampleRate,
        AudioFormat.CHANNEL_OUT_STEREO,
        AudioFormat.ENCODING_PCM_16BIT,
        buffer.size * 2,
        AudioTrack.MODE_STATIC
    )

    audioTrack.write(buffer, 0, buffer.size)
    audioTrack.play()
}

@Composable
fun CameraPreviewView(modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    BackHandler(enabled = true) {
        onBack?.invoke()
    }
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(true) {
        tflite = Interpreter(loadModelFile(context, "yolov5n-fp16.tflite"))

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(ContextCompat.getMainExecutor(context), YOLOAnalyzer(tflite))
            }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageAnalyzer
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier.fillMaxSize()
    )
}