package com.edgeore.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.BuildConfigInfo
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.receipts.StoredReceipt
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.EdgeOreHeader
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.screens.AiScreen
import com.edgeore.app.ui.screens.ConceptPreviewScreen
import com.edgeore.app.ui.screens.MineScreen
import com.edgeore.app.ui.screens.NodesScreen
import com.edgeore.app.ui.screens.ReceiptDetailScreen
import com.edgeore.app.ui.screens.ReceiptsScreen
import com.edgeore.app.ui.screens.ReviewScreen
import com.edgeore.app.ui.screens.BrowserScreen
import com.edgeore.app.ui.screens.StorageScreen
import com.edgeore.app.ui.theme.EdgeColors

enum class Destination(val label: String, val icon: ImageVector) {
    Mine("Mine", EdgeIcons.Mine), AI("AI", EdgeIcons.Ai), Storage("Storage", EdgeIcons.Storage), Nodes("Nodes", EdgeIcons.Nodes), Receipts("Receipts", EdgeIcons.Receipts)
}

/** Platform actions the UI needs from the Activity (wallet sender and share sheet). */
interface PlatformActions {
    fun connectWallet()
    fun disconnectWallet()
    fun signReviewed()
    fun export(receipt: StoredReceipt?, hideDeviceKey: Boolean = false)
}

@Composable
fun EdgeOreApp(vm: EdgeOreViewModel, actions: PlatformActions) {
    var tab by rememberSaveable { mutableStateOf(Destination.Mine) }
    var route by rememberSaveable { mutableStateOf<String?>(null) } // "review", "preview", "receipt:<id>"
    var about by rememberSaveable { mutableStateOf(false) }
    val receipts by vm.receipts.collectAsStateWithLifecycle()

    BackHandler(enabled = route != null) { route = null }

    Scaffold(
        containerColor = EdgeColors.background,
        bottomBar = {
            NavigationBar(containerColor = EdgeColors.surfaceInset) {
                Destination.entries.forEach { d ->
                    NavigationBarItem(
                        selected = tab == d && route == null,
                        onClick = { tab = d; route = null },
                        icon = { Icon(d.icon, contentDescription = null) },
                        label = { Text(d.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EdgeColors.copper, selectedTextColor = EdgeColors.copper,
                            indicatorColor = EdgeColors.copper.copy(alpha = 0.14f),
                            unselectedIconColor = EdgeColors.textMuted, unselectedTextColor = EdgeColors.textMuted,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Row(Modifier.fillMaxSize().padding(inner), horizontalArrangement = Arrangement.Center) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EdgeOreHeader(onSettings = { about = true })
                if (route != null) {
                    TextButton(onClick = { route = null }) {
                        Icon(EdgeIcons.Back, contentDescription = null, tint = EdgeColors.textMuted); Text(" Back", color = EdgeColors.textMuted)
                    }
                }
                val r = route
                when {
                    r == "review" -> ReviewScreen(vm, onSign = actions::signReviewed, onConnect = actions::connectWallet)
                    r == "preview" -> ConceptPreviewScreen()
                    r == "browser" -> BrowserScreen()
                    r != null && r.startsWith("receipt:") -> {
                        val rec = receipts.firstOrNull { it.id == r.removePrefix("receipt:") }
                        if (rec == null) Notice("Receipt not found.", error = true) else ReceiptDetailScreen(rec) { actions.export(rec) }
                    }
                    tab == Destination.Mine -> MineScreen(vm, actions::connectWallet, actions::disconnectWallet,
                        onReview = { vm.resetReview(); route = "review" }, onPreview = { route = "preview" })
                    tab == Destination.AI -> AiScreen(vm)
                    tab == Destination.Storage -> StorageScreen(vm, onBrowser = { route = "browser" })
                    tab == Destination.Nodes -> NodesScreen(vm)
                    tab == Destination.Receipts -> ReceiptsScreen(vm, onOpen = { route = "receipt:" + it.id }, onExport = actions::export)
                }
                Text("Built by CodesbyFebin · ORE × SKR research scope · no token issuance", style = MaterialTheme.typography.labelSmall,
                    color = EdgeColors.textMuted, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }

    if (about) AlertDialog(
        onDismissRequest = { about = false },
        title = { Text("About this build") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("EdgeORE ${BuildConfigInfo.VERSION} · Solana devnet only.")
                Text("Receipts signed with: ${vm.receiptSignerProtection}.")
                Text("Not store-ready. No ORE rewards, SKR payments, token issuance or mining income are implemented or implied. \$EdgeORE is a product name, not a token.")
            }
        },
        confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } },
    )
}
