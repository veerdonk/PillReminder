package nl.veerdonk.pillreminder.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsNavigationTest {

    private fun maps(
        key: String = "k1",
        ongoing: Boolean = true,
        category: String? = MapsNavigation.CATEGORY_NAVIGATION,
        channelId: String? = null,
        packageName: String = MapsNavigation.MAPS_PACKAGE,
    ) = NotificationFacts(key, packageName, id = 1, ongoing = ongoing, category = category, channelId = channelId)

    @Test
    fun `an ongoing Maps notification in the navigation category is navigation`() {
        assertTrue(MapsNavigation.isNavigation(maps()))
    }

    @Test
    fun `a navigation channel counts when the category is missing`() {
        assertTrue(MapsNavigation.isNavigation(maps(category = null, channelId = "Navigation_Notifications")))
    }

    @Test
    fun `other ongoing Maps notifications are not navigation`() {
        assertFalse(MapsNavigation.isNavigation(maps(category = "service", channelId = "location_sharing")))
    }

    @Test
    fun `a dismissible Maps notification is not navigation`() {
        assertFalse(MapsNavigation.isNavigation(maps(ongoing = false)))
    }

    @Test
    fun `another app's navigation notification is not Maps navigation`() {
        assertFalse(MapsNavigation.isNavigation(maps(packageName = "com.waze")))
    }

    @Test
    fun `navigating needs a connected listener and an active navigation notification`() {
        val connected = NavigationStatus().connected(listOf(maps()))
        assertTrue(connected.navigating)
        assertFalse(NavigationStatus(navigationKeys = setOf("k1")).navigating)
        assertFalse(connected.disconnected().navigating)
    }

    @Test
    fun `removing or replacing the navigation notification ends navigation`() {
        val status = NavigationStatus().connected(emptyList()).posted(maps())
        assertTrue(status.navigating)
        assertFalse(status.removed("k1").navigating)
        // Maps re-posts the same key without the navigation category: no longer navigating.
        assertFalse(status.posted(maps(category = "service")).navigating)
    }

    @Test
    fun `ongoing Maps notifications are listed for the status line`() {
        val status = NavigationStatus().connected(listOf(maps(channelId = "nav")))
        assertEquals("listener: connected\nnavigating: yes\nmaps: id=1 channel=nav category=navigation", status.describe())
    }

    @Test
    fun `the detector reports only changes in navigating`() {
        var changes = 0
        val detector = AndroidNavigationDetector { changes++ }
        detector.update { it.connected(emptyList()) }
        assertEquals(0, changes)
        detector.update { it.posted(maps()) }
        detector.update { it.posted(maps()) } // Maps updates the same notification often
        assertEquals(1, changes)
        assertTrue(detector.isNavigating())
        detector.update { it.removed("k1") }
        assertEquals(2, changes)
        assertFalse(detector.isNavigating())
    }
}
