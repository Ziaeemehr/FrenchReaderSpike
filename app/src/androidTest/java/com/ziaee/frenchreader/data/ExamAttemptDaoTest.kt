package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamAttemptDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ExamAttemptDao
    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        dao = db.examAttemptDao()
    }
    @After fun tearDown() { db.close() }
    private fun attempt(question: String, at: Long, exam: String = "tcf") = ExamAttempt(0, exam, question, "Original French subject", "", null, "", at, at)

    @Test fun insertUpdateDeleteAndDistinctProgress() = runBlocking {
        val later = dao.insert(attempt("a", 20))
        val earlier = dao.insert(attempt("a", 10))
        dao.insert(attempt("b", 30))
        dao.insert(attempt("a", 5, "other"))
        assertEquals(listOf(earlier, later), dao.observeForQuestion("tcf", "a").first().map { it.id })
        assertEquals(3, dao.observeForExam("tcf").first().size)
        assertEquals(2, dao.countPractisedQuestions("tcf").first())
        val row = dao.observeForQuestion("tcf", "a").first().first()
        dao.update(row.copy(answer = "Ma réponse", score = 14.5, note = "Note", updatedAtMs = 40))
        val updated = dao.observeForQuestion("tcf", "a").first().first()
        assertEquals(10L, updated.createdAtMs)
        assertEquals("Original French subject", updated.questionFr)
        assertEquals(14.5, updated.score!!, 0.0)
        dao.deleteById(earlier)
        assertEquals(2, dao.countPractisedQuestions("tcf").first())
        dao.deleteById(later)
        assertEquals(1, dao.countPractisedQuestions("tcf").first())
        assertTrue(dao.observeForQuestion("tcf", "a").first().isEmpty())
    }
}
