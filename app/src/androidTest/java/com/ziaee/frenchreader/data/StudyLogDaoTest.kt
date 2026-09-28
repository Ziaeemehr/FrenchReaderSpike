package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyLogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: StudyLogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).build()
        dao = db.studyLogDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun rangesCountsTotalsAndExclusionAreCorrect() = runBlocking {
        val source = dao.insertSource(
            StudySource(
                name = "Book",
                archived = true
            )
        )
        val unused = dao.insertSource(StudySource(name = "Notes"))
        val first = dao.insertSession(
            StudySession(
                date = 10,
                durationMin = 30,
                skill = StudySkill.CO,
                sourceId = source
            )
        )
        dao.insertSession(
            StudySession(
                date = 11,
                durationMin = 20,
                skill = StudySkill.CE,
                sourceId = source
            )
        )
        dao.insertSession(
            StudySession(
                date = 12,
                durationMin = 40,
                skill = StudySkill.EE
            )
        )

        assertEquals(
            listOf(11L, 10L),
            dao.observeSessions(10, 11).first().map { it.date }
        )
        assertTrue(dao.observeSources().first().first { it.id == source }.archived)
        assertEquals(2, dao.countSessionsForSource(source))
        assertEquals(0, dao.sumMinutesForDateExcluding(10, first))

        dao.deleteSource(dao.getSource(unused)!!)
        assertEquals(null, dao.getSource(unused))
        assertTrue(runCatching { dao.deleteSource(dao.getSource(source)!!) }.isFailure)
    }
}
