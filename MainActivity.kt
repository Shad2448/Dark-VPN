package com.example.freevpn

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

enum class VpnState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class VpnServer(
    val id: String,
    val name: String,
    val region: String,
    val category: String,
    val flagEmoji: String,
    val primaryDns: String,
    val secondaryDns: String,
    val pingMs: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00F2FE),
                    secondary = Color(0xFF8B5CF6),
                    tertiary = Color(0xFF10B981),
                    background = Color(0xFF05070D),
                    surface = Color(0xFF0E1322),
                    onBackground = Color(0xFFF9FAFB),
                    onSurface = Color(0xFFF3F4F6)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DarkVpnScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DarkVpnScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 15 Ultra-Low-Latency Global Anycast & Gaming Servers
    val servers = remember {
        mutableStateListOf(
            VpnServer("cf-asia", "Cloudflare Warp+", "Singapore • Ultra-Low Latency", "Ultra-Fast", "🇸🇬", "1.1.1.1", "1.0.0.1", 8),
            VpnServer("google-edge", "Google Fiber Edge", "Mumbai / South Asia Anycast", "Ultra-Fast", "🇮🇳", "8.8.8.8", "8.8.4.4", 11),
            VpnServer("cf-gaming", "Cloudflare Gaming Core", "Tokyo • Direct Peering", "Gaming", "🇯🇵", "1.0.0.1", "1.1.1.1", 14),
            VpnServer("quad9-ch", "Quad9 Swiss Vault", "Zurich • Zero-Log Encrypted", "Ultra-Fast", "🇨🇭", "9.9.9.9", "149.112.112.112", 19),
            VpnServer("adguard-nl", "AdGuard CyberShield", "Amsterdam • Blocks Ads & Trackers", "AdBlock", "🇳🇱", "94.140.14.14", "94.140.15.15", 21),
            VpnServer("controld-fast", "Control D Turbo", "Frankfurt • High-Speed Core", "Gaming", "🇩🇪", "76.76.2.0", "76.76.10.0", 22),
            VpnServer("dnssb-hk", "DNS.SB Asia Backbone", "Hong Kong • 100Gbps Node", "Gaming", "🇭🇰", "185.222.222.222", "45.11.45.11", 16),
            VpnServer("level3-global", "Lumen Tier-1 Backbone", "Global Anycast • Low Jitter", "Gaming", "🚀", "4.2.2.1", "4.2.2.2", 15),
            VpnServer("adguard-uk", "AdGuard Speed Node", "London • Non-Filtering Fast", "Ultra-Fast", "🇬🇧", "94.140.14.140", "94.140.14.141", 25),
            VpnServer("quad9-de", "Quad9 Speed Unfiltered", "Frankfurt • Low Ping Route", "Gaming", "🇩🇪", "9.9.9.10", "149.112.112.10", 20),
            VpnServer("opendns-us", "Cisco OpenDNS Core", "San Jose • US West Coast", "Ultra-Fast", "🇺🇸", "208.67.222.222", "208.67.220.220", 28),
            VpnServer("controld-ad", "Control D AdBlock", "Toronto • Ad & Malware Shield", "AdBlock", "🇨🇦", "76.76.2.2", "76.76.10.2", 26),
            VpnServer("cf-sec", "Cloudflare Malware Shield", "Sydney • Protected Anycast", "AdBlock", "🇦🇺", "1.1.1.2", "1.0.0.2", 23),
            VpnServer("cleanbrowsing-fr", "CleanBrowsing Shield", "Paris • Security Filter", "AdBlock", "🇫🇷", "185.228.168.9", "185.228.169.9", 29),
            VpnServer("verisign-us", "Verisign Ultra Stable", "Virginia • US East Backbone", "Ultra-Fast", "🇺🇸", "64.6.64.6", "64.6.65.6", 31)
        )
    }

    var selectedCategory by remember { mutableStateOf("All") }
    var selectedServer by remember { mutableStateOf(servers.first()) }
    var vpnState by remember { mutableStateOf(VpnState.DISCONNECTED) }
    var connectedSeconds by remember { mutableLongStateOf(0L) }
    var rxBytes by remember { mutableLongStateOf(0L) }
    var txBytes by remember { mutableLongStateOf(0L) }
    var rxSpeed by remember { mutableLongStateOf(0L) }
    var txSpeed by remember { mutableLongStateOf(0L) }
    var peakSpeedBps by remember { mutableLongStateOf(0L) }
    var isTestingSpeed by remember { mutableStateOf(false) }
    var isPingingAll by remember { mutableStateOf(false) }

    // Settings state
    var splitTunnelingBrowserOnly by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showImportConfigDialog by remember { mutableStateOf(false) }

    // Listen to status updates broadcast from FreeVpnService
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    FreeVpnService.ACTION_STATUS_BROADCAST -> {
                        val stateStr = intent.getStringExtra(FreeVpnService.EXTRA_STATE)
                        vpnState = when (stateStr) {
                            "CONNECTED" -> VpnState.CONNECTED
                            "CONNECTING" -> VpnState.CONNECTING
                            else -> VpnState.DISCONNECTED
                        }
                        rxBytes = intent.getLongExtra(FreeVpnService.EXTRA_RX_BYTES, 0L)
                        txBytes = intent.getLongExtra(FreeVpnService.EXTRA_TX_BYTES, 0L)
                        rxSpeed = intent.getLongExtra(FreeVpnService.EXTRA_RX_SPEED, 0L)
                        txSpeed = intent.getLongExtra(FreeVpnService.EXTRA_TX_SPEED, 0L)
                        if (rxSpeed + txSpeed > peakSpeedBps) {
                            peakSpeedBps = rxSpeed + txSpeed
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(FreeVpnService.ACTION_STATUS_BROADCAST)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    // Session timer while connected
    LaunchedEffect(vpnState) {
        if (vpnState == VpnState.CONNECTED) {
            while (vpnState == VpnState.CONNECTED) {
                delay(1000L)
                connectedSeconds += 1L
            }
        } else if (vpnState == VpnState.DISCONNECTED) {
            connectedSeconds = 0L
            rxBytes = 0L
            txBytes = 0L
            rxSpeed = 0L
            txSpeed = 0L
            peakSpeedBps = 0L
        }
    }

    fun startVpnService(server: VpnServer) {
        vpnState = VpnState.CONNECTING
        val serviceIntent = Intent(context, FreeVpnService::class.java).apply {
            action = FreeVpnService.ACTION_CONNECT
            putExtra(FreeVpnService.EXTRA_SERVER_NAME, server.name)
            putExtra(FreeVpnService.EXTRA_DNS_SERVER, server.primaryDns)
            putExtra(FreeVpnService.EXTRA_SECONDARY_DNS, server.secondaryDns)
            putExtra(FreeVpnService.EXTRA_SPLIT_TUNNEL, splitTunnelingBrowserOnly)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopVpnService() {
        val serviceIntent = Intent(context, FreeVpnService::class.java).apply {
            action = FreeVpnService.ACTION_DISCONNECT
        }
        context.startService(serviceIntent)
        vpnState = VpnState.DISCONNECTED
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService(selectedServer)
        } else {
            vpnState = VpnState.DISCONNECTED
        }
    }

    fun onToggleVpn() {
        if (vpnState == VpnState.CONNECTED || vpnState == VpnState.CONNECTING) {
            stopVpnService()
        } else {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                startVpnService(selectedServer)
            }
        }
    }

    // Live TCP latency ping to all servers & auto-select lowest latency
    fun pingAllAndSelectFastest() {
        if (isPingingAll) return
        isPingingAll = true
        scope.launch {
            val updated = withContext(Dispatchers.IO) {
                servers.map { srv ->
                    async {
                        val measured = measureTcpLatencyMs(srv.primaryDns, 53, srv.pingMs)
                        srv.copy(pingMs = measured)
                    }
                }.awaitAll().sortedBy { it.pingMs }
            }
            servers.clear()
            servers.addAll(updated)
            val fastest = servers.first()
            selectedServer = fastest
            if (vpnState == VpnState.CONNECTED) {
                startVpnService(fastest)
            }
            isPingingAll = false
        }
    }

    // Live active bandwidth test to sweep the speedometer gauge
    fun runLiveSpeedBenchmark() {
        if (isTestingSpeed) return
        isTestingSpeed = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val testUrls = listOf(
                        "https://speed.cloudflare.com/__down?bytes=5000000",
                        "https://proof.ovh.net/files/1Mb.dat"
                    )
                    for (urlStr in testUrls) {
                        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 3000
                            readTimeout = 4000
                            setRequestProperty("User-Agent", "DarkVPN-SpeedBenchmark/2.0")
                        }
                        val buffer = ByteArray(16384)
                        conn.inputStream.use { input ->
                            val startMs = System.currentTimeMillis()
                            while (input.read(buffer) != -1) {
                                if (System.currentTimeMillis() - startMs > 3500) break
                            }
                        }
                        conn.disconnect()
                        break
                    }
                } catch (_: Exception) {
                }
            }
            isTestingSpeed = false
        }
    }

    val filteredServers = remember(selectedCategory, servers.size, servers.toList()) {
        if (selectedCategory == "All") servers
        else servers.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Dark VPN",
                                tint = Color(0xFF05070D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "DARK VPN",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                letterSpacing = 1.5.sp,
                                color = Color.White
                            )
                            Text(
                                text = "HYPER-SPEED ANYCAST CORE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                color = Color(0xFF00F2FE)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { pingAllAndSelectFastest() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Auto-Ping Fastest Server",
                            tint = if (isPingingAll) Color(0xFF00F2FE) else Color(0xFF9CA3AF)
                        )
                    }
                    IconButton(onClick = { showImportConfigDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Custom Node"
                        )
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF05070D),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color(0xFF9CA3AF)
                )
            )
        },
        containerColor = Color(0xFF05070D)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                StatusBadge(
                    vpnState = vpnState,
                    pingMs = selectedServer.pingMs,
                    serverName = selectedServer.name
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Animated Speedometer Gauge + Connect Dashboard Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF11182B), Color(0xFF0A0E1A))
                            )
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00F2FE).copy(alpha = 0.45f),
                                    Color(0xFF8B5CF6).copy(alpha = 0.25f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Row: Animated Speedometer Gauge & Animated Cyber Connect Orb
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SpeedometerGauge(
                            bytesPerSec = rxSpeed + txSpeed,
                            pingMs = selectedServer.pingMs,
                            isConnected = vpnState == VpnState.CONNECTED
                        )

                        CyberConnectOrb(
                            vpnState = vpnState,
                            durationText = formatDuration(connectedSeconds),
                            onClick = { onToggleVpn() }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Download / Upload / Peak Telemetry Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryCard(
                            label = "DOWNLOAD",
                            speedValue = formatSpeed(rxSpeed),
                            totalValue = formatBytes(rxBytes),
                            accentColor = Color(0xFF00F2FE),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryCard(
                            label = "UPLOAD",
                            speedValue = formatSpeed(txSpeed),
                            totalValue = formatBytes(txBytes),
                            accentColor = Color(0xFF8B5CF6),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryCard(
                            label = "PEAK SPEED",
                            speedValue = formatSpeed(peakSpeedBps),
                            totalValue = "${selectedServer.pingMs} ms ping",
                            accentColor = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons Row: Speed Test + Auto-Select Fastest Node
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { runLiveSpeedBenchmark() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF19223B),
                                contentColor = Color(0xFF00F2FE)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isTestingSpeed) "TESTING SPEED..." else "⚡ SPEED TEST",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { pingAllAndSelectFastest() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF19223B),
                                contentColor = Color(0xFF10B981)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (isPingingAll) "PINGING 15 NODES..." else "🚀 FASTEST SERVER",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
            }

            // Category Filter Tabs
            item {
                val categories = listOf("All", "Ultra-Fast", "Gaming", "AdBlock")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val active = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (active) Brush.horizontalGradient(
                                        listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6))
                                    )
                                    else Brush.horizontalGradient(
                                        listOf(Color(0xFF11182B), Color(0xFF11182B))
                                    )
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (cat == "All") "All Nodes (${servers.size})" else cat,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color(0xFF05070D) else Color(0xFF9CA3AF)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Server List Items
            items(filteredServers, key = { it.id }) { server ->
                ServerRowItem(
                    server = server,
                    isSelected = server.id == selectedServer.id,
                    onSelect = {
                        val wasConnected = vpnState == VpnState.CONNECTED
                        selectedServer = server
                        if (wasConnected) {
                            startVpnService(server)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    if (showSettingsSheet) {
        AlertDialog(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = Color(0xFF0E1322),
            title = {
                Text("Dark VPN Turbo Settings", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SettingToggleRow(
                        title = "Split Tunneling (Browsers Only)",
                        subtitle = "Apply Dark VPN only to web browsers while keeping games direct",
                        checked = splitTunnelingBrowserOnly,
                        onCheckedChange = { splitTunnelingBrowserOnly = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsSheet = false }) {
                    Text("Done", color = Color(0xFF00F2FE))
                }
            }
        )
    }

    if (showImportConfigDialog) {
        var nameInput by remember { mutableStateOf("") }
        var primaryDnsInput by remember { mutableStateOf("") }
        var secondaryDnsInput by remember { mutableStateOf("1.0.0.1") }

        AlertDialog(
            onDismissRequest = { showImportConfigDialog = false },
            containerColor = Color(0xFF0E1322),
            title = {
                Text("Add Custom High-Speed Node", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Server Label (e.g. Singapore Gaming)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = primaryDnsInput,
                        onValueChange = { primaryDnsInput = it },
                        label = { Text("Primary Anycast / DNS IP") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = secondaryDnsInput,
                        onValueChange = { secondaryDnsInput = it },
                        label = { Text("Secondary IP") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isNotBlank() && primaryDnsInput.isNotBlank()) {
                            val newServer = VpnServer(
                                id = "custom-${System.currentTimeMillis()}",
                                name = nameInput.trim(),
                                region = "Custom Ultra-Low Latency Node",
                                category = "Ultra-Fast",
                                flagEmoji = "⚡",
                                primaryDns = primaryDnsInput.trim(),
                                secondaryDns = secondaryDnsInput.trim().ifBlank { "1.1.1.1" },
                                pingMs = 10
                            )
                            servers.add(0, newServer)
                            selectedServer = newServer
                            showImportConfigDialog = false
                        }
                    }
                ) {
                    Text("Save Node", color = Color(0xFF00F2FE))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfigDialog = false }) {
                    Text("Cancel", color = Color(0xFF9CA3AF))
                }
            }
        )
    }
}

/**
 * Animated Neon Speedometer Gauge drawn with Jetpack Compose Canvas.
 * Smoothly sweeps needle across a logarithmic Mbps scale from 0 to 100+ Mbps.
 */
@Composable
fun SpeedometerGauge(
    bytesPerSec: Long,
    pingMs: Int,
    isConnected: Boolean
) {
    val mbps = (bytesPerSec * 8.0) / 1_000_000.0
    // Logarithmic normalization so both KB/s browsing and 100 Mbps downloads move the needle smoothly
    val targetFraction = if (!isConnected && bytesPerSec == 0L) {
        0f
    } else {
        val logVal = (ln(1.0 + mbps * 12.0) / ln(1.0 + 100.0 * 12.0)).toFloat()
        logVal.coerceIn(0.03f, 1f)
    }

    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "speedNeedle"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(165.dp)
    ) {
        Canvas(modifier = Modifier.size(155.dp)) {
            val strokeWidth = 13.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

            // Background track arc (150 deg to 390 deg -> 240 deg sweep)
            drawArc(
                color = Color(0xFF1E293B),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Tick marks around the speedometer dial
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = (size.width - strokeWidth) / 2f - 10.dp.toPx()
            for (i in 0..24) {
                val angleDeg = 150f + (i * 10f)
                val rad = Math.toRadians(angleDeg.toDouble())
                val isMajor = i % 4 == 0
                val innerR = outerRadius - if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                val startPt = Offset(
                    x = center.x + (innerR * cos(rad)).toFloat(),
                    y = center.y + (innerR * sin(rad)).toFloat()
                )
                val endPt = Offset(
                    x = center.x + (outerRadius * cos(rad)).toFloat(),
                    y = center.y + (outerRadius * sin(rad)).toFloat()
                )
                drawLine(
                    color = if (i / 24f <= animatedFraction) Color(0xFF00F2FE) else Color(0xFF475569),
                    start = startPt,
                    end = endPt,
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Active neon gradient arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF00F2FE),
                        Color(0xFF8B5CF6),
                        Color(0xFF10B981),
                        Color(0xFF00F2FE)
                    )
                ),
                startAngle = 150f,
                sweepAngle = (240f * animatedFraction).coerceAtLeast(4f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Animated glowing needle
            val needleAngleDeg = 150f + 240f * animatedFraction
            val needleRad = Math.toRadians(needleAngleDeg.toDouble())
            val needleLength = outerRadius - 6.dp.toPx()
            val needleTip = Offset(
                x = center.x + (needleLength * cos(needleRad)).toFloat(),
                y = center.y + (needleLength * sin(needleRad)).toFloat()
            )
            drawLine(
                color = Color(0xFF00F2FE),
                start = center,
                end = needleTip,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFF8B5CF6),
                radius = 6.dp.toPx(),
                center = center
            )
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = center
            )
        }

        // Digital readout at bottom of gauge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 56.dp)
        ) {
            Text(
                text = String.format("%.2f", mbps),
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Text(
                text = "Mbps • ${pingMs}ms",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00F2FE)
            )
        }
    }
}

/**
 * Animated Cyber Orb Connect button with rotating radar ring and pulsing aura.
 */
@Composable
fun CyberConnectOrb(
    vpnState: VpnState,
    durationText: String,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = if (vpnState != VpnState.DISCONNECTED) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val primaryGlow by animateColorAsState(
        targetValue = when (vpnState) {
            VpnState.CONNECTED -> Color(0xFF00F2FE)
            VpnState.CONNECTING -> Color(0xFFF59E0B)
            VpnState.DISCONNECTED -> Color(0xFF475569)
        },
        label = "glow"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(150.dp)
    ) {
        // Pulsing outer cyber aura
        Box(
            modifier = Modifier
                .size(144.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(primaryGlow.copy(alpha = 0.14f))
        )

        // Rotating gradient ring
        Box(
            modifier = Modifier
                .size(130.dp)
                .rotate(if (vpnState != VpnState.DISCONNECTED) rotation else 0f)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primaryGlow,
                            Color(0xFF8B5CF6),
                            primaryGlow.copy(alpha = 0.2f),
                            primaryGlow
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Core clickable button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1B243B), Color(0xFF090D18))
                    )
                )
                .clickable { onClick() }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (vpnState == VpnState.CONNECTING) Icons.Default.Refresh else Icons.Default.Lock,
                    contentDescription = "Connect Dark VPN",
                    tint = primaryGlow,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (vpnState) {
                        VpnState.CONNECTED -> "CONNECTED"
                        VpnState.CONNECTING -> "LINKING..."
                        VpnState.DISCONNECTED -> "TAP TO LINK"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = durationText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = primaryGlow
                )
            }
        }
    }
}

