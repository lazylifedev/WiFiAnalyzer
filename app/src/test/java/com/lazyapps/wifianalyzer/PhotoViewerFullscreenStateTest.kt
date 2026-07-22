package com.lazyapps.wifianalyzer

import com.lazyapps.wifianalyzer.ui.photos.PhotoViewerFullscreenState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoViewerFullscreenStateTest {
    @Test fun fullscreenToggleChangesInsetsChromeAndAction() {
        val normal = PhotoViewerFullscreenState()
        assertTrue(normal.applySystemBarInsets)
        assertEquals("全画面", normal.fullscreenActionDescription)

        val immersive = normal.toggleFullscreen()
        assertTrue(immersive.immersive)
        assertFalse(immersive.applySystemBarInsets)
        assertFalse(immersive.chromeVisible)
        assertEquals("全画面を終了", immersive.fullscreenActionDescription)

        assertEquals(normal, immersive.toggleFullscreen())
    }

    @Test fun photoTapOnlyTogglesChromeInFullscreen() {
        assertEquals(PhotoViewerFullscreenState(), PhotoViewerFullscreenState().toggleChrome())
        val immersive = PhotoViewerFullscreenState().toggleFullscreen()
        assertTrue(immersive.toggleChrome().chromeVisible)
        assertFalse(immersive.toggleChrome().toggleChrome().chromeVisible)
    }

    @Test fun closeAlwaysRestoresNormalSystemBarState() {
        val restored = PhotoViewerFullscreenState(true, true).close()
        assertFalse(restored.immersive)
        assertTrue(restored.chromeVisible)
        assertTrue(restored.applySystemBarInsets)
    }

    @Test fun repeatedTogglesRemainStable() {
        var state = PhotoViewerFullscreenState()
        repeat(20) { state = state.toggleFullscreen() }
        assertEquals(PhotoViewerFullscreenState(), state)
    }
}
