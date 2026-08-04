package com.lazyapps.wifianalyzer

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lazyapps.wifianalyzer.billing.FeatureAccessPolicy
import com.lazyapps.wifianalyzer.data.registry.SignalHistoryRepository
import com.lazyapps.wifianalyzer.data.registry.WifiAnalyzerDatabase
import com.lazyapps.wifianalyzer.model.DistanceRange
import com.lazyapps.wifianalyzer.model.SecurityType
import com.lazyapps.wifianalyzer.model.SignalQuality
import com.lazyapps.wifianalyzer.model.WifiAccessPoint
import com.lazyapps.wifianalyzer.model.WifiBand
import com.lazyapps.wifianalyzer.model.WifiStandard
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalHistoryRepositoryTest {
    private lateinit var database: WifiAnalyzerDatabase
    private lateinit var repository: SignalHistoryRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WifiAnalyzerDatabase::class.java).allowMainThreadQueries().build()
        repository = SignalHistoryRepository(database)
    }

    @After fun tearDown() = database.close()

    @Test fun duplicateWorkspaceBssidTimestampIsIgnoredAndLatestIsBounded() = runBlocking {
        val now = 2_000_000L
        val points = (0..950).map { point(it.toLong(), "aa:bb:cc:dd:ee:ff") }
        repository.insert(7L, points + points.last(), FeatureAccessPolicy(isPro = true), now)

        val rows = repository.observeLatest(7L, "aa:bb:cc:dd:ee:ff").first()
        assertEquals(SignalHistoryRepository.MAX_POINTS, rows.size)
        assertEquals(950L, rows.first().timestampMillis)
        assertEquals(51L, rows.last().timestampMillis)
    }

    @Test fun freeInsertRemovesOnlyHistoryOlderThan24Hours() = runBlocking {
        val now = 10_000_000L
        repository.insert(1L, listOf(point(now - 24L * 60 * 60 * 1000 - 1, "one"), point(now - 24L * 60 * 60 * 1000, "two")), FeatureAccessPolicy(isPro = false), now)
        assertEquals(1, repository.observe(1L, "two").first().size)
        assertTrue(repository.observe(1L, "one").first().isEmpty())
    }

    private fun point(timestamp: Long, bssid: String) = WifiAccessPoint("ssid", bssid, -55, 2412, 1, 20, "WPA2", timestamp * 1000, WifiBand.BAND_24, SignalQuality.GOOD, SecurityType.WPA2, WifiStandard.WIFI_4, DistanceRange.ONE_TO_THREE, timestamp)
}
