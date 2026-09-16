package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// v2 -> v3: adds multi-list support (vocab_lists table + vocab.listId) and
// the Leitner spaced-repetition fields on vocab. Written as a real
// migration (unlike the v1->v2 bump) so existing texts/vocab survive this
// update -- existing entries just start their review cycle at box 1, due
// immediately (nextReviewAtMs = their original createdAtMs).
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `vocab_lists` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE vocab ADD COLUMN listId INTEGER")
        db.execSQL("ALTER TABLE vocab ADD COLUMN leitnerBox INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE vocab ADD COLUMN nextReviewAtMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vocab ADD COLUMN lastReviewedAtMs INTEGER")
        db.execSQL("UPDATE vocab SET nextReviewAtMs = createdAtMs")
    }
}

// v3 -> v4: adds source-attribution columns to `texts` for content imported
// from an external source (Vikidia and beyond -- ROADMAP.md section 6). All
// nullable, so existing rows (pasted texts, file imports, RSS news) just get
// NULL and keep behaving exactly as before.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE texts ADD COLUMN sourceUrl TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN sourceName TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN author TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN license TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN publishedAt INTEGER")
    }
}

@Database(
    entities = [TextDocument::class, VocabEntry::class, VocabList::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun textDao(): TextDao
    abstract fun vocabDao(): VocabDao
    abstract fun vocabListDao(): VocabListDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "french_reader.db"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    // Safety net only -- covers a version jump with no
                    // matching migration (e.g. skipping straight from a
                    // much older schema); the normal v2->v3 path above
                    // never falls back to this.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
