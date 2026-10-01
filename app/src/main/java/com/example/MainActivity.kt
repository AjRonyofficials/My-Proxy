package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ProxyEntity
import com.example.service.MyProxyVpnService
import com.example.ui.ProxyViewModel
import com.example.ui.screens.MainDashboardScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewModel: ProxyViewModel = viewModel()
                var pendingProxy by remember { mutableStateOf<ProxyEntity?>(null) }

                // VpnService permission launcher
                val vpnPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        pendingProxy?.let { proxy ->
                            startProxyTunnel(proxy)
                            pendingProxy = null
                        }
                    } else {
                        Toast.makeText(this, "VPN permission denied by user", Toast.LENGTH_SHORT).show()
                    }
                }

                // Notification permission launcher for Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* optional outcome */ }

                // Overlay permission launcher for Floating Bubble
                val overlayPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { /* check overlay */ }

                MainDashboardScreen(
                    viewModel = viewModel,
                    onConnectRequested = { proxy ->
                        // Request notification permission if Android 13+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }

                        // Check overlay permission if floating bubble enabled
                        if (viewModel.floatingBubbleEnabled.value &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !Settings.canDrawOverlays(this)
                        ) {
                            runCatching {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                overlayPermissionLauncher.launch(intent)
                            }
                        }

                        // Check VPN Permission
                        val vpnIntent = VpnService.prepare(this)
                        if (vpnIntent != null) {
                            pendingProxy = proxy
                            vpnPermissionLauncher.launch(vpnIntent)
                        } else {
                            startProxyTunnel(proxy)
                        }
                    },
                    onDisconnectRequested = {
                        stopProxyTunnel()
                    }
                )
            }
        }
    }

    private fun startProxyTunnel(proxy: ProxyEntity) {
        val intent = Intent(this, MyProxyVpnService::class.java).apply {
            action = MyProxyVpnService.ACTION_START
            putExtra("ID", proxy.id)
            putExtra("NAME", proxy.name)
            putExtra("HOST", proxy.host)
            putExtra("PORT", proxy.port)
            putExtra("TYPE", proxy.type)
            putExtra("USER", proxy.user)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Toast.makeText(this, "Starting tunnel to ${proxy.name}...", Toast.LENGTH_SHORT).show()
    }

    private fun stopProxyTunnel() {
        val intent = Intent(this, MyProxyVpnService::class.java).apply {
            action = MyProxyVpnService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "Tunnel disconnected", Toast.LENGTH_SHORT).show()
    }
}