@Composable
fun StatusBadge(vpnState: VpnState, pingMs: Int, serverName: String) {
    val bgColor = when (vpnState) {
        VpnState.CONNECTED -> Color(0xFF062C30)
        VpnState.CONNECTING -> Color(0xFF3B2106)
        VpnState.DISCONNECTED -> Color(0xFF141B2D)
    }
    val dotColor = when (vpnState) {
        VpnState.CONNECTED -> Color(0xFF00F2FE)
        VpnState.CONNECTING -> Color(0xFFF59E0B)
        VpnState.DISCONNECTED -> Color(0xFFEF4444)
    }
    val text = when (vpnState) {
        VpnState.CONNECTED -> "DARK TUNNEL ACTIVE • $serverName ($pingMs ms)"
        VpnState.CONNECTING -> "INITIALIZING HYPER-SPEED TUNNEL..."
        VpnState.DISCONNECTED -> "TUNNEL STANDBY • SELECT ANYCAST NODE"
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .border(1.dp, dotColor.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
fun TelemetryCard(
    label: String,
    speedValue: String,
    totalValue: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF090D18))
            .border(1.dp, accentColor.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9CA3AF),
            letterSpacing = 0.7.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = speedValue,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = accentColor
        )
        Text(
            text = totalValue,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF64748B)
        )
    }
}

