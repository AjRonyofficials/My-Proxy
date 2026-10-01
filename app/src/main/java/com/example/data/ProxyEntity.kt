package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String = "SOCKS5",
    val host: String,
    val port: Int,
    val user: String? = null,
    val pass: String? = null,
    val isDefault: Boolean = false,
    val pingMs: Long = -1,
    val countryCode: String = "",
    val city: String = ""
)
