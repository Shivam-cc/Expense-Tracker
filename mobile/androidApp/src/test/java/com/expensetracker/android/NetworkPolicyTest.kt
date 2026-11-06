package com.expensetracker.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NetworkPolicyTest {
    @Test
    fun endpointMatchesTheBuildTypeAndOnlyTheEmulatorHostAllowsCleartext() {
        if (BuildConfig.DEBUG) {
            assertEquals("http://10.0.2.2:8080", BuildConfig.API_BASE_URL)
        } else {
            assertEquals("https", java.net.URI(BuildConfig.API_BASE_URL).scheme)
        }
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/debug/res/xml/network_security_config.xml"))
        assertEquals("false", xml.getElementsByTagName("base-config").item(0).attributes.getNamedItem("cleartextTrafficPermitted").nodeValue)
        val domains = xml.getElementsByTagName("domain")
        assertEquals(1, domains.length)
        assertEquals("10.0.2.2", domains.item(0).textContent.trim())
        assertEquals("false", domains.item(0).attributes.getNamedItem("includeSubdomains").nodeValue)
    }

    @Test
    fun releaseDoesNotInheritTheDebugNetworkException() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertFalse(manifest.contains("networkSecurityConfig"))
        assertFalse(File("src/main/res/xml/network_security_config.xml").exists())
        assertFalse(File("src/release/res/xml/network_security_config.xml").exists())
    }
}
