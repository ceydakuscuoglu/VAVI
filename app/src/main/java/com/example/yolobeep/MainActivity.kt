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
import kotlinx.coroutines.withContext
import java.util.Locale

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
                        is Screen.Home -> HomeScreen(
                            onDetection = { screen = Screen.Detection },
                            onMapping = { screen = Screen.Mapping }
                        )
                        is Screen.Detection -> CameraPreviewView(
                            modifier = Modifier.padding(innerPadding)
                        )
                        is Screen.Mapping -> GridNavigationScreen(
                            modifier = Modifier.padding(innerPadding),
                            tts = tts
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
fun HomeScreen(onDetection: () -> Unit, onMapping: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(
            onClick = onDetection,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) { Text("Real-Time Detection") }
        Button(
            onClick = onMapping,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) { Text("Indoor Mapping") }
    }
}

lateinit var tflite: Interpreter

@Composable
fun GridNavigationScreen(modifier: Modifier = Modifier, tts: TextToSpeech?) {
    val context = LocalContext.current
    // Load grid from assets
    val grid = remember { GridMapLoader.loadGridMap(context) }
    var start by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var end by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var path by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var currentIdx by remember { mutableStateOf(0) }
    val current: Pair<Int, Int>? = if (path.isNotEmpty() && currentIdx in path.indices) path[currentIdx] else null
    var rotation by remember { mutableStateOf(90f) }
    var setStartMode by remember { mutableStateOf(true) } // true: set start, false: set end

    // Launch pathfinding in a coroutine when start and end are set
    LaunchedEffect(start, end) {
        if (start != null && end != null && start != end) {
            path = withContext(Dispatchers.Default) {
                AStarPathfinder.findPath(grid, start!!, end!!)
            }
            currentIdx = 0
        }
    }

    // Helper to speak direction
    fun speakDirection(direction: String) {
        tts?.speak(direction, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Toggle button for Set Start / Set End
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = { setStartMode = !setStartMode },
                modifier = Modifier
                    .semantics { contentDescription = if (setStartMode) "Switch to Set End" else "Switch to Set Start" }
            ) {
                Text(if (setStartMode) "Set Start" else "Set End")
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    GridMapView(ctx).apply {
                        this.grid = grid
                        this.onCellTapped = { row, col ->
                            if (start == null) {
                                start = row to col
                                setStartMode = false // Switch to Set End
                            } else if (end == null && (row to col) != start) {
                                end = row to col
                            } else if (start != null && end != null) {
                                // Both set: move the closer one
                                val distToStart = kotlin.math.abs(row - start!!.first) + kotlin.math.abs(col - start!!.second)
                                val distToEnd = kotlin.math.abs(row - end!!.first) + kotlin.math.abs(col - end!!.second)
                                if (distToStart <= distToEnd) {
                                    start = row to col
                                    if (end == start) end = null
                                    setStartMode = false // Switch to Set End
                                } else {
                                    end = row to col
                                    if (start == end) start = null
                                    setStartMode = true // Switch to Set Start
                                }
                            }
                            if (start != null && end != null && start != end) {
                                // path will be computed by LaunchedEffect
                            } else {
                                path = emptyList()
                                currentIdx = 0
                            }
                            this.start = start
                            this.end = end
                            this.path = path
                            this.current = current
                            this.rotationDegrees = rotation
                        }
                        this.start = start
                        this.end = end
                        this.path = path
                        this.current = current
                        this.rotationDegrees = rotation
                    }
                },
                update = { view ->
                    view.grid = grid
                    view.start = start
                    view.end = end
                    view.path = path
                    view.current = current
                    view.rotationDegrees = rotation
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        // D-pad navigation
        val directions = listOf(
            "up" to (-1 to 0),
            "down" to (1 to 0),
            "left" to (0 to -1),
            "right" to (0 to 1)
        )
        val canMove = { dr: Int, dc: Int ->
            val nextIdx = currentIdx + 1
            if (path.isNotEmpty() && nextIdx in path.indices) {
                val (curRow, curCol) = path[currentIdx]
                val (nextRow, nextCol) = path[nextIdx]
                (nextRow - curRow == dr) && (nextCol - curCol == dc)
            } else false
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (canMove(-1, 0)) {
                            currentIdx++
                            speakDirection("Move up")
                        }
                    },
                    enabled = canMove(-1, 0),
                    modifier = Modifier
                        .semantics { contentDescription = "Move Up" }
                        .padding(8.dp)
                ) { Text("↑") }
                Spacer(modifier = Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        if (canMove(0, -1)) {
                            currentIdx++
                            speakDirection("Move left")
                        }
                    },
                    enabled = canMove(0, -1),
                    modifier = Modifier
                        .semantics { contentDescription = "Move Left" }
                        .padding(8.dp)
                ) { Text("←") }
                Spacer(modifier = Modifier.width(32.dp))
                Button(
                    onClick = {
                        if (canMove(0, 1)) {
                            currentIdx++
                            speakDirection("Move right")
                        }
                    },
                    enabled = canMove(0, 1),
                    modifier = Modifier
                        .semantics { contentDescription = "Move Right" }
                        .padding(8.dp)
                ) { Text("→") }
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        if (canMove(1, 0)) {
                            currentIdx++
                            speakDirection("Move down")
                        }
                    },
                    enabled = canMove(1, 0),
                    modifier = Modifier
                        .semantics { contentDescription = "Move Down" }
                        .padding(8.dp)
                ) { Text("↓") }
                Spacer(modifier = Modifier.weight(1f))
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
fun CameraPreviewView(modifier: Modifier = Modifier) {
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