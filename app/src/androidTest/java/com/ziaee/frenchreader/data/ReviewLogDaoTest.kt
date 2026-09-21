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
class ReviewLogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ReviewLogDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.reviewLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun epochMsAt(date: LocalDate, hour: Long = 12): Long =
        date.atStartOfDay(ZoneId.systemDefault()).plusHours(hour).toInstant().toEpochMilli()

    @Test
    fun distinctActiveDates_returnsOneStringPerDistinctLocalDay() = runBlocking {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        dao.insert(ReviewLogEntry(entryId = 1, timestampMs = epochMsAt(today), knew = true, boxBefore = 1, boxAfter = 2))
        dao.insert(ReviewLogEntry(entryId = 2, timestampMs = epochMsAt(today), knew = false, boxBefore = 2, boxAfter = 1))
        dao.insert(ReviewLogEntry(entryId = 3, timestampMs = epochMsAt(yesterday), knew = true, boxBefore = 1, boxAfter = 2))

        val activeDates = dao.distinctActiveDates()

        assertEquals(setOf(today.toString(), yesterday.toString()), activeDates.toSet())
        // Every returned string must be parseable back into the LocalDate it represents --
        // this is the seam StatisticsViewModel.load() depends on.
        activeDates.forEach { LocalDate.parse(it) }
    }

    @Test
    fun countSince_onlyCountsRowsAtOrAfterTheBoundary() = runBlocking {
        val today = LocalDate.now()
        val startOfToday = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.insert(ReviewLogEntry(entryId = 1, timestampMs = epochMsAt(today.minusDays(1)), knew = true, boxBefore = 1, boxAfter = 2))
        dao.insert(ReviewLogEntry(entryId = 2, timestampMs = epochMsAt(today), knew = true, boxBefore = 1, boxAfter = 2))
        dao.insert(ReviewLogEntry(entryId = 3, timestampMs = epochMsAt(today), knew = false, boxBefore = 2, boxAfter = 1))

        assertEquals(2, dao.countSince(startOfToday))
    }

    @Test
    fun countKnew_and_countTotal_reflectAMixedSet() = runBlocking {
        val today = LocalDate.now()
        dao.insert(ReviewLogEntry(entryId = 1, timestampMs = epochMsAt(today), knew = true, boxBefore = 1, boxAfter = 2))
        dao.insert(ReviewLogEntry(entryId = 2, timestampMs = epochMsAt(today), knew = true, boxBefore = 2, boxAfter = 3))
        dao.insert(ReviewLogEntry(entryId = 3, timestampMs = epochMsAt(today), knew = false, boxBefore = 3, boxAfter = 1))

        assertEquals(2, dao.countKnew())
        assertEquals(3, dao.countTotal())
    }

    @Test
    fun deleteById_removesOnlyTheInsertedRow() = runBlocking {
        val today = LocalDate.now()
        val id = dao.insert(ReviewLogEntry(entryId = 1, timestampMs = epochMsAt(today), knew = true, boxBefore = 1, boxAfter = 2))
        dao.insert(ReviewLogEntry(entryId = 2, timestampMs = epochMsAt(today), knew = true, boxBefore = 1, boxAfter = 2))
        dao.deleteById(id)
        assertEquals(1, dao.countTotal())
    }
}
