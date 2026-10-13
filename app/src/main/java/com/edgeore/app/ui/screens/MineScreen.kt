package com.edgeore.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.edgeore.app.BuildConfig
import com.edgeore.app.EdgeOreViewModel
import com.edgeore.app.contribution.ConstraintObservation
import com.edgeore.app.contribution.ContributionPolicy
import com.edgeore.app.contribution.MAX_DURATION_MS
import com.edgeore.app.contribution.MAX_ROUNDS
import com.edgeore.app.contribution.Measurement
import com.edgeore.app.contribution.PartStatus
import com.edgeore.app.contribution.PartView
import com.edgeore.app.contribution.SchedulerStatus
import com.edgeore.app.contribution.SessionPhase
import com.edgeore.app.contribution.SessionView
import com.edgeore.app.device.EdgePolicy
import com.edgeore.app.settings.Control
import com.edgeore.app.solana.RpcObservation
import com.edgeore.app.ui.Format
import com.edgeore.app.ui.components.EdgeCard
import com.edgeore.app.ui.components.EdgeIcons
import com.edgeore.app.ui.components.EffectNote
import com.edgeore.app.ui.components.IconTile
import com.edgeore.app.ui.components.MiningAnimation
import com.edgeore.app.ui.components.PrimaryAction
import com.edgeore.app.ui.components.ResourceControl
import com.edgeore.app.ui.components.SecondaryAction
import com.edgeore.app.ui.theme.EdgeColors
import com.edgeore.app.wallet.WalletDisplay

