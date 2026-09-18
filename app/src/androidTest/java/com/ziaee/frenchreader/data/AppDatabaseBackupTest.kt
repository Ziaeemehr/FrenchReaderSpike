package com.ziaee.frenchreader.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile get() = context.getDatabasePath("checkpoint_test.db")
    private val snapshotFile get() = context.getDatabasePath("checkpoint_test_snapshot.db")

    @After
    fun tearDown() {
        context.deleteDatabase("checkpoint_test.db")
        snapshotFile.delete()
    }

    // What actually matters for backup: reading only the main .db file's bytes
    // (never -wal/-shm, since that's exactly what the real upload path does) right
    // after checkpointWal -- while the live connection stays open, matching the real
    // backup flow, which never closes the db mid-backup -- must be a complete, valid
    // snapshot on its own. Checked via raw SQLiteDatabase (not Room) so a Room-level
    // quirk on reopening a copied file can't hide or fake this result.
    @Test
    fun checkpointWalMakesTheMainDbFileACompleteSnapshotOnItsOwn() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "checkpoint_test.db")
            .addMigrations(*ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()
        db.vocabListDao().insert(VocabList(name = "test-list"))

        AppDatabase.checkpointWal(db)
        dbFile.copyTo(snapshotFile, overwrite = true)
        db.close()

        val rawDb = SQLiteDatabase.openDatabase(snapshotFile.path, null, SQLiteDatabase.OPEN_READONLY)
        val cursor = rawDb.rawQuery("SELECT name FROM vocab_lists WHERE name = ?", arrayOf("test-list"))
        val found = cursor.moveToFirst()
        cursor.close()
        rawDb.close()

        assertTrue("expected the inserted list to be present in a snapshot copy of just the main .db file", found)
    }
}
