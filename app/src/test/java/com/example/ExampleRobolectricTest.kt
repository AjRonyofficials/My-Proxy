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
  }
}
