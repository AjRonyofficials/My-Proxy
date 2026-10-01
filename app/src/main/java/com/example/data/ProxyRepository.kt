package com.example.data

import com.example.network.GeoIpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProxyRepository(private val proxyDao: ProxyDao) {

    val allProxies: Flow<List<ProxyEntity>> = proxyDao.getAllProxies()

    suspend fun getDefaultProxy(): ProxyEntity? = proxyDao.getDefaultProxy()

    suspend fun insert(proxy: ProxyEntity): Long {
        // Automatically extract country short alpha code from username if present
        val detectedCountry = GeoIpFetcher.extractCountryCodeFromUsername(proxy.user)
        val entity = if (!detectedCountry.isNullOrBlank()) {
            proxy.copy(
                countryCode = detectedCountry,
                city = GeoIpFetcher.getCountryName(detectedCountry)
            )
        } else {
            proxy
        }
        return proxyDao.insertProxy(entity)
    }

    suspend fun update(proxy: ProxyEntity) {
        val detectedCountry = GeoIpFetcher.extractCountryCodeFromUsername(proxy.user)
        val entity = if (!detectedCountry.isNullOrBlank()) {
            proxy.copy(
                countryCode = detectedCountry,
                city = GeoIpFetcher.getCountryName(detectedCountry)
            )
        } else {
            proxy
        }
        proxyDao.updateProxy(entity)
    }

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

    suspend fun cleanupDummyProxies() = withContext(Dispatchers.IO) {
        val dummyHosts = listOf("154.16.2.1", "139.180.208.52", "188.165.23.41", "45.76.102.88", "51.89.24.110")
        proxyDao.deleteByHosts(dummyHosts)
    }
}
