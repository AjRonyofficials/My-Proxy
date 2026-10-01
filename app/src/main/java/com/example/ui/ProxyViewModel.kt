package com.example.ui

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MyProxyApp
import com.example.data.ProxyEntity
import com.example.data.SplitTunnelMode
import com.example.network.GeoIpFetcher
import com.example.network.ProxyGeoInfo
import com.example.service.ActiveProxyInfo
import com.example.service.MyProxyVpnService
import com.example.service.TunnelTraffic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean
)

class ProxyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MyProxyApp.repository
    private val splitConfig = MyProxyApp.splitTunnelConfig

    val proxies: StateFlow<List<ProxyEntity>> = repository.allProxies
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val vpnRunning: StateFlow<Boolean> = MyProxyVpnService.vpnRunningFlow
    val activeProxy: StateFlow<ActiveProxyInfo?> = MyProxyVpnService.activeProxyFlow
    val durationSeconds: StateFlow<Long> = MyProxyVpnService.durationSecondsFlow
    val traffic: StateFlow<TunnelTraffic> = MyProxyVpnService.trafficFlow

    private val _selectedProxy = MutableStateFlow<ProxyEntity?>(null)
    val selectedProxy: StateFlow<ProxyEntity?> = _selectedProxy.asStateFlow()

    private val _geoInfo = MutableStateFlow<ProxyGeoInfo?>(null)
    val geoInfo: StateFlow<ProxyGeoInfo?> = _geoInfo.asStateFlow()

    private val _isResolvingGeo = MutableStateFlow(false)
    val isResolvingGeo: StateFlow<Boolean> = _isResolvingGeo.asStateFlow()

    private val _isPinging = MutableStateFlow(false)
    val isPinging: StateFlow<Boolean> = _isPinging.asStateFlow()

    private val _splitTunnelMode = MutableStateFlow(splitConfig.mode)
    val splitTunnelMode: StateFlow<SplitTunnelMode> = _splitTunnelMode.asStateFlow()

    private val _selectedPackages = MutableStateFlow(splitConfig.getSelectedPackages())
    val selectedPackages: StateFlow<Set<String>> = _selectedPackages.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _floatingBubbleEnabled = MutableStateFlow(splitConfig.floatingBubbleEnabled)
    val floatingBubbleEnabled: StateFlow<Boolean> = _floatingBubbleEnabled.asStateFlow()

    private val _selectedDns = MutableStateFlow(splitConfig.selectedDns)
    val selectedDns: StateFlow<String> = _selectedDns.asStateFlow()

    init {
        // Observe proxies to set default selected
        viewModelScope.launch {
            proxies.collect { list ->
                if (_selectedProxy.value == null && list.isNotEmpty()) {
                    val defaultProxy = list.firstOrNull { it.isDefault } ?: list.first()
                    _selectedProxy.value = defaultProxy
                }
            }
        }
        refreshGeoInfo()
    }

    fun selectProxy(proxy: ProxyEntity) {
        _selectedProxy.value = proxy
        viewModelScope.launch {
            repository.setDefault(proxy.id)
        }
    }

    fun addProxy(name: String, type: String, host: String, port: Int, user: String?, pass: String?) {
        viewModelScope.launch {
            val entity = ProxyEntity(
                name = name.ifBlank { "Custom ${type.uppercase()}" },
                type = type.uppercase(),
                host = host.trim(),
                port = port,
                user = user?.takeIf { it.isNotBlank() },
                pass = pass?.takeIf { it.isNotBlank() },
                isDefault = true
            )
            val newId = repository.insert(entity)
            repository.setDefault(newId)
            _selectedProxy.value = entity.copy(id = newId)
        }
    }

    fun deleteProxy(proxy: ProxyEntity) {
        viewModelScope.launch {
            repository.delete(proxy)
            if (_selectedProxy.value?.id == proxy.id) {
                _selectedProxy.value = proxies.value.firstOrNull { it.id != proxy.id }
            }
        }
    }

    fun pingSelectedProxy() {
        val proxy = _selectedProxy.value ?: return
        viewModelScope.launch {
            _isPinging.value = true
            val latency = repository.pingProxy(proxy)
            _selectedProxy.value = proxy.copy(pingMs = latency)
            _isPinging.value = false
        }
    }

    fun refreshGeoInfo() {
        viewModelScope.launch {
            _isResolvingGeo.value = true
            val targetHost = if (vpnRunning.value) activeProxy.value?.host else null
            val info = GeoIpFetcher.getDetails(targetHost)
            _geoInfo.value = info
            _isResolvingGeo.value = false
        }
    }

    fun setSplitTunnelMode(mode: SplitTunnelMode) {
        splitConfig.mode = mode
        _splitTunnelMode.value = mode
    }

    fun toggleAppPackage(packageName: String) {
        val newState = splitConfig.togglePackage(packageName)
        val current = _selectedPackages.value.toMutableSet()
        if (newState) current.add(packageName) else current.remove(packageName)
        _selectedPackages.value = current
    }

    fun selectAllApps() {
        val allPkgs = _installedApps.value.map { it.packageName }.toSet()
        splitConfig.setSelectedPackages(allPkgs)
        _selectedPackages.value = allPkgs
    }

    fun clearAllApps() {
        splitConfig.setSelectedPackages(emptySet())
        _selectedPackages.value = emptySet()
    }

    fun toggleFloatingBubble(enabled: Boolean) {
        splitConfig.floatingBubbleEnabled = enabled
        _floatingBubbleEnabled.value = enabled
    }

    fun setDnsServer(dns: String) {
        splitConfig.selectedDns = dns
        _selectedDns.value = dns
    }

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            _isLoadingApps.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                val seenPackages = mutableSetOf<String>()
                val result = mutableListOf<InstalledAppItem>()

                for (info in resolveInfos) {
                    val pkg = info.activityInfo.packageName
                    if (pkg == getApplication<Application>().packageName) continue // Skip self
                    if (!seenPackages.add(pkg)) continue

                    val label = info.loadLabel(pm).toString()
                    val icon = info.loadIcon(pm)
                    val isSystem = (info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    result.add(
                        InstalledAppItem(
                            packageName = pkg,
                            appName = label,
                            icon = icon,
                            isSystemApp = isSystem
                        )
                    )
                }
                result.sortedBy { it.appName.lowercase() }
            }
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }
}
