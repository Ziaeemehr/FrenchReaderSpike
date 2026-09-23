package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ShadowAttemptDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ShadowAttemptDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        dao = db.shadowAttemptDao()
    }

    @After fun tearDown() { db.close() }

    private fun ms(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).plusHours(12).toInstant().toEpochMilli()
    private fun attempt(ts: Long, textId: Long = 1) = ShadowAttempt(textId = textId, chunkIndex = 0, sentenceIndex = 0, matched = 3, total = 4, paceRatio = 1.1f, engine = "vosk", timestampMs = ts)

    @Test fun getSince_filtersByTimestamp_andDistinctDates() = runBlocking {
        val today = LocalDate.now()
        dao.insert(attempt(ms(today.minusDays(10))))
        dao.insert(attempt(ms(today)))
        dao.insert(attempt(ms(today)))
        assertEquals(2, dao.getSince(ms(today.minusDays(6))).size)
        assertEquals(3, dao.countAll())
        assertEquals(setOf(today.toString(), today.minusDays(10).toString()), dao.distinctActiveDates().toSet())
    }

    @Test fun getForTextSince_filtersByTextAndTime() = runBlocking {
        dao.insert(attempt(1_000, textId = 1))
        dao.insert(attempt(5_000, textId = 1))
        dao.insert(attempt(5_000, textId = 2))
        val rows = dao.getForTextSince(textId = 1, sinceMs = 2_000)
        assertEquals(1, rows.size)
        assertEquals(1.1f, rows[0].paceRatio!!, 0.001f)
    }
}
