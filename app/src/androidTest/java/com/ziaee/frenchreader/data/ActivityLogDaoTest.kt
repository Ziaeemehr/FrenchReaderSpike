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

@RunWith(AndroidJUnit4::class)
class ActivityLogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ActivityLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.activityLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addListening_createsRowWhenNoneExists() = runBlocking {
        dao.addListening("2026-09-16", 5000L)

        assertEquals(5000L, dao.getForDate("2026-09-16")?.listeningMs)
    }

    @Test
    fun addListening_accumulatesAcrossCalls() = runBlocking {
        dao.addListening("2026-09-16", 5000L)
        dao.addListening("2026-09-16", 3000L)

        assertEquals(8000L, dao.getForDate("2026-09-16")?.listeningMs)
    }

    @Test
    fun addListening_keepsSeparateDatesIndependent() = runBlocking {
        dao.addListening("2026-09-16", 5000L)
        dao.addListening("2026-09-17", 2000L)

        assertEquals(5000L, dao.getForDate("2026-09-16")?.listeningMs)
        assertEquals(2000L, dao.getForDate("2026-09-17")?.listeningMs)
    }
    @Test
    fun activityUpserts_preserveEverySource() = runBlocking {
        val day = "2026-10-26"
        dao.addReading(day, 1000)
        dao.addWatching(day, 2000)
        dao.addListening(day, 3000)
        dao.addReading(day, 4000)
        dao.addWatching(day, 5000)
        assertEquals(ActivityLogEntry(day, 3000, 5000, 7000), dao.getForDate(day))
    }

    @Test
    fun dailyActivity_sumsAllSourcesAndKeepsLegacyRule() = runBlocking {
        val day = java.time.LocalDate.of(2026, 10, 26)
        val timestamp = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.addListening(day.toString(), 60_000)
        dao.addReading(day.toString(), 70_000)
        dao.addWatching(day.toString(), 80_000)
        db.reviewLogDao().insert(ReviewLogEntry(entryId = 1, timestampMs = timestamp, knew = true, boxBefore = 1, boxAfter = 2))
        db.shadowAttemptDao().insert(ShadowAttempt(textId = 1, chunkIndex = 0, sentenceIndex = 0,
            matched = 1, total = 1, engine = "android", timestampMs = timestamp))
        db.studyLogDao().insertSession(StudySession(date = day.toEpochDay(), durationMin = 1, skill = StudySkill.CE))
        dao.addReading(day.minusDays(1).toString(), 60_000)
        val shadowDay = day.minusDays(2)
        val shadowTimestamp = shadowDay.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        db.shadowAttemptDao().insert(ShadowAttempt(textId = 1, chunkIndex = 0, sentenceIndex = 0,
            matched = 1, total = 1, engine = "android", timestampMs = shadowTimestamp))
        val rows = dao.dailyActivity().associateBy { it.date }
        assertEquals(DailyActivityRow(day.toString(), 300_000, true), rows[day.toString()])
        assertEquals(DailyActivityRow(shadowDay.toString(), 20_000, true), rows[shadowDay.toString()])
        assertEquals(DailyActivityRow(day.minusDays(1).toString(), 60_000, false), rows[day.minusDays(1).toString()])
    }
}
