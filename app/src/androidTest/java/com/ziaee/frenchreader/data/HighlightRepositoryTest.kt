package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HighlightRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: HighlightRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = HighlightRepository(db.highlightDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addUpdateDeleteAndQueryByTextIdRoundTripsHighlight() = runBlocking {
        repository.addHighlight(textId = 1, startOffset = 2, endOffset = 8, colorKey = "yellow")
        repository.addHighlight(textId = 2, startOffset = 0, endOffset = 4, colorKey = "pink")

        val inserted = repository.observeHighlights(1).first().single()
        assertEquals(2, inserted.startOffset)
        assertEquals(8, inserted.endOffset)
        assertEquals("yellow", inserted.colorKey)

        repository.updateColor(inserted.id, "blue")
        assertEquals("blue", repository.observeHighlights(1).first().single().colorKey)
        assertEquals(1, repository.observeHighlights(2).first().size)

        repository.delete(inserted.id)
        assertEquals(emptyList<HighlightEntry>(), repository.observeHighlights(1).first())
    }
}
