/*
 * Zalith Launcher 2 Plus
 */

package com.movtery.zalithlauncher.ui.screens.content.settings

import android.opengl.GLSurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.movtery.zalithlauncher.R

/**
 * Full-screen benchmark overlay.
 * Renders 500 rotating quads via GLES 2.0 for [DURATION_S] seconds and
 * reports average FPS, min FPS, 99th-percentile FPS, stability %, and a
 * composite score (avgFps × stability / 100).
 *
 * This tests raw GPU draw-call throughput — not renderer translation overhead.
 * A higher score means the device/driver has more headroom for heavy renderers.
 */
@Composable
fun RendererBenchmarkOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current

    var secondsLeft by remember { mutableIntStateOf(15) }
    var result     by remember { mutableStateOf<BenchmarkResult?>(null) }
    var running    by remember { mutableStateOf(true) }

    val renderer = remember {
        BenchmarkGLRenderer(
            durationMs = 15_000L,
            onProgress = { s -> secondsLeft = s },
            onComplete = { r -> result = r; running = false }
        )
    }

    val glView = remember(context) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(Unit) {
        onDispose { glView.onPause() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // GL surface fills the background
        AndroidView(
            factory = { glView },
            modifier = Modifier.fillMaxSize()
        )

        // Semi-transparent overlay panel
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.benchmark_title),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            AnimatedVisibility(visible = running, enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.benchmark_running, secondsLeft),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            AnimatedVisibility(visible = !running && result != null, enter = fadeIn()) {
                result?.let { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {

                            BenchmarkRow(stringResource(R.string.benchmark_result_score),
                                "${r.score}", highlight = true)
                            BenchmarkRow(stringResource(R.string.benchmark_result_avg),
                                "${r.avgFps} fps")
                            BenchmarkRow(stringResource(R.string.benchmark_result_min),
                                "${r.minFps} fps")
                            BenchmarkRow(stringResource(R.string.benchmark_result_p99),
                                "${r.p99Fps} fps")
                            BenchmarkRow(stringResource(R.string.benchmark_result_stability),
                                "${r.stabilityPct} %")

                            Text(
                                stringResource(R.string.benchmark_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Button(onClick = onDismiss) {
                Text(if (running) "Cancel" else "Close")
            }
        }
    }
}

@Composable
private fun BenchmarkRow(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = if (highlight) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.bodyMedium)
        Text(value,
            style = if (highlight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
    }
}
