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
class ResourceDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: ResourceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.resourceDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertUpdateDelete_roundTripsResource() = runBlocking {
        val id = dao.insert(ResourceLink(title = "Fabulang", url = "https://fabulang.com"))
        val inserted = dao.observeAll().first().single()
        assertEquals(id, inserted.id)

        dao.update(inserted.copy(title = "French stories"))
        assertEquals("French stories", dao.observeAll().first().single().title)

        dao.delete(inserted.copy(title = "French stories"))
        assertEquals(emptyList<ResourceLink>(), dao.observeAll().first())
    }
}
