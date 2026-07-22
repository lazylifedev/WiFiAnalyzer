package com.lazyapps.wifianalyzer.ui.photos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.collectLatest

internal fun isPhotoZoomed(scale: Float): Boolean = scale > 1.01f

@Composable
internal fun PhotoPager(
    photoIds: List<Long>,
    initialPage: Int,
    currentPageZoomed: Boolean,
    modifier: Modifier = Modifier,
    onPageChanged: (Int) -> Unit,
    pageContent: @Composable (Int) -> Unit,
) {
    if (photoIds.isEmpty()) return
    val safeInitialPage = initialPage.coerceIn(0, photoIds.lastIndex)
    var currentPhotoId by rememberSaveable { mutableLongStateOf(photoIds[safeInitialPage]) }
    val pagerState = rememberPagerState(safeInitialPage) { photoIds.size }
    val latestPhotoIds by rememberUpdatedState(photoIds)
    val latestOnPageChanged by rememberUpdatedState(onPageChanged)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            latestPhotoIds.getOrNull(page)?.let { currentPhotoId = it }
            latestOnPageChanged(page)
        }
    }
    LaunchedEffect(photoIds) {
        val retainedPage = photoIds.indexOf(currentPhotoId)
        val safePage = if (retainedPage >= 0) retainedPage else pagerState.currentPage.coerceAtMost(photoIds.lastIndex)
        if (pagerState.currentPage != safePage) pagerState.scrollToPage(safePage)
        currentPhotoId = photoIds[safePage]
        onPageChanged(safePage)
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize().testTag("photo_viewer_pager"),
        userScrollEnabled = !currentPageZoomed,
        key = { photoIds[it] },
    ) { page -> pageContent(page) }
}