@Composable
fun MineScreen(
    vm: EdgeOreViewModel,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReview: () -> Unit,
    onPreview: () -> Unit,
    onOpenReview: () -> Unit = onReview,
    onOpenTab: (com.edgeore.app.ui.Destination) -> Unit = {},
    onOre: () -> Unit = {},
    onOreMode: (pro: Boolean) -> Unit = { onOre() },
    onSettings: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val device by vm.device.collectAsStateWithLifecycle()
    val wallet by vm.walletState.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val unmetered by vm.unmetered.collectAsStateWithLifecycle()
    val job by vm.contributionJob.collectAsStateWithLifecycle()
    val lastRun by vm.lastContributionRun.collectAsStateWithLifecycle()
    val eval = EdgePolicy.evaluate(settings, device)
    val fresh = balance as? RpcObservation.Fresh
    val ops by vm.operations.collectAsStateWithLifecycle()
    val receipts by vm.receipts.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()

    // "Now" overview: what needs attention, each row opening the place that handles it.
    val waiting =
        com.edgeore.app.ui.NavBadges
            .waiting(ops)
    val sessionView by vm.contributionSession.collectAsStateWithLifecycle()
    val node by vm.node.collectAsStateWithLifecycle()
    val gateway by vm.gateway.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(node.record, gateway) { vm.refreshSession() }
    val deploy by vm.oreDeploy.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.checkOreDeployCluster() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val hw = androidx.compose.runtime.remember(ctx) { DeviceFacts.read(ctx) }
    val cluster by vm.cluster.collectAsStateWithLifecycle()
    val bal by vm.balances.collectAsStateWithLifecycle()
    val checkIn by vm.checkIn.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val checkInMsg by vm.checkInMessage.collectAsStateWithLifecycle()
    val oreState by vm.ore.collectAsStateWithLifecycle()
    var now by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    var diskReading by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.edgeore.app.device.StorageReading?>(null)
    }
    var net by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.edgeore.app.device.NetActivity.UNAVAILABLE) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var prev: Long? = null
        while (true) {
            diskReading =
                com.edgeore.app.device.Telemetry
                    .storage(ctx.filesDir)
            val cur =
                com.edgeore.app.device.Telemetry
                    .appTrafficBytes()
            net =
                com.edgeore.app.device.Telemetry
                    .activity(prev, cur)
            prev = cur
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(2_000)
        }
    }
    androidx.compose.runtime.LaunchedEffect(wallet.address, cluster) { vm.refreshBalances() }
    val notifPermission =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts
                .RequestPermission(),
        ) {
            vm.setReminders(it)
        }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    // One network switch for the whole Home: the ORE card follows it (mainnet still needs the risk tick).
    androidx.compose.runtime.LaunchedEffect(cluster) {
        val main = cluster == com.edgeore.app.solana.Cluster.MAINNET
        if (deploy.mainnet != main) vm.setOreDeployMainnet(main)
        vm.checkOreDeployCluster()
    }
    val ore by vm.ore.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(ore.rpc) {
        while (true) {
            vm.refreshOre()
            kotlinx.coroutines.delay(15_000)
        }
    }
    NetworkSelector(cluster, vm::selectCluster, onSettings = onSettings)
    WalletConnectRow(
        wallet.address,
        wallet.busy,
        cluster,
        onConnect = onConnect,
        onCopy = {
            wallet.address?.let {
                clipboard.setText(
                    androidx.compose.ui.text
                        .AnnotatedString(it),
                )
            }
        },
        onSwitch = onConnect,
        onDisconnect = onDisconnect,
    )
    val failure = wallet.error
    if (failure != null && !wallet.busy) {
        // Persistent until the next Connect attempt; sanitized (no token, key or wallet payload).
        Column(
            Modifier.fillMaxWidth().testTag("wallet-error").semantics {
                contentDescription =
                    "Wallet connection error ${failure.code.name}"
            },
        ) {
            WalletDisplay.errorLines(failure, showDiagnostics = BuildConfig.DEBUG).forEachIndexed { i, line ->
                Text(
                    line,
                    color = if (i == 0) EdgeColors.copper else EdgeColors.textMuted,
                    style = if (i < 2) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    BalanceRow(bal, now, wallet.address != null, if (cluster == com.edgeore.app.solana.Cluster.MAINNET) "ORE" else "Test ORE")
    EdgeNodeCard(sessionView, settings.edgeModeResumed, eval.headline, vm::startSession, vm::stopSession) {
        vm.updateSettings { it.copy(edgeModeResumed = !it.edgeModeResumed) }
    }
    Text("Device metrics", color = EdgeColors.textPrimary, style = MaterialTheme.typography.titleSmall)
    ResourceGrid(
        hw.first,
        hw.second,
        diskReading,
        net,
        com.edgeore.app.ui.components
            .rememberReducedMotion(),
    )
    OreMiningCard(
        deploy,
        wallet.address != null,
        fresh?.lamports,
        onEdit = vm::editOreDeploy,
        onCheck = {
            vm.checkOreDeployCluster()
        },
        snapshot = ore.snapshot,
        nowMs = now,
        onDeploy = {
            vm.prepareOreDeploy()
            onOpenReview()
        },
    )
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
    DailyCheckInCard(
        checkIn,
        now,
        reminders,
        checkInMsg,
        com.edgeore.app.bot.OreBot.tips(
            wallet.address != null,
            sessionView.anyConfigured,
            com.edgeore.app.device.DailyCheckIn
                .available(checkIn, now),
            cluster.label,
        ),
        onCheckIn = vm::checkInNow,
        onReminders = { on ->
            if (on &&
                android.os.Build.VERSION.SDK_INT >= 33 &&
                !com.edgeore.app.bot.OreBot
                    .notificationsAllowed(ctx)
            ) {
                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.setReminders(on)
            }
        },
    )
    EdgeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Now",
                style = MaterialTheme.typography.titleMedium,
                color = EdgeColors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(cluster.label, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
        StatusRow(
            EdgeIcons.Clock,
            "Awaiting an outcome",
            if (waiting ==
                0
            ) {
                "Nothing waiting"
            } else {
                "$waiting operation${if (waiting == 1) "" else "s"} · observe status"
            },
            attention = waiting > 0,
            onClick = onOpenReview,
        )
        StatusRow(
            EdgeIcons.Receipts,
            "Receipt log",
            if (receipts.isEmpty()) "None yet" else "${receipts.size} stored on this phone",
            attention = false,
            onClick = { onOpenTab(com.edgeore.app.ui.Destination.Receipts) },
        )
        StatusRow(
            EdgeIcons.Lock,
            "Vault",
            if (storage.files.isEmpty()) "Empty · encrypt a file in Storage" else "${storage.files.size} encrypted file${if (storage.files.size == 1) "" else "s"}",
            attention = false,
            onClick = { onOpenTab(com.edgeore.app.ui.Destination.Storage) },
        )
    }
    SecondaryAction("Send SOL (devnet transfer review)", Modifier.fillMaxWidth(), onClick = onReview)

    EdgeCard {
        Text("Safety & resource controls", style = MaterialTheme.typography.titleMedium)
        Text(
            "Each control states its effect: Enforced, Saved only or Unavailable. The gates use battery and thermal readings; nothing here caps CPU, memory, storage or network.",
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        ResourceControl(
            EdgeIcons.Bolt,
            "Charge-only mode",
            "Run only when charging",
            settings.chargeOnly,
            gateText(eval, "Charge-only"),
        ) { v ->
            vm.updateSettings {
                it.copy(chargeOnly = v)
            }
        }
        EffectNote(Control.CHARGE_ONLY)
        ResourceControl(
            EdgeIcons.Thermo,
            "Thermal guard",
            "Pause at high temperature",
            settings.thermalGuard,
            gateText(eval, "Thermal guard"),
        ) { v ->
            vm.updateSettings {
                it.copy(thermalGuard = v)
            }
        }
        EffectNote(Control.THERMAL_GUARD)
        LabeledSlider(
            EdgeIcons.Battery,
            "Battery reserve",
            "Keep ${settings.batteryReservePercent}% minimum",
            gateText(eval, "Battery reserve"),
            settings.batteryReservePercent,
            5f..90f,
        ) { v -> vm.updateSettings { it.copy(batteryReservePercent = v) } }
        EffectNote(Control.BATTERY_RESERVE)
        LabeledSlider(
            EdgeIcons.Cpu,
            "CPU limit",
            "Stored limit ${settings.cpuLimitPercent}%",
            "Now: not applied",
            settings.cpuLimitPercent,
            10f..100f,
        ) { v -> vm.updateSettings { it.copy(cpuLimitPercent = v) } }
        EffectNote(Control.CPU_LIMIT, Control.cpuDetail(settings.cpuLimitPercent))
    }

    val observed = ConstraintObservation.of(device, unmetered)
    val sched = ContributionPolicy.view(settings, device, observed, job)
    EdgeCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Contribution scheduler", style = MaterialTheme.typography.titleMedium)
            Text(
                sched.status.label,
                color =
                    if (sched.status ==
                        SchedulerStatus.CHECKING
                    ) {
                        EdgeColors.mint
                    } else {
                        EdgeColors.copper
                    },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(sched.detail, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium)
        ResourceControl(
            EdgeIcons.Wifi,
            "Allow scheduled checks",
            "Check only on Wi-Fi, while charging, battery not low",
            settings.contributionOptIn,
            if (settings.contributionOptIn) "Now: opted in" else "Now: off",
        ) { v ->
            vm.updateSettings {
                it.copy(contributionOptIn = v)
            }
        }
        EffectNote(Control.CONTRIBUTION_SCHEDULER)
        Text(
            ContributionPolicy.constraintLine("Unmetered network (Wi-Fi)", observed.unmetered),
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            ContributionPolicy.constraintLine("Charging", observed.charging),
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            ContributionPolicy.constraintLine("Battery not low", observed.batteryLow?.not()),
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            lastRun?.let { "Last check ${Format.time(it.atMillis)}: ${it.outcome.label}" } ?: "No check has run yet",
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            "The scheduler runs no workload in this build and measures nothing. It does not produce progress or any token.",
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    attention: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(com.edgeore.app.ui.components.CardShape)
            .clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onClick)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(icon, tint = if (attention) EdgeColors.copper else EdgeColors.mint)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = EdgeColors.textPrimary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = if (attention) EdgeColors.copper else EdgeColors.textMuted)
        }
        Icon(EdgeIcons.Chevron, contentDescription = null, tint = EdgeColors.textMuted)
    }
}

@Composable
private fun RowScope.MetricTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
) {
    EdgeCard(Modifier.weight(1f)) {
        Icon(icon, contentDescription = null, tint = EdgeColors.copper, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text("Not measured", color = EdgeColors.copper, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

private fun gateText(
    eval: com.edgeore.app.device.EdgeEvaluation,
    name: String,
): String =
    eval.gates.firstOrNull { it.name == name }?.let { (if (it.allowed) "Now: pass · " else "Now: blocking · ") + it.explanation }
        ?: "Now: off"

@Composable
private fun LabeledSlider(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    explanation: String,
    enforcement: String,
    value: Int,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = EdgeColors.mint)
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(explanation, style = MaterialTheme.typography.bodyMedium, color = EdgeColors.textMuted)
                Text(enforcement, style = MaterialTheme.typography.labelSmall, color = EdgeColors.copper)
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = EdgeColors.mint, activeTrackColor = EdgeColors.mint),
            modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = "$label $value percent" },
        )
    }
}

