package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.network.GeoIpFetcher
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("My Proxy", appName)
  }

  @Test
  fun `test country code to flag emoji`() {
    val flagUs = GeoIpFetcher.countryCodeToEmoji("US")
    assertEquals("🇺🇸", flagUs)

    val flagSg = GeoIpFetcher.countryCodeToEmoji("SG")
    assertEquals("🇸🇬", flagSg)

    val flagBd = GeoIpFetcher.countryCodeToEmoji("BD")
    assertEquals("🇧🇩", flagBd)
  }

  @Test
  fun `test extract country code from username`() {
    assertEquals("US", GeoIpFetcher.extractCountryCodeFromUsername("customer-user-country-us"))
    assertEquals("GB", GeoIpFetcher.extractCountryCodeFromUsername("proxyuser_country_gb"))
    assertEquals("BD", GeoIpFetcher.extractCountryCodeFromUsername("myuser-country-bd"))
    assertEquals("SG", GeoIpFetcher.extractCountryCodeFromUsername("sg-premium-proxy"))
    assertEquals("DE", GeoIpFetcher.extractCountryCodeFromUsername("zone-resi-de"))
    assertEquals("CA", GeoIpFetcher.extractCountryCodeFromUsername("CA"))
  }
}
