package com.edgeore.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.edgeore.app.scan.QrDecoder
import com.edgeore.app.scan.QrScanActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.edgeore.app.receipts.StoredReceipt
import com.edgeore.app.ui.EdgeOreApp
import com.edgeore.app.ui.PlatformActions
import com.edgeore.app.ui.theme.EdgeOreTheme
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: EdgeOreViewModel by viewModels()

    // QR address scan for the review destination. Each launcher is registered before STARTED, as required.
    private val scanResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringExtra(QrScanActivity.EXTRA_TEXT)
        val error = r.data?.getStringExtra(QrScanActivity.EXTRA_ERROR)
        when {
            r.resultCode == RESULT_OK && text != null -> vm.applyScannedQr(text)
            error != null -> vm.scanFailed("Scan stopped: $error")
        }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanResult.launch(Intent(this, QrScanActivity::class.java))
        else vm.scanFailed("Camera permission was not granted, so nothing was scanned. You can paste the address or use QR from image instead.")
    }
    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val text = withContext(Dispatchers.Default) { runCatching { decodeQrImage(uri) }.getOrNull() }
            if (text != null) vm.applyScannedQr(text) else vm.scanFailed("No QR code was found in that image.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Must be created before the Activity is STARTED (registers an activity-result launcher).
        val sender = ActivityResultSender(this)
        val actions = object : PlatformActions {
            override fun connectWallet() { vm.connectWallet(sender) }
            override fun disconnectWallet() { vm.disconnectWallet(sender) }
            override fun signReviewed() { vm.approveAndSign(sender) }
            override fun export(receipt: StoredReceipt?, hideDeviceKey: Boolean) { exportReceipts(receipt, hideDeviceKey) }
            override fun scanAddressQr() {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    scanResult.launch(Intent(this@MainActivity, QrScanActivity::class.java))
                } else cameraPermission.launch(Manifest.permission.CAMERA)
            }
            override fun pickQrImage() {
                pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
        setContent { EdgeOreTheme { EdgeOreApp(vm, actions) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDevice()
    }

    /** Downsamples the picked image (max 1600 px side), decodes it in memory and discards it. Nothing is stored. */
    private fun decodeQrImage(uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1600 || bounds.outHeight / (sample * 2) >= 1600) sample *= 2
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        return try {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            QrDecoder.decodeArgb(px, bmp.width, bmp.height)
        } finally { bmp.recycle() }
    }

    private fun exportReceipts(only: StoredReceipt?, hideDeviceKey: Boolean) {
        lifecycleScope.launch {
            try {
                val file = vm.exportReceipts(only, hideDeviceKey)
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, file.name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                startActivity(Intent.createChooser(send, "Export EdgeORE receipts"))
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
