package com.ziaee.frenchreader.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Reverts [MIGRATION_17_18] on a freshly created current-schema database, so older migration
 * tests can keep building "current schema, then strip back" fixtures. */
internal fun SupportSQLiteDatabase.downgrade18To17() {
    listOf(
        "texts", "library_folders", "vocab", "vocab_lists", "review_log", "resources", "shadow_attempts"
    ).forEach { table ->
        execSQL("DROP INDEX IF EXISTS `index_${table}_language`")
    }
    execSQL("DROP INDEX IF EXISTS `index_texts_externalKey_language`")
    execSQL("DROP INDEX IF EXISTS `index_resources_url_language`")
    listOf(
        "texts", "library_folders", "vocab", "vocab_lists", "review_log", "resources", "shadow_attempts"
    ).forEach { table ->
        execSQL("ALTER TABLE `$table` DROP COLUMN `language`")
    }
    execSQL("CREATE UNIQUE INDEX `index_texts_externalKey` ON `texts` (`externalKey`)")
    execSQL("CREATE UNIQUE INDEX `index_resources_url` ON `resources` (`url`)")

    execSQL("DROP TABLE `headlines`")
    execSQL(
        "CREATE TABLE `headlines` (" +
            "`sourceId` TEXT NOT NULL, `sourceLabel` TEXT NOT NULL, " +
            "`externalId` TEXT NOT NULL, `title` TEXT NOT NULL, `snippet` TEXT NOT NULL, " +
            "`articleUrl` TEXT NOT NULL, `imageUrl` TEXT, `publishedAtMs` INTEGER, " +
            "`cachedAtMs` INTEGER NOT NULL, PRIMARY KEY(`sourceId`, `externalId`))"
    )
    execSQL("CREATE INDEX `index_headlines_publishedAtMs` ON `headlines` (`publishedAtMs`)")
    execSQL("CREATE INDEX `index_headlines_articleUrl` ON `headlines` (`articleUrl`)")

    execSQL("DROP TABLE `activity_log`")
    execSQL(
        "CREATE TABLE `activity_log` (`date` TEXT NOT NULL, `listeningMs` INTEGER NOT NULL, " +
            "PRIMARY KEY(`date`))"
    )
}
