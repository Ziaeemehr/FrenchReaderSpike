package com.ziaee.frenchreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EpubImageRefTest {
    private val filesDir = Files.createTempDirectory("files").toFile()

    private fun store(path: String) = File(filesDir, "text_images/$path").apply {
        parentFile!!.mkdirs(); writeText("x")
    }

    @Test
    fun resolvesDatasetHashAndNonJpegImages() {
        val dataset = store("epub_dataset/fabulang-a1-voyage-camping.jpg")
        val epub = store("epub_3fa9/0_1.jpg")
        val png = store("epub_3fa9/Photo 1.PNG")
        assertEquals(dataset.canonicalFile, resolveEpubImage(filesDir, "dataset/fabulang-a1-voyage-camping.jpg"))
        assertEquals(epub.canonicalFile, resolveEpubImage(filesDir, "3fa9/0_1.jpg"))
        assertEquals(png.canonicalFile, resolveEpubImage(filesDir, "3fa9/Photo 1.PNG"))
    }

    @Test
    fun rejectsMissingMalformedAndEscapingRefs() {
        store("epub_x/a.jpg")
        File(filesDir, "secret.jpg").writeText("x")
        assertNull(resolveEpubImage(filesDir, "x/missing.jpg"))
        assertNull(resolveEpubImage(filesDir, "x/a.txt"))
        assertNull(resolveEpubImage(filesDir, "../../secret.jpg"))
        assertNull(resolveEpubImage(filesDir, "x/../../secret.jpg"))
        assertNull(resolveEpubImage(filesDir, "a.jpg"))
    }
}
