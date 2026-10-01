package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

data class ProxyGeoInfo(
    val flag: String,
    val countryCode: String,
    val country: String,
    val city: String,
    val ip: String,
    val isp: String = "Secured Node"
)

object GeoIpFetcher {

    fun countryCodeToEmoji(countryCode: String?): String {
        if (countryCode.isNullOrEmpty() || countryCode.length != 2) return "🌐"
        val code = countryCode.uppercase(Locale.US)
        val firstChar = Character.codePointAt(code, 0) - 0x41 + 0x1F1E6
        val secondChar = Character.codePointAt(code, 1) - 0x41 + 0x1F1E6
        return if (firstChar in 0x1F1E6..0x1F1FF && secondChar in 0x1F1E6..0x1F1FF) {
            String(Character.toChars(firstChar)) + String(Character.toChars(secondChar))
        } else {
            "🌐"
        }
    }

    fun getCountryName(countryCode: String?): String {
        if (countryCode.isNullOrBlank() || countryCode.length != 2) return "Global"
        return runCatching {
            val loc = Locale("", countryCode.uppercase(Locale.US))
            val name = loc.displayCountry
            if (name.isNotBlank()) name else "Region $countryCode"
        }.getOrDefault("Global")
    }

    /**
     * Extracts country short alpha code from proxy username (e.g. user-country-us, user_country_gb, bd-proxy, etc.)
     */
    fun extractCountryCodeFromUsername(username: String?): String? {
        if (username.isNullOrBlank()) return null
        val u = username.lowercase(Locale.US)

        // Pattern 1: -country-xx or _country_xx or :country:xx or -cc-xx
        val countryRegex = Regex("""(?:country|cc|geo|zone|region)[-_:=]([a-z]{2})\b""")
        val match = countryRegex.find(u)
        if (match != null) {
            return match.groupValues[1].uppercase(Locale.US)
        }

        // Pattern 2: trailing -xx or _xx
        val trailingRegex = Regex("""[-_:=]([a-z]{2})$""")
        val trailingMatch = trailingRegex.find(u)
        if (trailingMatch != null) {
            return trailingMatch.groupValues[1].uppercase(Locale.US)
        }

        // Pattern 3: leading xx- or xx_
        val leadingRegex = Regex("""^([a-z]{2})[-_:=]""")
        val leadingMatch = leadingRegex.find(u)
        if (leadingMatch != null) {
            return leadingMatch.groupValues[1].uppercase(Locale.US)
        }

        // Pattern 4: exact 2-letter country code as username
        val trimmed = username.trim()
        if (trimmed.length == 2 && trimmed.all { it.isLetter() }) {
            return trimmed.uppercase(Locale.US)
        }

        return null
    }

    suspend fun getDetails(customHost: String? = null, usernameHint: String? = null): ProxyGeoInfo = withContext(Dispatchers.IO) {
        val hintCode = extractCountryCodeFromUsername(usernameHint)

        val targetUrl = if (!customHost.isNullOrBlank() && customHost != "127.0.0.1") {
            "https://ipwho.is/$customHost"
        } else {
            "https://ipwho.is/"
        }

        try {
            val url = java.net.URL(targetUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MyProxy-Android/1.0")
            }
            if (conn.responseCode in 200..299) {
                val content = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(content)
                if (json.optBoolean("success", true)) {
                    val ip = json.optString("ip", customHost ?: "0.0.0.0")
                    var code = json.optString("country_code", "")
                    var country = json.optString("country", "Global")
                    val city = json.optString("city", "Proxy Node")
                    val connectionObj = json.optJSONObject("connection")
                    val isp = connectionObj?.optString("isp", "Proxy Backbone") ?: "Proxy Backbone"

                    // If username has country alpha code specified, honor username's target country!
                    if (hintCode != null) {
                        code = hintCode
                        country = getCountryName(hintCode)
                    }

                    return@withContext ProxyGeoInfo(
                        flag = countryCodeToEmoji(code),
                        countryCode = code.ifEmpty { "--" },
                        country = country,
                        city = city,
                        ip = ip,
                        isp = isp
                    )
                }
            }
        } catch (_: Exception) {
            // Network fallback
        }

        val effectiveCode = hintCode ?: (if (customHost != null && customHost.length >= 2) "US" else "--")
        ProxyGeoInfo(
            flag = countryCodeToEmoji(effectiveCode),
            countryCode = effectiveCode,
            country = getCountryName(effectiveCode),
            city = if (hintCode != null) "${getCountryName(hintCode)} Tunnel" else "Target Proxy",
            ip = customHost ?: "Tunnel Active",
            isp = "Direct Proxy Route"
        )
    }

    suspend fun testPing(host: String, port: Int, timeoutMs: Int = 2500): Long = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            return@withContext (System.currentTimeMillis() - startTime)
        } catch (_: Exception) {
            return@withContext -1L
        }
    }
}
