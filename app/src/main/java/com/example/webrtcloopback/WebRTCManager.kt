package com.example.webrtcloopback

import android.content.Context
import android.util.Log
import org.webrtc.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WebRTCManager(private val context: Context) {

    private val TAG = "WebRTCManager"

    // WebRTC core
    private lateinit var eglBase: EglBase
    private lateinit var encoderFactory: DefaultVideoEncoderFactory
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var videoCapturer: VideoCapturer
    private lateinit var videoSource: VideoSource
    private lateinit var videoTrack: VideoTrack
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper

    private var peerConnection: PeerConnection? = null

    // State flags
    private var isSurfaceInitialized = false
    private var isCapturerInitialized = false
    private var isCapturerRunning = false
    private var isFrameSinkAttached = false

    // Actual camera FPS reporting
    private val _cameraFps = MutableStateFlow(0)
    val cameraFps: StateFlow<Int> = _cameraFps.asStateFlow()

    private val frameCounter = AtomicInteger(0)
    private var fpsScheduler: ScheduledExecutorService? = null

    // Video sink that counts frames
    private val frameCountingSink = VideoSink { frame: VideoFrame ->
        frameCounter.incrementAndGet()
    }

    /** Expose EglBase context safely */
    val eglBaseContext: EglBase.Context
        get() {
            if (!::eglBase.isInitialized) throw IllegalStateException("Call initialize() first")
            return eglBase.eglBaseContext
        }

    /** Initialize WebRTC components (call once) */
    fun initialize() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        eglBase = EglBase.create()

        encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        videoCapturer = createVideoCapturer()
        videoSource = peerConnectionFactory.createVideoSource(false)
        videoTrack = peerConnectionFactory.createVideoTrack("local_track", videoSource)

        // attach counting sink once
        if (!isFrameSinkAttached) {
            videoTrack.addSink(frameCountingSink)
            isFrameSinkAttached = true
        }

        // Setup PeerConnection for loopback
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE connection state: $state")
                }
                override fun onIceConnectionReceivingChange(p0: Boolean) {}
                override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate?) {
                    Log.d(TAG, "ICE candidate: $candidate")
                }
                override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                override fun onAddStream(p0: MediaStream?) {}
                override fun onRemoveStream(p0: MediaStream?) {}
                override fun onDataChannel(p0: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
            }
        )

        peerConnection?.addTrack(videoTrack)

        Log.d(TAG, "Using DefaultVideoEncoderFactory (hardware + software fallback enabled)")
    }

    /** Start or restart camera loopback with specified FPS */
    fun startLoopback(surface: SurfaceViewRenderer, fps: Int = 30) {
        // Initialize SurfaceViewRenderer only once
        if (!isSurfaceInitialized) {
            surface.init(eglBase.eglBaseContext, null)
            isSurfaceInitialized = true
        }

        // Initialize capturer only once
        if (!isCapturerInitialized) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoCapturer.initialize(surfaceTextureHelper, context, videoSource.capturerObserver)
            isCapturerInitialized = true
        }

        // Stop previous capture safely if running
        try {
            if (isCapturerRunning) videoCapturer.stopCapture()
            videoCapturer.startCapture(1280, 720, fps)
            isCapturerRunning = true
            Log.d(TAG, "Camera capture started/restarted with fps: $fps")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting/restarting camera: ${e.message}")
        }

        // Attach video track to renderer
        videoTrack.addSink(surface)

        // Start FPS scheduler to update _cameraFps every second
        fpsScheduler?.shutdownNow()
        fpsScheduler = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay({
                val fpsValue = frameCounter.getAndSet(0)
                _cameraFps.value = fpsValue
                Log.d(TAG, "Camera actual fps: $fpsValue")
            }, 1, 1, TimeUnit.SECONDS)
        }

        // Ensure loopback SDP
        createLoopbackSdp()
    }

    /** Stop camera and cleanup */
    fun stopLoopback() {
        fpsScheduler?.shutdownNow()
        if (isCapturerRunning) {
            try {
                videoCapturer.stopCapture()
            } catch (e: Exception) { /* ignore */ }
            isCapturerRunning = false
        }
    }

    /** Create local offer + remote answer for loopback */
    private fun createLoopbackSdp() {
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(offer: SessionDescription?) {
                offer?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onSetSuccess() {
                            Log.d(TAG, "Local SDP set successfully")
                            val answer = SessionDescription(SessionDescription.Type.ANSWER, it.description)
                            peerConnection?.setRemoteDescription(object : SdpObserver {
                                override fun onSetSuccess() {
                                    Log.d(TAG, "Remote SDP set successfully (loopback)")
                                }
                                override fun onSetFailure(p0: String?) {
                                    Log.e(TAG, "Remote SDP failed: $p0")
                                }
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onCreateFailure(p0: String?) {}
                            }, answer)
                        }
                        override fun onSetFailure(error: String?) { Log.e(TAG, "Local SDP failed: $error") }
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onCreateFailure(p0: String?) {}
                    }, it)
                }
            }
            override fun onCreateFailure(error: String?) { Log.e(TAG, "SDP Offer failed: $error") }
            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, MediaConstraints())
    }

    /** Create rear camera capturer */
    private fun createVideoCapturer(): VideoCapturer {
        val enumerator = Camera2Enumerator(context)
        val devices = enumerator.deviceNames
        devices.firstOrNull { enumerator.isBackFacing(it) }?.let {
            return enumerator.createCapturer(it, null)
        }
        return enumerator.createCapturer(devices.first(), null)
    }
}