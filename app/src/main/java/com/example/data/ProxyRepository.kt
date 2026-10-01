package com.example.data

import com.example.network.GeoIpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProxyRepository(private val proxyDao: ProxyDao) {

    val allProxies: Flow<List<ProxyEntity>> = proxyDao.getAllProxies()

    suspend fun getDefaultProxy(): ProxyEntity? = proxyDao.getDefaultProxy()

    suspend fun insert(proxy: ProxyEntity): Long = proxyDao.insertProxy(proxy)

    suspend fun update(proxy: ProxyEntity) = proxyDao.updateProxy(proxy)

    suspend fun delete(proxy: ProxyEntity) = proxyDao.deleteProxy(proxy)

    suspend fun deleteById(id: Long) = proxyDao.deleteById(id)

    suspend fun setDefault(id: Long) {
        proxyDao.clearDefault()
        proxyDao.setDefault(id)
    }

    suspend fun pingProxy(proxy: ProxyEntity): Long = withContext(Dispatchers.IO) {
        val latency = GeoIpFetcher.testPing(proxy.host, proxy.port)
        if (latency >= 0) {
            proxyDao.updatePing(proxy.id, latency)
        }
        latency
    }

    suspend fun preseedIfEmpty() = withContext(Dispatchers.IO) {
        if (proxyDao.count() == 0) {
            val presets = listOf(
                ProxyEntity(
                    name = "US Fast East",
                    type = "SOCKS5",
                    host = "154.16.2.1",
                    port = 1080,
                    countryCode = "US",
                    city = "New York",
                    isDefault = true,
                    pingMs = 42
                ),
                ProxyEntity(
                    name = "SG Lightning",
                    type = "SOCKS5",
                    host = "139.180.208.52",
                    port = 1080,
                    countryCode = "SG",
                    city = "Singapore",
                    isDefault = false,
                    pingMs = 28
                ),
                ProxyEntity(
                    name = "DE Frankfurt Central",
                    type = "HTTP",
                    host = "188.165.23.41",
                    port = 8080,
                    countryCode = "DE",
                    city = "Frankfurt",
                    isDefault = false,
                    pingMs = 54
                ),
                ProxyEntity(
                    name = "JP Tokyo Ultra",
                    type = "SOCKS5",
                    host = "45.76.102.88",
                    port = 1080,
                    countryCode = "JP",
                    city = "Tokyo",
                    isDefault = false,
                    pingMs = 62
                ),
                ProxyEntity(
                    name = "UK London Gateway",
                    type = "SOCKS5",
                    host = "51.89.24.110",
                    port = 1080,
                    countryCode = "GB",
                    city = "London",
                    isDefault = false,
                    pingMs = 48
                )
            )
            presets.forEach { proxyDao.insertProxy(it) }
        }
    }
}
