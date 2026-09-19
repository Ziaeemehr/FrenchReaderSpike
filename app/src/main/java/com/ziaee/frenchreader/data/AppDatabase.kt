package com.ziaee.frenchreader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ziaee.frenchreader.resources.DEFAULT_RESOURCES
import com.ziaee.frenchreader.resources.ResourceCategory

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

// v4 -> v5: preserves existing texts while adding downloaded-image storage,
// stable external keys for deduplication, recency tracking, and a small
// per-source cache for RSS headlines.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE texts ADD COLUMN imagePath TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN externalKey TEXT")
        db.execSQL("ALTER TABLE texts ADD COLUMN lastAccessedAtMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_texts_externalKey` " +
                "ON `texts` (`externalKey`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `headlines` (" +
                "`sourceId` TEXT NOT NULL, " +
                "`sourceLabel` TEXT NOT NULL, " +
                "`externalId` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`snippet` TEXT NOT NULL, " +
                "`articleUrl` TEXT NOT NULL, " +
                "`imageUrl` TEXT, " +
                "`publishedAtMs` INTEGER, " +
                "`cachedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`sourceId`, `externalId`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_headlines_publishedAtMs` " +
                "ON `headlines` (`publishedAtMs`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_headlines_articleUrl` " +
                "ON `headlines` (`articleUrl`)"
        )
    }
}

// v5 -> v6: adds review_log (one row per vocab-review answer) and
// activity_log (one row per day with listening time) for the Statistics
// screen (ROADMAP.md section 3). Both start empty -- existing texts/vocab
// are untouched, so the Statistics screen just starts at zero history
// for anyone upgrading.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `review_log` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`entryId` INTEGER NOT NULL, " +
                "`timestampMs` INTEGER NOT NULL, " +
                "`knew` INTEGER NOT NULL, " +
                "`boxBefore` INTEGER NOT NULL, " +
                "`boxAfter` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_review_log_timestampMs` " +
                "ON `review_log` (`timestampMs`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `activity_log` (" +
                "`date` TEXT NOT NULL PRIMARY KEY, " +
                "`listeningMs` INTEGER NOT NULL)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `resources` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`url` TEXT NOT NULL, " +
                "`imageUrl` TEXT, " +
                "`createdAtMs` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_resources_url` ON `resources` (`url`)")
        db.execSQL(
            "INSERT OR IGNORE INTO resources (title, url, imageUrl, createdAtMs) " +
                "VALUES ('Fabulang', 'https://www.fabulang.com/en/fr/', NULL, 0)"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `library_folders` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `library_tags` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`createdAtMs` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_library_tags_name` " +
                "ON `library_tags` (`name`)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `text_tags` (" +
                "`textId` INTEGER NOT NULL, " +
                "`tagId` INTEGER NOT NULL, " +
                "PRIMARY KEY(`textId`, `tagId`))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_text_tags_tagId` " +
                "ON `text_tags` (`tagId`)"
        )
        db.execSQL("ALTER TABLE texts ADD COLUMN folderId INTEGER")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_folders ADD COLUMN parentId INTEGER")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE resources ADD COLUMN category TEXT NOT NULL DEFAULT 'other'")
        db.execSQL("UPDATE resources SET category = 'video' WHERE url LIKE '%tv5monde.com%'")
        db.execSQL(
            "UPDATE resources SET category = 'reading' WHERE " +
                "url LIKE '%lingua.com%' OR " +
                "url LIKE '%lawlessfrench.com%' OR " +
                "url LIKE '%fluencydrop.com%' OR " +
                "url LIKE '%fabulang.com%'"
        )
        db.execSQL("UPDATE resources SET category = 'books' WHERE url LIKE '%gutenberg.org%'")
        DEFAULT_RESOURCES
            .filter { it.category == ResourceCategory.PODCASTS }
            .forEach { resource ->
                db.execSQL(
                    "INSERT OR IGNORE INTO resources " +
                        "(title, url, imageUrl, createdAtMs, category) VALUES (?, ?, NULL, ?, ?)",
                    arrayOf(resource.title, resource.url, resource.createdAtMs, resource.category.key)
                )
            }
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE texts ADD COLUMN bodyPath TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
    MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11
)

@Database(
    entities = [
        TextDocument::class, HeadlineEntity::class, VocabEntry::class, VocabList::class,
        ReviewLogEntry::class, ActivityLogEntry::class, ResourceLink::class,
        LibraryFolder::class, LibraryTag::class, TextTagCrossRef::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun textDao(): TextDao
    abstract fun headlineDao(): HeadlineDao
    abstract fun vocabDao(): VocabDao
    abstract fun vocabListDao(): VocabListDao
    abstract fun reviewLogDao(): ReviewLogDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun resourceDao(): ResourceDao
    abstract fun libraryOrganizerDao(): LibraryOrganizerDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "french_reader.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // Safety net only -- covers a version jump with no
                    // matching migration (e.g. skipping straight from a
                    // much older schema); the normal v2->v3 path above
                    // never falls back to this.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }

        // PRAGMA wal_checkpoint returns a result row (busy, log, checkpointed), so it
        // must go through query()/rawQuery() -- execSQL() rejects any statement that
        // returns data. TRUNCATE (not FULL) is required to actually shrink the -wal
        // file back to empty -- FULL only guarantees the data is checkpointed, not
        // that the file shrinks. The first column (busy) can come back 1 -- meaning
        // it only partially completed -- if Room's own internal reader connection
        // happens to be mid-use at that exact moment; that's transient, so retry a
        // few times rather than silently accepting a partial checkpoint (verified on
        // a real device: ignoring `busy` here left committed rows missing from a
        // plain copy of just the main .db file).
        fun checkpointWal(db: AppDatabase) {
            repeat(20) {
                val busy = db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
                if (busy == 0) return
                Thread.sleep(50)
            }
        }

        /** Closes and forgets the singleton so the next [get] call reopens against
         * whatever file is on disk at that point -- used right before a restore
         * overwrites the database file, since swapping the file under a live
         * Room instance is not safe. Callers must not use any existing DAO/db
         * reference obtained before this call. */
        fun closeForRestore() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
