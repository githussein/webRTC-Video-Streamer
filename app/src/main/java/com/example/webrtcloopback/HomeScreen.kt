package com.example.webrtcloopback

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer


@Composable
fun HomeScreen(viewModel: WebRTCViewModel) {
    var surfaceView: SurfaceViewRenderer? by remember { mutableStateOf(null) }
    var fps by remember { mutableIntStateOf(viewModel.fps.intValue) } // default FPS
    var loopbackStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initialize()
    }


    Column(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceView = this
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // FPS Slider
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(text = "Frame Rate: $fps fps")
            Slider(
                value = fps.toFloat(),
                onValueChange = { newFps ->
                    fps = newFps.toInt()
                    // slider changes -> FPS changes -> update fps
                    if (loopbackStarted) surfaceView?.let { viewModel.updateFps(fps, it) }
                },
                valueRange = 10f..30f,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                surfaceView?.let { viewModel.startLoopback(it, fps) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("Start Loopback Preview")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}