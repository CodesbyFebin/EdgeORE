package com.edgeore.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.BuildConfigInfo
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.receipts.StoredReceipt
import com.edgeore.app.ui.components.CountBadge
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.EdgeOreHeader
import com.edgeore.app.ui.components.EnvironmentStrip
import com.edgeore.app.ui.components.Notice
import com.edgeore.app.ui.screens.AiScreen
import com.edgeore.app.ui.screens.BrowserScreen
import com.edgeore.app.ui.screens.ConceptPreviewScreen
import com.edgeore.app.ui.screens.MineScreen
import com.edgeore.app.ui.screens.NodesScreen
import com.edgeore.app.ui.screens.OnboardingScreen
import com.edgeore.app.ui.screens.ReceiptDetailScreen
import com.edgeore.app.ui.screens.ReceiptsScreen
import com.edgeore.app.ui.screens.ReviewScreen
import com.edgeore.app.ui.screens.StorageScreen
import com.edgeore.app.ui.theme.EdgeColors

enum class Destination(
    val label: String,
    val icon: ImageVector,
    val inNav: Boolean = true,
) {
    Mine("Home", EdgeIcons.Home),
    AI("AI", EdgeIcons.Ai),
    Zk("ZK Proof", EdgeIcons.Proof),
    Settings("Settings", EdgeIcons.Settings),

    // Reachable from Home and Settings, not from the bottom bar.
    Storage("Storage", EdgeIcons.Storage, inNav = false),
    Nodes("Nodes", EdgeIcons.Nodes, inNav = false),
    Receipts("Receipts", EdgeIcons.Receipts, inNav = false),
    ;

    companion object {
        val nav = entries.filter { it.inNav }
    }
}

/** Platform actions the UI needs from the Activity (wallet sender and share sheet). */
interface PlatformActions {
    fun setReminders(enabled: Boolean) {}

    /** Opt-in round alerts; asks for POST_NOTIFICATIONS on Android 13+ first. */
    fun setRoundAlerts(enabled: Boolean) {}

    fun exportRoundProof(proof: com.edgeore.app.edge.StoredProof) {}

    fun connectWallet()

    fun disconnectWallet()

    fun signReviewed()

    fun export(
        receipt: StoredReceipt?,
        hideDeviceKey: Boolean = false,
    )

    /** Opens the live QR scanner. The CAMERA permission is requested here, only after the user taps Scan. */
    fun scanAddressQr() {}

    /** Decodes a QR code from an image the user picks (system photo picker; no storage or camera permission). */
    fun pickQrImage() {}
}

