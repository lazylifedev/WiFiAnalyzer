package com.lazyapps.wifianalyzer.ui.photos

/** Pure viewer state transitions, kept independent from Window APIs for JVM coverage. */
internal data class PhotoViewerFullscreenState(
    val immersive: Boolean = false,
    val chromeVisible: Boolean = true,
) {
    val applySystemBarInsets: Boolean get() = !immersive
    fun toggleFullscreen(): PhotoViewerFullscreenState = copy(
        immersive = !immersive,
        chromeVisible = immersive,
    )

    fun toggleChrome(): PhotoViewerFullscreenState =
        if (immersive) copy(chromeVisible = !chromeVisible) else this

    fun close(): PhotoViewerFullscreenState = PhotoViewerFullscreenState()
}
