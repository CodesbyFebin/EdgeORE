package com.edgeore.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.R
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.ui.theme.EdgeShapes

/** Hero card in the design's style: art, title, subtitle, and an honest status pill. */
@Composable
fun Hero(
    art: Int,
    title: String,
    subtitle: String,
    pill: String,
    pillOn: Boolean,
    tag: String,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(EdgeShapes.card)
            .border(1.dp, EdgeColors.holoBorder, EdgeShapes.card)
            .testTag(tag),
    ) {
        Image(
            painterResource(art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(190.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    190.dp,
                ).background(Brush.verticalGradient(listOf(Color(0xCC060D0C), Color.Transparent, Color(0x66060D0C)))),
        )
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    color =
                        Color(
                            0xFFF2F7F5,
                        ),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier =
                        Modifier.weight(1f).semantics {
                            heading()
                        },
                )
                Text(
                    pill,
                    color = Color(0xFF07110F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier =
                        Modifier
                            .background(
                                if (pillOn) Color(0xFF19E6BC) else Color(0xFFFF963F),
                                EdgeShapes.pill,
                            ).padding(horizontal = 12.dp, vertical = 5.dp)
                            .testTag("$tag-pill"),
                )
            }
            Text(subtitle, color = Color(0xFFD5E0DC), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, end = 120.dp))
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tag: String = "",
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                EdgeColors.surface,
                EdgeShapes.card,
            ).border(1.dp, EdgeColors.holoBorder, EdgeShapes.card)
            .padding(14.dp)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = EdgeColors.mint, modifier = Modifier.size(22.dp).padding(end = 6.dp)) }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = EdgeColors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                subtitle?.let { Text(it, color = EdgeColors.textMuted, fontSize = 12.sp) }
            }
        }
        content()
    }
}

@Composable
fun NavRow(
    icon: ImageVector,
    label: String,
    value: String,
    tag: String,
    sub: String? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(
                min = 52.dp,
            ).background(EdgeColors.surfaceInset, RoundedCornerShape(12.dp))
            .border(1.dp, EdgeColors.border, RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = EdgeColors.textMuted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(label, color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
            sub?.let { Text(it, color = EdgeColors.textMuted, fontSize = 12.sp) }
        }
        Text(value, color = EdgeColors.textMuted, fontSize = 13.sp)
        Icon(EdgeIcons.Chevron, null, tint = EdgeColors.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun CopperButton(
    text: String,
    icon: ImageVector?,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(
                min = 50.dp,
            ).background(EdgeColors.copper, EdgeShapes.control)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(tag),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, null, tint = EdgeColors.onAction, modifier = Modifier.size(20.dp).padding(end = 6.dp)) }
        Text(text, color = EdgeColors.onAction, fontWeight = FontWeight.Bold)
    }
}

