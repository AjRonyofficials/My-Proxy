package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.ProxyEntity
import com.example.ui.ProxyViewModel
import com.example.ui.components.ActiveProxyCard
import com.example.ui.components.CyberGlowDial
import com.example.ui.components.FeatureActionTiles
import com.example.ui.sheets.DnsSettingsSheet
import com.example.ui.sheets.GeoDetailsSheet
import com.example.ui.sheets.ProxyListSheet
import com.example.ui.sheets.SplitTunnelSheet
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    viewModel: ProxyViewModel,
    onConnectRequested: (ProxyEntity) -> Unit,
    onDisconnectRequested: () -> Unit
) {
    val proxies by viewModel.proxies.collectAsStateWithLifecycle()
    val selectedProxy by viewModel.selectedProxy.collectAsStateWithLifecycle()
    val isConnected by viewModel.vpnRunning.collectAsStateWithLifecycle()
    val durationSeconds by viewModel.durationSeconds.collectAsStateWithLifecycle()
    val traffic by viewModel.traffic.collectAsStateWithLifecycle()
    val geoInfo by viewModel.geoInfo.collectAsStateWithLifecycle()
    val isResolvingGeo by viewModel.isResolvingGeo.collectAsStateWithLifecycle()
    val isPinging by viewModel.isPinging.collectAsStateWithLifecycle()

    val splitMode by viewModel.splitTunnelMode.collectAsStateWithLifecycle()
    val selectedPackages by viewModel.selectedPackages.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val isLoadingApps by viewModel.isLoadingApps.collectAsStateWithLifecycle()

    val floatingBubbleEnabled by viewModel.floatingBubbleEnabled.collectAsStateWithLifecycle()
    val selectedDns by viewModel.selectedDns.collectAsStateWithLifecycle()

    var showProxySheet by remember { mutableStateOf(false) }
    var showSplitSheet by remember { mutableStateOf(false) }
    var showGeoSheet by remember { mutableStateOf(false) }
    var showDnsSheet by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            TopAppBarRow(
                isConnected = isConnected,
                geoFlag = geoInfo?.flag ?: "🌐",
                exitIp = geoInfo?.ip ?: "Standby",
                onGeoClick = { showGeoSheet = true },
                onSettingsClick = { showDnsSheet = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Cyber Glow Dial (Pulsating Rings, Power Button, Stopwatch, Live Speeds)
            CyberGlowDial(
                isConnected = isConnected,
                durationSeconds = durationSeconds,
                traffic = traffic,
                onToggleClick = {
                    if (isConnected) {
                        onDisconnectRequested()
                    } else {
                        selectedProxy?.let { onConnectRequested(it) }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Active Proxy Profile Card
            ActiveProxyCard(
                proxy = selectedProxy,
                isPinging = isPinging,
                onSelectServerClick = { showProxySheet = true },
                onPingClick = { viewModel.pingSelectedProxy() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Feature Grid (Split Tunneling, Floating Widget, DNS Shield, Geo/IP)
            FeatureActionTiles(
                splitMode = splitMode,
                selectedPackagesCount = selectedPackages.size,
                floatingBubbleEnabled = floatingBubbleEnabled,
                dnsProvider = selectedDns,
                geoInfo = geoInfo,
                onSplitTunnelClick = { showSplitSheet = true },
                onFloatingBubbleToggle = { viewModel.toggleFloatingBubble(it) },
                onDnsClick = { showDnsSheet = true },
                onGeoClick = { showGeoSheet = true }
            )

            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Modal Bottom Sheets
    if (showProxySheet) {
        ProxyListSheet(
            proxies = proxies,
            selectedProxy = selectedProxy,
            onSelectProxy = {
                viewModel.selectProxy(it)
                showProxySheet = false
            },
            onAddProxy = { name, type, host, port, user, pass ->
                viewModel.addProxy(name, type, host, port, user, pass)
            },
            onDeleteProxy = {
                viewModel.deleteProxy(it)
            },
            onDismiss = { showProxySheet = false }
        )
    }

    if (showSplitSheet) {
        SplitTunnelSheet(
            mode = splitMode,
            selectedPackages = selectedPackages,
            installedApps = installedApps,
            isLoadingApps = isLoadingApps,
            onLoadApps = { viewModel.loadInstalledApps() },
            onSetMode = { viewModel.setSplitTunnelMode(it) },
            onTogglePackage = { viewModel.toggleAppPackage(it) },
            onSelectAll = { viewModel.selectAllApps() },
            onClearAll = { viewModel.clearAllApps() },
            onDismiss = { showSplitSheet = false }
        )
    }

    if (showGeoSheet) {
        GeoDetailsSheet(
            geoInfo = geoInfo,
            isResolving = isResolvingGeo,
            isConnected = isConnected,
            onRefresh = { viewModel.refreshGeoInfo() },
            onDismiss = { showGeoSheet = false }
        )
    }

    if (showDnsSheet) {
        DnsSettingsSheet(
            selectedDns = selectedDns,
            onSelectDns = { viewModel.setDnsServer(it) },
            onDismiss = { showDnsSheet = false }
        )
    }
}

@Composable
private fun TopAppBarRow(
    isConnected: Boolean,
    geoFlag: String,
    exitIp: String,
    onGeoClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Title & Glowing Beacon
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(CyberGreen.copy(alpha = 0.15f))
                    .border(1.dp, CyberGreen.copy(alpha = 0.3f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = CyberGreen,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MY PROXY",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) CyberGreen else CyberCyan)
                    )
                }
                Text(
                    text = if (isConnected) "Tunnel Protected" else "Ready to Connect",
                    color = if (isConnected) CyberGreen else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Live IP Pill & Settings Button
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, RoundedCornerShape(20.dp))
                    .clickable(onClick = onGeoClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = geoFlag, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = exitIp,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DarkSurface)
                    .border(1.dp, DarkSurfaceBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "DNS & Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