@Composable
fun ServerRowItem(
    server: VpnServer,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderBrush = if (isSelected) {
        Brush.horizontalGradient(listOf(Color(0xFF00F2FE), Color(0xFF8B5CF6)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFF141D33) else Color(0xFF0E1322))
            .border(1.5.dp, borderBrush, RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(text = server.flagEmoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = server.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = server.category.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00F2FE)
                        )
                    }
                }
                Text(
                    text = "${server.region} • ${server.primaryDns}",
                    fontSize = 11.sp,
                    color = Color(0xFF9CA3AF)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val pingColor = when {
                server.pingMs <= 20 -> Color(0xFF10B981)
                server.pingMs <= 45 -> Color(0xFF00F2FE)
                else -> Color(0xFFF59E0B)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(pingColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${server.pingMs} ms",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = pingColor
                )
            }
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color(0xFF00F2FE),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFF9CA3AF), fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun measureTcpLatencyMs(host: String, port: Int, fallbackMs: Int): Int {
    return try {
        val start = System.currentTimeMillis()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 1200)
        }
        (System.currentTimeMillis() - start).toInt().coerceIn(2, 999)
    } catch (_: Exception) {
        fallbackMs
    }
}

private fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec < 1024) return "$bytesPerSec B/s"
    val kb = bytesPerSec / 1024.0
    if (kb < 1024) return String.format("%.1f KB/s", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB/s", mb)
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
