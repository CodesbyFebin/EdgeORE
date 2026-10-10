package com.edgeore.app.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Live QR scanner for the review destination field. Camera2 + zxing-core only (no CameraX, no Play Services).
 * Frames are decoded in memory on a background thread and never written to disk or sent anywhere.
 * The CAMERA permission is requested by MainActivity only after the user taps "Scan QR"; this activity refuses
 * to open the camera without it. It returns the raw decoded text; [AddressQr] decides what (if anything) it fills.
 */
class QrScanActivity : ComponentActivity() {
    private lateinit var texture: TextureView
    private lateinit var status: TextView
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private val done = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texture = TextureView(this).apply { contentDescription = "Camera preview for QR scanning" }
        status = TextView(this).apply {
            setText(com.edgeore.app.R.string.qr_scan_hint)
            setTextColor(Color.parseColor("#EEF2EF")); textSize = 16f; gravity = Gravity.CENTER
            setPadding(32, 32, 32, 16)
        }
        val cancel = Button(this).apply {
            setText(com.edgeore.app.R.string.qr_scan_cancel); minHeight = (48 * resources.displayMetrics.density).toInt()
            contentDescription = "Cancel scanning"
            setOnClickListener { finishWith(null, null) }
        }
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#E6101414"))
            addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(cancel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(32, 0, 32, 48) })
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#101414"))
            addView(texture, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(bottom, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        })
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finishWith(null, "Camera permission was not granted."); return
        }
        thread = HandlerThread("edgeore-qr").also { it.start(); handler = Handler(it.looper) }
        if (texture.isAvailable) open() else texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) = open()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) = Unit
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) = Unit
        }
    }

    override fun onPause() {
        close()
        super.onPause()
    }

    @SuppressLint("MissingPermission") // Checked in onResume; nothing reaches here without CAMERA granted.
    private fun open() {
        val mgr = getSystemService(CameraManager::class.java) ?: return finishWith(null, "No camera service on this device.")
        val id = try {
            mgr.cameraIdList.firstOrNull { mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
                ?: mgr.cameraIdList.firstOrNull()
        } catch (e: Exception) { null } ?: return finishWith(null, "No usable camera was found.")
        val img = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.YUV_420_888, 2).also { reader = it }
        img.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (done.get()) return@setOnImageAvailableListener
                val plane = image.planes[0]
                val buf = plane.buffer
                val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                val text = QrDecoder.decodeLuminance(bytes, plane.rowStride, image.width, image.height)
                if (text != null) runOnUiThread { finishWith(text, null) }
            } catch (_: Exception) {
                // A bad frame is skipped; the next frame is tried.
            } finally { image.close() }
        }, handler)
        try {
            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; startSession(device, img) }
                override fun onDisconnected(device: CameraDevice) { device.close(); camera = null }
                override fun onError(device: CameraDevice, error: Int) { device.close(); camera = null; runOnUiThread { finishWith(null, "The camera reported error $error.") } }
            }, handler)
        } catch (e: Exception) {
            finishWith(null, "The camera could not be opened.")
        }
    }

    @Suppress("DEPRECATION") // createCaptureSession(List, …) is the API 26–27 path; SessionConfiguration needs API 28.
    private fun startSession(device: CameraDevice, img: ImageReader) {
        val st = texture.surfaceTexture ?: return
        st.setDefaultBufferSize(WIDTH, HEIGHT)
        val preview = Surface(st)
        try {
            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview); addTarget(img.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            }
            device.createCaptureSession(listOf(preview, img.surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    try { s.setRepeatingRequest(req.build(), null, handler) } catch (_: Exception) { runOnUiThread { finishWith(null, "The camera preview could not start.") } }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { runOnUiThread { finishWith(null, "The camera preview could not start.") } }
            }, handler)
        } catch (e: Exception) {
            finishWith(null, "The camera preview could not start.")
        }
    }

    private fun close() {
        runCatching { session?.close() }; session = null
        runCatching { camera?.close() }; camera = null
        runCatching { reader?.close() }; reader = null
        thread?.quitSafely(); thread = null; handler = null
    }

    private fun finishWith(text: String?, error: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) { runOnUiThread { finishWith(text, error) }; return }
        if (!done.compareAndSet(false, true)) return
        val data = Intent().apply { text?.let { putExtra(EXTRA_TEXT, it) }; error?.let { putExtra(EXTRA_ERROR, it) } }
        setResult(if (text != null) Activity.RESULT_OK else Activity.RESULT_CANCELED, data)
        finish()
    }

    companion object {
        const val EXTRA_TEXT = "com.edgeore.app.scan.TEXT"
        const val EXTRA_ERROR = "com.edgeore.app.scan.ERROR"
        private const val WIDTH = 1280
        private const val HEIGHT = 720
    }
}