/** Shown next to Start Mining: exactly what a session runs. The button text is the owner's; this copy is the substance. */
const val SESSION_EXPLAINER =
    "Mine runs a session on your own equipment only: proof jobs on your paired node, and one 256 KiB " +
        "encrypted chunk stored on your storage gateway, sent and read back each round. It repeats until you tap Stop, up to " +
        "$MAX_ROUNDS rounds or ${MAX_DURATION_MS / 60_000} minutes, and only on Wi-Fi with Edge Mode on and your charge, heat and battery limits met. " +
        "Nothing is earned: no ORE, SKR, points or payment."

fun SessionPhase.label(): String =
    when (this) {
        SessionPhase.IDLE -> "Idle"
        SessionPhase.STARTING -> "Starting"
        SessionPhase.ACTIVE -> "Active"
        SessionPhase.STOPPING -> "Stopping"
        SessionPhase.FAILED -> "Failed"
    }

/**
 * The rock-and-pickaxe art with the Start Mining / Stop control and the session's parts. Stateless, so it renders on
 * the JVM. The pickaxe swings only while [view] is ACTIVE; every number shown comes from a [Measurement] reported
 * by a running part, and a part that is not configured shows what to set up instead of a value.
 */
@Composable
fun MiningSessionPanel(
    view: SessionView,
    edgeModeResumed: Boolean,
    headline: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleEdgeMode: () -> Unit,
) {
    val acknowledged = view.parts.any { it.rounds > 0 }
    val motionPhase = if (view.phase == SessionPhase.ACTIVE && !acknowledged) SessionPhase.STARTING else view.phase
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.edgeore.app.ui.components.HoloRing(200.dp, bright = view.phase == SessionPhase.ACTIVE) {
            MiningAnimation(motionPhase, Modifier.size(width = 190.dp, height = 150.dp))
        }
        if (view.phase ==
            SessionPhase.STARTING
        ) {
            Text(
                "Starting… waiting for your node and gateway to acknowledge",
                color = EdgeColors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        // From PR #20: the pickaxe swings only after a part has reported a round, never on ACTIVE alone.
        if (view.phase == SessionPhase.ACTIVE &&
            !acknowledged
        ) {
            Text("Waiting for first worker result…", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            "Session: " + view.phase.label(),
            fontWeight = FontWeight.Bold,
            color =
                if (view.phase ==
                    SessionPhase.FAILED
                ) {
                    EdgeColors.copper
                } else {
                    EdgeColors.textPrimary
                },
            modifier = Modifier.semantics { contentDescription = "Contribution session ${view.phase.label()}" },
        )
        when (view.phase) {
            SessionPhase.IDLE, SessionPhase.FAILED ->
                PrimaryAction("Mine", Modifier.testTag("start-mining"), icon = EdgeIcons.Play, onClick = onStart)
            SessionPhase.STARTING, SessionPhase.ACTIVE ->
                PrimaryAction("Stop", Modifier.testTag("stop-mining"), icon = EdgeIcons.Pause, onClick = onStop)
            SessionPhase.STOPPING ->
                PrimaryAction(
                    "Stopping…",
                    Modifier.testTag("stopping-mining"),
                    enabled = false,
                    loading = true,
                    onClick = {},
                )
        }
        Text(
            SESSION_EXPLAINER,
            color = EdgeColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        view.failure
            ?.takeIf {
                view.phase == SessionPhase.FAILED
            }?.let { Text(it, color = EdgeColors.copper, style = MaterialTheme.typography.bodyMedium) }
        view.note
            ?.takeIf {
                view.phase == SessionPhase.IDLE
            }?.let { Text(it, color = EdgeColors.textMuted, style = MaterialTheme.typography.bodyMedium) }
        if (!view.anyConfigured) {
            Text(
                "Nothing to run yet. Pair a node (Nodes) or set up your storage gateway (Storage).",
                color = EdgeColors.copper,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        EdgeCard {
            Text("What this session runs", style = MaterialTheme.typography.titleMedium)
            view.parts.forEach { SessionPartRow(it) }
        }
        SecondaryAction(if (edgeModeResumed) "Pause Edge Mode" else "Resume Edge Mode", Modifier.fillMaxWidth(), onClick = onToggleEdgeMode)
        Text(headline, color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SessionPartRow(p: PartView) {
    val (status, color) =
        when (val st = p.status) {
            is PartStatus.NotConfigured -> st.setup to EdgeColors.copper
            PartStatus.Ready -> "Ready" to EdgeColors.textMuted
            PartStatus.Running -> "Running · round ${p.rounds + 1}" to EdgeColors.mint
            PartStatus.Waiting -> "Waiting for its next round · ${p.rounds} done" to EdgeColors.mint
            is PartStatus.Done -> "Done: ${st.detail}" to EdgeColors.mint
            PartStatus.Cancelled -> "Cancelled" to EdgeColors.copper
            is PartStatus.Failed -> "Failed: ${st.reason}" to EdgeColors.copper
        }
    Column(Modifier.fillMaxWidth().testTag("session-part-${p.kind.name}").semantics(mergeDescendants = true) {}) {
        Text(p.kind.title, fontWeight = FontWeight.SemiBold, color = EdgeColors.textPrimary)
        Text(status, color = color, style = MaterialTheme.typography.bodyMedium)
        p.measurements.forEach { m ->
            Text(measurementLine(m), color = EdgeColors.textPrimary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

fun measurementLine(m: Measurement): String {
    val v =
        when (m.unit) {
            "bytes" -> "%,d bytes".format(m.value)
            "ms" -> if (m.value >= 1000) "%.1f s".format(m.value / 1000.0) else "${m.value} ms"
            else -> "${m.value} " + if (m.value == 1L && m.unit.endsWith("s")) m.unit.dropLast(1) else m.unit
        }
    return "${m.label}: $v · ${m.source}"
}

/** The central ORE icon on Mine: opens the read-only ORE v3 board. */
@Composable
fun OreEntry(onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(role = Role.Button, onClick = onOpen)
                .testTag("open-ore")
                .semantics(mergeDescendants = true) { contentDescription = "Open the ORE v3 board, read-only" }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(EdgeIcons.Ore, contentDescription = null, tint = EdgeColors.copper, modifier = Modifier.size(40.dp))
            Text("ORE board", fontWeight = FontWeight.SemiBold, color = EdgeColors.textPrimary)
            Text("Read-only · mainnet observation", color = EdgeColors.textMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** CPU cores and a memory class, as Android reports them. Usage is not sampled. */
object DeviceFacts {
    @androidx.annotation.VisibleForTesting
    @Volatile internal var testSource: ((android.content.Context) -> Pair<Int?, Int?>)? = null

    fun read(ctx: android.content.Context): Pair<Int?, Int?> {
        testSource?.let { return it(ctx) }
        val cores = Runtime.getRuntime().availableProcessors().takeIf { it > 0 }
        val am = ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val mem =
            am
                ?.let {
                    val mi = android.app.ActivityManager.MemoryInfo()
                    it.getMemoryInfo(mi)
                    mi.totalMem
                }?.takeIf { it > 0 }
        return cores to mem?.let { gbClass(it) }
    }

    /** Total RAM rounded up to the next whole GB (kernel-reserved memory makes totalMem read a little low). */
    fun gbClass(totalBytes: Long): Int = ((totalBytes + (1L shl 30) - 1) / (1L shl 30)).toInt()
}
