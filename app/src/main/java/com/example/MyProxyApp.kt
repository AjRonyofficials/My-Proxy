package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.ProxyRepository
import com.example.data.SplitTunnelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyProxyApp : Application() {

    companion object {
        lateinit var database: AppDatabase
            private set
        lateinit var repository: ProxyRepository
            private set
        lateinit var splitTunnelConfig: SplitTunnelConfig
            private set
        const val CHANNEL_ID = "MY_PROXY_CHANNEL"
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        repository = ProxyRepository(database.proxyDao())
        splitTunnelConfig = SplitTunnelConfig(this)

        createNotificationChannel()

        CoroutineScope(Dispatchers.IO).launch {
            repository.cleanupDummyProxies()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "My Proxy Connection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live connection status and tunnel stats"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
