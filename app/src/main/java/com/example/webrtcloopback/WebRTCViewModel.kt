package com.example.webrtcloopback

import android.app.Application
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer

class WebRTCViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = WebRTCManager(app.applicationContext)

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    var fps = mutableIntStateOf(30)
        private set


    fun initialize() {
        if (!_isInitialized.value) {
            manager.initialize()
            _isInitialized.value = true
        }
    }

    fun startLoopback(surfaceView: SurfaceViewRenderer, fpsValue: Int = 30) {
        if (!_isInitialized.value) return
        fps.intValue = fpsValue
        manager.startLoopback(surfaceView, fpsValue)
    }

    fun updateFps(newFps: Int, surfaceView: SurfaceViewRenderer) {
        fps.intValue = newFps
        startLoopback(surfaceView, newFps)
    }
}