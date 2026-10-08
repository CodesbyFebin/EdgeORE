package com.edgeore.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        }
        setContent { EdgeOreTheme { EdgeOreApp(vm, actions) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDevice()
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
