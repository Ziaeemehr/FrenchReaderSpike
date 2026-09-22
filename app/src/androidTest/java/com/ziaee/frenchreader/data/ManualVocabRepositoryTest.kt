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
class ManualVocabRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: VocabRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = VocabRepository(db.vocabDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun manualSaveDoesNotOverwriteAnkiAndDeduplicatesIgnoringCase() = runBlocking {
        db.vocabDao().insert(
            VocabEntry(
                word = "École",
                sentence = "",
                textId = 0L,
                dictionaryUrl = "anki",
                meaning = "Anki meaning"
            )
        )

        repository.save(
            textId = MANUAL_VOCAB_TEXT_ID,
            word = "école",
            sentence = "",
            dictionaryUrl = "dictionary",
            meaning = "manual meaning",
            listId = null
        )
        repository.save(
            textId = MANUAL_VOCAB_TEXT_ID,
            word = "e\u0301cole",
            sentence = "",
            dictionaryUrl = "dictionary",
            meaning = "updated manual meaning",
            listId = null
        )

        val entries = db.vocabDao().getAllOnce()
        assertEquals(2, entries.size)
        assertEquals("Anki meaning", entries.single { it.textId == 0L }.meaning)
        assertEquals("updated manual meaning", entries.single { it.textId == MANUAL_VOCAB_TEXT_ID }.meaning)
    }
}
