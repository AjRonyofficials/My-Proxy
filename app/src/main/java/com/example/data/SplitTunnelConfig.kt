package com.example.data

import android.content.Context
import android.content.SharedPreferences

enum class SplitTunnelMode {
    ALL,              // Route all traffic through proxy
    ROUTE_SELECTED,   // Only route traffic from selected apps
    BYPASS_SELECTED   // Bypass proxy for selected apps
}

class SplitTunnelConfig(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("split_tunnel_prefs", Context.MODE_PRIVATE)

    var mode: SplitTunnelMode
        get() {
            val name = prefs.getString("split_mode", SplitTunnelMode.ALL.name)
                ?: SplitTunnelMode.ALL.name
            return runCatching { SplitTunnelMode.valueOf(name) }.getOrDefault(SplitTunnelMode.ALL)
        }
        set(value) = prefs.edit().putString("split_mode", value.name).apply()

    fun getSelectedPackages(): Set<String> {
        return prefs.getStringSet("selected_packages", emptySet()) ?: emptySet()
    }

    fun setSelectedPackages(packages: Set<String>) {
        prefs.edit().putStringSet("selected_packages", packages).apply()
    }

    fun togglePackage(packageName: String): Boolean {
        val current = getSelectedPackages().toMutableSet()
        val newState = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        setSelectedPackages(current)
        return newState
    }

    var floatingBubbleEnabled: Boolean
        get() = prefs.getBoolean("floating_bubble_enabled", true)
        set(value) = prefs.edit().putBoolean("floating_bubble_enabled", value).apply()

    var selectedDns: String
        get() = prefs.getString("selected_dns", "Cloudflare (1.1.1.1)") ?: "Cloudflare (1.1.1.1)"
        set(value) = prefs.edit().putString("selected_dns", value).apply()

    fun getDnsServers(): List<String> {
        return when {
            selectedDns.contains("Google") -> listOf("8.8.8.8", "8.8.4.4")
            selectedDns.contains("Quad9") -> listOf("9.9.9.9", "149.112.112.112")
            selectedDns.contains("AdGuard") -> listOf("94.140.14.14", "94.140.15.15")
            else -> listOf("1.1.1.1", "1.0.0.1") // Cloudflare default
        }
    }
}
