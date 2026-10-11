package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextLevelDaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java
    ).build()

    @After fun tearDown() { db.close() }

    @Test fun setsAndClearsAcrossIdChunksWithoutChangingOtherTexts() = runBlocking {
        val dao = db.textDao()
        val ids = (0..SQL_ID_CHUNK).map { dao.insert(TextDocument(title = "Story $it", rawText = "Text")) }
        val other = dao.insert(TextDocument(title = "Other", rawText = "Text", level = "C1"))
        dao.setLevel(ids, "B2")
        assertEquals(ids.size, dao.getAllOnce().count { it.level == "B2" })
        assertEquals("C1", dao.getById(other)!!.level)
        dao.setLevel(ids, null)
        assertEquals(ids.size, dao.getAllOnce().count { it.level == null })
        assertEquals("C1", dao.getById(other)!!.level)
        dao.setLevel(emptyList(), "A1")
        assertEquals(ids.size + 1, dao.getAllOnce().size)
    }
}