/** Jav AI header: honest model state, wired to the existing allowlisted on-device loader (no import of arbitrary files). */
@Composable
fun JavAiTop(vm: EdgeOreViewModel) {
    val od by vm.onDevice.collectAsStateWithLifecycle()
    var choose by rememberSaveable { mutableStateOf(false) }
    var import by rememberSaveable { mutableStateOf(false) }
    val loaded = od.models.firstOrNull { it.id == od.loadedId }
    Hero(
        R.drawable.hero_ai,
        "Jav AI",
        "Your on-device assistant for EdgeORE.",
        if (loaded !=
            null
        ) {
            "Model loaded"
        } else {
            "Model not loaded"
        },
        loaded != null,
        "jav-hero",
    )
    SectionCard("Local model", tag = "local-model") {
        val installed = od.models.filter { it.id in od.downloaded }
        NavRow(
            EdgeIcons.Cube,
            "Choose a model",
            "",
            "choose-model",
            sub =
                loaded?.name ?: od.models.firstOrNull { it.id == od.selectedId && it.id in od.downloaded }?.name
                    ?: if (installed.isEmpty()) "No model installed" else "${installed.size} installed, none selected",
        ) { choose = true }
        CopperButton("Import model", EdgeIcons.Upload, "import-model") { import = true }
        Text(
            "Load a compatible model to start a local session. Models come only from the allowlist below (pinned commit, size and SHA-256 checked).",
            color = EdgeColors.textMuted,
            fontSize = 12.sp,
        )
    }
    if (choose) {
        AlertDialog(onDismissRequest = { choose = false }, title = { Text("Choose a model") }, text = {
            val installed = od.models.filter { it.id in od.downloaded }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (installed.isEmpty()) Text("No model is installed on this phone yet. Use Import model to download an allowlisted one.")
                installed.forEach { m ->
                    TextButton(onClick = {
                        vm.selectOnDeviceModel(m.id)
                        choose = false
                    }) {
                        Text(
                            m.name + if (m.id == od.selectedId) " (selected)" else "",
                        )
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { choose = false }) { Text("Close") } })
    }
    if (import) {
        AlertDialog(onDismissRequest = { import = false }, title = { Text("Import model") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "This build imports models only from its allowlist (Hugging Face, pinned commit, verified size and SHA-256). Picking an arbitrary file is not supported.",
                )
                if (od.models.isEmpty()) Text(od.catalogError ?: "The model allowlist is not available.")
                od.models.forEach { m ->
                    TextButton(onClick = {
                        vm.requestOnDeviceDownload(m.id)
                        import = false
                    }, enabled = m.id !in od.downloaded) {
                        Text(m.name + if (m.id in od.downloaded) " (installed)" else " · ${com.edgeore.app.ui.Format.bytes(m.sizeBytes)}")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { import = false }) { Text("Close") } })
    }
}

const val ZK_NOTE = "No prover, circuit or verification key ships in this build, so no proof is generated or verified and no claim is made. This screen shows the setup a future prover would need."

/** ZK Proof: setup only. Every choice honestly reports that nothing is available yet. */
@Composable
fun ZkProofScreen() {
    var info by rememberSaveable { mutableStateOf<String?>(null) }
    Hero(R.drawable.hero_zk, "Prove. Verify. Share.", "Proofs for supported computations.", "Prover not configured", false, "zk-hero")
    SectionCard("Proving setup", tag = "zk-setup") {
        NavRow(EdgeIcons.Circuit, "Circuit", "Select circuit", "zk-circuit") { info = "No circuits are available in this build yet." }
        NavRow(EdgeIcons.Cube, "Backend", "Select prover", "zk-backend") { info = "No prover backend is configured in this build yet." }
        NavRow(EdgeIcons.Key, "Verification key", "Not loaded", "zk-vk") {
            info =
                "No verification key is loaded. One becomes available only with a supported circuit."
        }
        Text("Choose a supported circuit and matching verification key.", color = EdgeColors.textMuted, fontSize = 12.sp)
    }
    info?.let { Text(it, color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("zk-info")) }
    Text(ZK_NOTE, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("zk-note"))
}

/** Settings: wallet & network, device and AI preferences, theme, and links to Receipts, Storage, Nodes and About. */
@Composable
fun SettingsScreen(
    vm: EdgeOreViewModel,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpen: (String) -> Unit,
    onAbout: () -> Unit,
    onAi: () -> Unit,
) {
    val wallet by vm.walletState.collectAsStateWithLifecycle()
    val cluster by vm.cluster.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val od by vm.onDevice.collectAsStateWithLifecycle()
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    var net by rememberSaveable { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Hero(
        R.drawable.hero_settings,
        "Your device. Your rules.",
        "Configure your wallet, network, performance and privacy settings for EdgeORE.",
        cluster.label,
        cluster != com.edgeore.app.solana.Cluster.MAINNET,
        "settings-hero",
    )
    SectionCard("Wallet & network", "Configure your wallet and network settings.", EdgeIcons.Wallet, "settings-wallet") {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(EdgeIcons.Wallet, null, tint = EdgeColors.textMuted, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Wallet", color = EdgeColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Text(
                    wallet.address?.let { "${it.take(4)}…${it.takeLast(4)}" } ?: "Not connected",
                    color = EdgeColors.textMuted,
                    fontSize = 12.sp,
                )
            }
            Text(
                if (wallet.address == null) (if (wallet.busy) "Waiting…" else "Connect wallet") else "Disconnect",
                color =
                    if (wallet.address ==
                        null
                    ) {
                        EdgeColors.onAction
                    } else {
                        EdgeColors.danger
                    },
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier =
                    Modifier
                        .background(if (wallet.address == null) EdgeColors.copper else Color.Transparent, EdgeShapes.control)
                        .clickable(
                            role = Role.Button,
                            enabled = !wallet.busy,
                            onClick =
                                if (wallet.address ==
                                    null
                                ) {
                                    onConnect
                                } else {
                                    onDisconnect
                                },
                        ).padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("settings-connect"),
            )
        }
        NavRow(EdgeIcons.Globe, "Network", cluster.label, "settings-network") { net = true }
        Text(
            "One network for the whole app. Mainnet ORE deploys still need the risk tick and are capped at 0.01 SOL.",
            color = EdgeColors.textMuted,
            fontSize = 12.sp,
        )
    }
    ContributionToggles(
        settings.chargeOnly,
        settings.thermalGuard,
        settings.batteryReservePercent,
        effect = { it.effect.label },
        onChargeOnly = { v ->
            vm.updateSettings { it.copy(chargeOnly = v) }
        },
        onThermal = { v -> vm.updateSettings { it.copy(thermalGuard = v) } },
    )
    SectionCard("AI preferences", "Configure your on-device AI assistant.", EdgeIcons.Ai, "settings-ai") {
        NavRow(
            EdgeIcons.Cube,
            "Default model",
            od.models
                .firstOrNull {
                    it.id == od.selectedId
                }?.name ?: "None selected",
            "settings-default-model",
            onClick = onAi,
        )
    }
    SectionCard("Appearance", icon = EdgeIcons.Settings, tag = "settings-theme") {
        com.edgeore.app.ui.components.ThemeSwitch(com.edgeore.app.ui.theme.ThemeState.mode) { m ->
            com.edgeore.app.ui.theme.ThemeState.mode = m
            com.edgeore.app.ui.theme.ThemePrefs
                .write(ctx, m)
        }
    }
    SectionCard("More", icon = EdgeIcons.Grid, tag = "settings-more") {
        NavRow(EdgeIcons.Receipts, "Receipts", if (receipts.isEmpty()) "None yet" else "${receipts.size} stored", "settings-receipts") {
            onOpen("receipts")
        }
        NavRow(EdgeIcons.Storage, "Storage", "", "settings-storage") { onOpen("storage") }
        NavRow(EdgeIcons.Nodes, "Nodes", "", "settings-nodes") { onOpen("nodes") }
        NavRow(EdgeIcons.Info, "About, build & legal", "", "settings-about", onClick = onAbout)
    }
    if (net) {
        AlertDialog(onDismissRequest = { net = false }, title = { Text("Network") }, text = {
            Column {
                com.edgeore.app.solana.Cluster.entries.forEach { c ->
                    TextButton(onClick = {
                        vm.selectCluster(c)
                        net = false
                    }) {
                        Text(
                            c.label + if (c == cluster) " (selected)" else "",
                        )
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { net = false }) { Text("Close") } })
    }
}