@Composable
fun EdgeOreApp(
    vm: EdgeOreViewModel,
    actions: PlatformActions,
) {
    var tab by rememberSaveable { mutableStateOf(Destination.Mine) }
    var route by rememberSaveable { mutableStateOf<String?>(null) } // "review", "preview", "browser", "ore", "receipt:<id>"
    var signerDetails by rememberSaveable { mutableStateOf(false) }
    var walletSheet by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var inbox by rememberSaveable { mutableStateOf(false) }
    val requestConnect = { walletSheet = true }
    var about by rememberSaveable { mutableStateOf(false) }
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    val ops by vm.operations.collectAsStateWithLifecycle()
    val damage by vm.receiptDamage.collectAsStateWithLifecycle()
    val largeText = LocalDensity.current.fontScale > 1.5f
    val context = LocalContext.current
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val wallet by vm.walletState.collectAsStateWithLifecycle()
    val session by vm.contributionSession.collectAsStateWithLifecycle()
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    var onboarded by rememberSaveable { mutableStateOf(Onboarding.done(context)) }

    if (!onboarded) {
        Scaffold(containerColor = EdgeColors.background) { inner ->
            Column(Modifier.fillMaxSize().padding(inner)) {
                OnboardingScreen(onDone = {
                    Onboarding.set(context, true)
                    onboarded = true
                })
            }
        }
        return
    }

    BackHandler(enabled = route != null) { route = null }

    Scaffold(
        containerColor = EdgeColors.background,
        floatingActionButton = {
            androidx.compose.material3.SmallFloatingActionButton(
                onClick = { help = true },
                modifier = Modifier.size(48.dp),
                containerColor = EdgeColors.surfaceInset,
                contentColor = EdgeColors.mint,
            ) { Icon(EdgeIcons.Bot, contentDescription = "ORE Bot help") }
        },
        bottomBar = {
            if (largeText) {
                val badges = NavBadges.of(ops, damage.size)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(EdgeColors.surface)
                        .border(1.dp, EdgeColors.mint.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                        .horizontalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                ) {
                    Destination.nav.forEach { d ->
                        val selected = tab == d && route == null
                        val tint =
                            if (!selected) {
                                EdgeColors.textMuted
                            } else if (d ==
                                Destination.Mine
                            ) {
                                EdgeColors.copper
                            } else {
                                EdgeColors.mint
                            }
                        val badge = badges[d]
                        Column(
                            Modifier
                                .width(144.dp)
                                .heightIn(min = 96.dp)
                                .testTag("nav-${d.label}")
                                .background(if (selected) EdgeColors.surfacePressed else EdgeColors.surface)
                                .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = {
                                    tab = d
                                    route = null
                                })
                                .semantics {
                                    this.selected = selected
                                    badge?.let { stateDescription = it.spoken }
                                }.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (badge == null) {
                                Icon(d.icon, null, tint = tint, modifier = Modifier.size(24.dp))
                            } else {
                                BadgedBox(
                                    badge = { CountBadge(badge.count, tone = badge.tone) },
                                ) { Icon(d.icon, null, tint = tint, modifier = Modifier.size(24.dp)) }
                            }
                            Text(d.label, color = tint, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                NavigationBar(
                    modifier =
                        Modifier
                            .padding(
                                horizontal = 12.dp,
                                vertical = 8.dp,
                            ).border(1.dp, EdgeColors.mint.copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
                    containerColor = EdgeColors.surface,
                ) {
                    val badges = NavBadges.of(ops, damage.size)
                    Destination.nav.forEach { d ->
                        val badge = badges[d]
                        NavigationBarItem(
                            selected = tab == d && route == null,
                            onClick = {
                                tab = d
                                route = null
                            },
                            modifier = if (badge != null) Modifier.semantics { stateDescription = badge.spoken } else Modifier,
                            icon = {
                                if (badge == null) {
                                    Icon(d.icon, contentDescription = null)
                                } else {
                                    BadgedBox(
                                        badge = { CountBadge(badge.count, tone = badge.tone) },
                                    ) { Icon(d.icon, contentDescription = null) }
                                }
                            },
                            label = { Text(d.label) },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    selectedIconColor =
                                        if (d ==
                                            Destination.Mine
                                        ) {
                                            EdgeColors.copper
                                        } else {
                                            EdgeColors.mint
                                        },
                                    selectedTextColor =
                                        if (d ==
                                            Destination.Mine
                                        ) {
                                            EdgeColors.copper
                                        } else {
                                            EdgeColors.mint
                                        },
                                    indicatorColor = EdgeColors.mint.copy(alpha = 0.14f),
                                    unselectedIconColor = EdgeColors.textMuted,
                                    unselectedTextColor = EdgeColors.textMuted,
                                ),
                        )
                    }
                }
            }
        },
    ) { inner ->
        Row(Modifier.fillMaxSize().padding(inner), horizontalArrangement = Arrangement.Center) {
            Column(
                Modifier
                    .widthIn(
                        max = 640.dp,
                    ).fillMaxSize()
                    .background(
                        EdgeColors.ambient,
                    ).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EdgeOreHeader(onSettings = { about = true }, onReview = { route = "review" })
                // Follows the selected network: the cluster selector or the ORE deploy Mainnet switch.
                val headerCluster by vm.cluster.collectAsStateWithLifecycle()
                val headerDeploy by vm.oreDeploy.collectAsStateWithLifecycle()
                EnvironmentStrip(mainnet = headerDeploy.mainnet || headerCluster == com.edgeore.app.solana.Cluster.MAINNET)
                if (route != null) {
                    TextButton(onClick = { route = null }) {
                        Icon(EdgeIcons.Back, contentDescription = null, tint = EdgeColors.textMuted)
                        Text(" Back", color = EdgeColors.textMuted)
                    }
                }
                val r = route
                when {
                    r == "review" ->
                        ReviewScreen(
                            vm,
                            onSign = actions::signReviewed,
                            onConnect = requestConnect,
                            onScanQr = actions::scanAddressQr,
                            onPickQrImage = actions::pickQrImage,
                        )
                    r == "crew" -> com.edgeore.app.ui.screens.CrewScreen(vm) { route = "review" }
                    r == "history" -> com.edgeore.app.ui.screens.HistoryView(vm.historyRows())
                    r == "proofs" -> {
                        val edgeState by vm.edge.collectAsStateWithLifecycle()
                        com.edgeore.app.ui.screens.RoundProofView(
                            edgeState.proofs,
                            actions::exportRoundProof,
                            { vm.explainProof(it.sha256) },
                            edgeState.explanation,
                        )
                    }
                    r == "preview" -> ConceptPreviewScreen()
                    r == "browser" -> BrowserScreen()
                    r == "ore" || r == "ore:pro" ->
                        com.edgeore.app.ui.screens
                            .OreScreen(vm, initialPro = true)
                    r == "ore:lite" ->
                        com.edgeore.app.ui.screens
                            .OreScreen(vm, initialPro = false)
                    r != null && r.startsWith("receipt:") -> {
                        val rec = receipts.firstOrNull { it.id == r.removePrefix("receipt:") }
                        if (rec == null) Notice("Receipt not found.", error = true) else ReceiptDetailScreen(rec) { actions.export(rec) }
                    }
                    tab == Destination.Mine ->
                        MineScreen(
                            vm,
                            requestConnect,
                            actions::disconnectWallet,
                            onReview = {
                                vm.resetReview()
                                route = "review"
                            },
                            onPreview = { route = "preview" },
                            onOpenReview = { route = "review" },
                            onOpenTab = {
                                tab = it
                                route = null
                            },
                            onOre = { route = "ore" },
                            onOreMode = { pro ->
                                route =
                                    if (pro) "ore:pro" else "ore:lite"
                            },
                            onSettings = { about = true },
                            onHistory = { route = "history" },
                            onProofs = { route = "proofs" },
                            onCrew = { route = "crew" },
                            onAlerts = actions::setRoundAlerts,
                        )
                    tab == Destination.AI -> {
                        com.edgeore.app.ui.screens
                            .JavAiTop(vm)
                        AiScreen(vm)
                    }
                    tab == Destination.Zk ->
                        com.edgeore.app.ui.screens
                            .ZkProofScreen()
                    tab == Destination.Settings ->
                        com.edgeore.app.ui.screens.SettingsScreen(
                            vm,
                            requestConnect,
                            actions::disconnectWallet,
                            onOpen = { r ->
                                tab =
                                    when (r) {
                                        "receipts" -> Destination.Receipts
                                        "storage" -> Destination.Storage
                                        else -> Destination.Nodes
                                    }
                                ; route =
                                    null
                            },
                            onAbout = { about = true },
                            onAi = {
                                tab = Destination.AI
                                route = null
                            },
                        )
                    tab == Destination.Storage -> StorageScreen(vm, onBrowser = { route = "browser" })
                    tab == Destination.Nodes -> NodesScreen(vm)
                    tab == Destination.Receipts -> ReceiptsScreen(vm, onOpen = { route = "receipt:" + it.id }, onExport = actions::export)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.edgeore.app.ui.components
                        .SecondaryAction("ORE Bot · Help", Modifier.weight(1f), onClick = { help = true })
                    com.edgeore.app.ui.components
                        .IconAction(EdgeIcons.Bell, "Notification inbox", onClick = { inbox = true })
                }
                Text(
                    "Built by CodesbyFebin · ORE v3 client · no token issuance",
                    style = MaterialTheme.typography.labelSmall,
                    color = EdgeColors.textMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("Settings & build") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.edgeore.app.ui.components.ThemeSwitch(com.edgeore.app.ui.theme.ThemeState.mode) { m ->
                        com.edgeore.app.ui.theme.ThemeState.mode = m
                        com.edgeore.app.ui.theme.ThemePrefs
                            .write(context, m)
                    }
                    Text(
                        "EdgeORE ${BuildConfigInfo.VERSION} (${AppClock.buildStamp()}) · transfers use Solana devnet; ORE Deploy is devnet by default with an opt-in mainnet option, capped at 0.01 SOL per deploy and signed in your wallet. Hackathon build 0.1.0-clockin; no rewards or income.",
                    )
                    com.edgeore.app.ui.components.ResourceControl(
                        EdgeIcons.Bell,
                        "Optional daily reminder",
                        "Around 10:00 local time; quiet hours 21:00–09:00. At most one per day. Delivery may be delayed by Android.",
                        reminders,
                        "Local notification · no eligibility or reward claim",
                        onChange = actions::setReminders,
                    )
                    Text("Receipts signed with: ${vm.receiptSignerProtection.substringBefore(" · key ")}.")
                    com.edgeore.app.ui.components
                        .SecondaryAction(if (signerDetails) "Hide signer identity" else "Signer identity", onClick = {
                            signerDetails =
                                !signerDetails
                        })
                    if (signerDetails) Text(vm.receiptSignerProtection, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Hackathon build 0.1.0-clockin. No ORE rewards, payments, token issuance or mining income are implemented or implied. \$EdgeORE is a product name, not a token.",
                    )
                }
            },
            confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    about = false
                    Onboarding.set(context, false)
                    onboarded = false
                }) { Text("Show introduction") }
            },
        )
    }
    if (walletSheet) {
        com.edgeore.app.ui.components.WalletConnectSheet(onDismiss = { walletSheet = false }, onConnect = {
            walletSheet = false
            actions.connectWallet()
        })
    }
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text("ORE Bot · Basic help") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Current app state · ${com.edgeore.app.ui.Format.time(AppClock.nowMs())}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        if (wallet.address ==
                            null
                        ) {
                            "Wallet: not connected. Use Connect, then authorize in your installed Solana wallet."
                        } else {
                            "Wallet: connected. Connection alone does not approve a transaction."
                        },
                    )
                    Text(
                        "Balances: ${portfolio.network.label}, ${if (portfolio.loading) {
                            "reading"
                        } else {
                            portfolio.error ?: "${portfolio.assets.count {
                                it.amount != null &&
                                    it.status.startsWith(
                                        "Observed",
                                    )
                            }} of 3 assets observed"
                        }}. Missing balances are never assumed to be zero.",
                    )
                    Text(
                        "Node session: ${session.phase.name.lowercase()}. ${session.failure ?: session.note ?: "Inspect each worker before starting. Configure your node in Nodes and gateway in Storage."}",
                    )
                    Text(
                        "Review: check recipient, integer lamports, fees and exact message hash. Signing and submission are separate. An unknown result must be observed, never resent.",
                    )
                    Text(
                        "Receipts: ${receipts.size} stored. Verification checks signed contents; it does not independently confirm payment.",
                    )
                    Text(
                        "ORE: the 5×5 board reads mainnet. ORE Deploy (Mine) is reviewed and signed by you in your wallet; mainnet deploys are capped at 0.01 SOL and risk that SOL. No reward campaign is configured. AI conversation is available separately in AI using your configured owned host or local model.",
                    )
                }
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Close") } },
        )
    }
    if (inbox) {
        AlertDialog(
            onDismissRequest = { inbox = false },
            title = { Text("Notifications") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Optional reminders are ${if (reminders) "enabled" else "off"}. No backend notification inbox is configured.")
                    Text("Reminders contain general help only. No balances, wallet addresses or private jobs appear on your lock screen.")
                    Text("Android notification settings control delivery. You can disable reminders in Settings at any time.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    inbox = false
                    about = true
                }) { Text("Preferences") }
            },
            dismissButton = { TextButton(onClick = { inbox = false }) { Text("Close") } },
        )
    }
}
