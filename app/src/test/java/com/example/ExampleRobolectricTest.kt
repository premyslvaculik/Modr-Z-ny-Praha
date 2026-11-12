package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.MapHtmlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Modré Zóny Praha", appName)
  }

  @Test
  fun `verify MapHtmlBuilder builds HTML without regex errors and includes leaflet`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val html = MapHtmlBuilder.buildHtml(
      context = context,
      userLat = 50.1319683,
      userLng = 14.4874056,
      activeLayer = "google_roadmap",
      zonesJson = """[{"id":"PRG_8_3","sectorCode":"8.3","isSubscribed":true,"polygon":[[50.12,14.48],[50.13,14.49]]}]"""
    )
    assertTrue("HTML should not be empty", html.isNotEmpty())
    assertTrue("HTML should contain INITIAL_CONFIG", html.contains("INITIAL_CONFIG"))
    assertTrue("HTML should contain PRG_8_3 zone", html.contains("PRG_8_3"))
    assertTrue("HTML should contain inlined Leaflet CSS", html.contains(".leaflet-container"))
    assertTrue("HTML should contain L.map initialization code", html.contains("L.map"))
    assertTrue("HTML should define vectorZonesPane", html.contains("vectorZonesPane"))
    assertTrue("HTML should configure tile definitions", html.contains("google_roadmap") && html.contains("osm"))
  }
}
