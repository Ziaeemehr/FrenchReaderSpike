package com.ziaee.frenchreader.content

import android.content.Context
import androidx.room.withTransaction
import com.ziaee.frenchreader.data.AppDatabase
import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.TextBodyStorage
import com.ziaee.frenchreader.data.TextBodyStore
import com.ziaee.frenchreader.data.TextDocument
import com.ziaee.frenchreader.data.VocabEntry
import com.ziaee.frenchreader.data.VocabList
import com.ziaee.frenchreader.data.insertTextDocument
import com.ziaee.frenchreader.images.ArticleImageStorage
import com.ziaee.frenchreader.images.ArticleImageStore
import com.ziaee.frenchreader.ui.wordReferenceUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val GRADED_STORIES_FOLDER = "Histoires graduées"

data class DatasetPackStatus(val installed: Int, val total: Int) {
    val complete get() = installed >= total && total > 0
}

data class DatasetStatus(
    val decks: Map<String, DatasetPackStatus>,
    val stories: Map<String, DatasetPackStatus>
)

data class DatasetInstallResult(val added: Int, val skipped: Int)

class DatasetRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val bodyStore: TextBodyStorage = TextBodyStore(context),
    private val imageStore: ArticleImageStorage = ArticleImageStore(context)
) {
    suspend fun loadManifest(): DatasetManifest = withContext(Dispatchers.IO) {
        parseDatasetManifest(asset("manifest.json"))
    }

    suspend fun installDeck(pack: DatasetDeckPack): DatasetInstallResult = withContext(Dispatchers.IO) {
        val rows = parseDatasetDeck(asset(pack.file))
        val now = System.currentTimeMillis()
        db.withTransaction {
            val lists = db.vocabListDao().getAllOnce()
            val listId = lists.firstOrNull { it.name.equals(pack.name, true) }?.id
                ?: db.vocabListDao().insert(VocabList(name = pack.name))
            val existing = db.vocabDao().getAllOnce()
                .filter { it.listId == listId }
                .map { it.word.lowercase() }
                .toMutableSet()
            var added = 0
            rows.forEach { row ->
                if (existing.add(row.word.lowercase())) {
                    db.vocabDao().insert(
                        VocabEntry(
                            word = row.word,
                            sentence = row.sentence,
                            textId = 0,
                            dictionaryUrl = wordReferenceUrl(row.word),
                            meaning = datasetMeaning(row.meaning, row.lesson),
                            listId = listId,
                            createdAtMs = now + added,
                            nextReviewAtMs = now,
                            leitnerBox = 1,
                            lastReviewedAtMs = null
                        )
                    )
                    added++
                }
            }
            DatasetInstallResult(added, rows.size - added)
        }
    }

    suspend fun installStories(pack: DatasetStoryPack): DatasetInstallResult = withContext(Dispatchers.IO) {
        val rows = parseDatasetStories(asset(pack.file))
        db.withTransaction {
            val folders = db.libraryOrganizerDao()
            val root = folders.findFolderByName(GRADED_STORIES_FOLDER)?.id
                ?: folders.insertFolder(LibraryFolder(name = GRADED_STORIES_FOLDER))
            val level = folders.findFolderByName(pack.level, root)?.id
                ?: folders.insertFolder(LibraryFolder(name = pack.level, parentId = root))
            var added = 0
            rows.forEach { story ->
                if (db.textDao().findByExternalKey(story.key) == null) {
                    val stored = story.image?.let { image ->
                        val bytes = context.assets.open("dataset/$image").use { it.readBytes() }
                        imageStore.storeBytes(
                            "text_images/epub_dataset/${image.substringAfterLast('/')}",
                            bytes
                        )
                    }
                    val body = if (stored != null) {
                        // Brackets in the alt text would break the reader's image-line pattern.
                        val alt = story.title.replace(Regex("[\\[\\]]"), "")
                        "![$alt](epubimg:dataset/${stored.substringAfterLast('/')})\n\n${story.body}"
                    } else {
                        story.body
                    }
                    insertTextDocument(
                        db.textDao(),
                        bodyStore,
                        TextDocument(
                            title = story.title,
                            rawText = "",
                            sourceName = pack.source,
                            sourceUrl = story.sourceUrl,
                            externalKey = story.key,
                            folderId = level,
                            imagePath = stored
                        ),
                        body
                    )
                    added++
                }
            }
            DatasetInstallResult(added, rows.size - added)
        }
    }

    suspend fun status(manifestOrNull: DatasetManifest? = null): DatasetStatus = withContext(Dispatchers.IO) {
        val manifest = manifestOrNull ?: loadManifest()
        val lists = db.vocabListDao().getAllOnce()
        val entries = db.vocabDao().getAllOnce()
        val storyKeys = db.textDao().getDatasetExternalKeys().toSet()
        DatasetStatus(
            decks = manifest.decks.associate { pack ->
                val installed = lists.firstOrNull { it.name.equals(pack.name, true) }
                    ?.let { list -> entries.count { it.listId == list.id } }
                    ?: 0
                pack.id to DatasetPackStatus(installed, pack.count)
            },
            stories = manifest.stories.associate { pack ->
                val installed = parseDatasetStories(asset(pack.file)).count { it.key in storyKeys }
                pack.id to DatasetPackStatus(installed, pack.count)
            }
        )
    }

    suspend fun installAll(manifestOrNull: DatasetManifest? = null): DatasetInstallResult {
        val manifest = manifestOrNull ?: loadManifest()
        var added = 0
        var skipped = 0
        manifest.decks.forEach { pack ->
            installDeck(pack).also { result ->
                added += result.added
                skipped += result.skipped
            }
        }
        manifest.stories.forEach { pack ->
            installStories(pack).also { result ->
                added += result.added
                skipped += result.skipped
            }
        }
        return DatasetInstallResult(added, skipped)
    }

    private fun asset(path: String): String = context.assets.open("dataset/$path")
        .bufferedReader()
        .use { it.readText() }
}
