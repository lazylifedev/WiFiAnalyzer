package com.lazyapps.wifianalyzer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.lazyapps.wifianalyzer.ui.photos.PhotoPager
import org.junit.Rule
import org.junit.Test

class PhotoPagerTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun onePhotoDoesNotMove() {
        setPager(listOf(1L))
        swipeLeft()
        assertPage(0)
        swipeRight()
        assertPage(0)
    }

    @Test fun twoPhotosSwipeLeftThenRight() {
        setPager(listOf(1L, 2L))
        swipeLeft()
        assertPage(1)
        swipeRight()
        assertPage(0)
    }

    @Test fun ninePhotosSwipeContinuouslyAndStopAtBoundaries() {
        setPager((1L..9L).toList())
        repeat(10) { swipeLeft() }
        assertPage(8)
        repeat(10) { swipeRight() }
        assertPage(0)
    }

    @Test fun initialPageMatchesThumbnail() {
        setPager((1L..9L).toList(), initialPage = 4)
        assertPage(4)
    }

    @Test fun zoomDisablesPagerAndReturningToOneEnablesIt() {
        var zoomed by mutableStateOf(false)
        setPager(listOf(1L, 2L), zoomed = { zoomed })
        swipeLeft()
        assertPage(1)
        composeRule.runOnUiThread { zoomed = true }
        swipeRight()
        assertPage(1)
        composeRule.runOnUiThread { zoomed = false }
        swipeRight()
        assertPage(0)
    }

    @Test fun deletionClampsPageAndReorderRetainsPhoto() {
        var ids by mutableStateOf(listOf(1L, 2L, 3L))
        setPager(ids = { ids }, initialPage = 2)
        assertPage(2)
        composeRule.runOnUiThread { ids = listOf(1L, 2L) }
        assertPage(1)
        composeRule.runOnUiThread { ids = listOf(2L, 1L) }
        assertPage(0)
    }

    @Test fun recreationRestoresCurrentPage() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            var page by rememberSaveable { mutableStateOf(0) }
            Box(Modifier.fillMaxSize()) {
                PhotoPager(listOf(1L, 2L, 3L), 0, false, onPageChanged = { page = it }) { index ->
                    Text("photo $index", Modifier.fillMaxSize())
                }
                Text(page.toString(), Modifier.testTag("current_page"))
            }
        }
        swipeLeft()
        assertPage(1)
        restorationTester.emulateSavedInstanceStateRestore()
        assertPage(1)
    }

    private fun setPager(ids: List<Long>, initialPage: Int = 0, zoomed: () -> Boolean = { false }) =
        setPager({ ids }, initialPage, zoomed)

    private fun setPager(ids: () -> List<Long>, initialPage: Int = 0, zoomed: () -> Boolean = { false }) {
        composeRule.setContent {
            var page by rememberSaveable { mutableStateOf(initialPage) }
            Box(Modifier.fillMaxSize()) {
                PhotoPager(ids(), initialPage, zoomed(), onPageChanged = { page = it }) { index ->
                    Text("photo ${ids()[index]}", Modifier.fillMaxSize())
                }
                Text(page.toString(), Modifier.testTag("current_page"))
            }
        }
        composeRule.waitForIdle()
    }

    private fun swipeLeft() {
        composeRule.onNodeWithTag("photo_viewer_pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
    }

    private fun swipeRight() {
        composeRule.onNodeWithTag("photo_viewer_pager").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
    }

    private fun assertPage(expected: Int) {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("current_page").assertTextEquals(expected.toString())
            }.isSuccess
        }
        composeRule.onNodeWithTag("current_page").assertTextEquals(expected.toString())
    }
}
