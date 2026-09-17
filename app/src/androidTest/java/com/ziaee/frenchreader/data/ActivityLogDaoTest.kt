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
}
