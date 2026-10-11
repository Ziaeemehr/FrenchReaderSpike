package com.ziaee.frenchreader.share

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ziaee.frenchreader.exams.examToPdfBlocks
import com.ziaee.frenchreader.exams.loadExams
import com.ziaee.frenchreader.grammar.GrammarExportLabels
import com.ziaee.frenchreader.grammar.grammarNoteToPdfBlocks
import com.ziaee.frenchreader.grammar.loadGrammarNotes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val signature = shareSignatureLines("French Reader")

    /** Files stay in cache/pdf-test so a person can pull them and look at the layout. */
    private fun render(name: String, blocks: List<PdfBlock>): Int {
        val file = File(context.cacheDir, "pdf-test/$name").apply { parentFile?.mkdirs() }
        file.outputStream().use { writePdf(context, blocks, signature, it) }
        return PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(0).use { assertEquals(595, it.width) }
            pdf.pageCount
        }
    }

    @Test fun grammarNoteAndExamGuideRenderAsReadablePdfs() {
        val labels = GrammarExportLabels(
            "کاربرد", "ساختار", "مثال‌ها", "اشتباه رایج", "نکته‌های بیشتر", "مقایسه",
            "خط زمانی", "انتخاب ساختار", "مراحل", "درست", "نادرست", "French Reader"
        )
        val note = loadGrammarNotes(context.assets).first { it.id == "passe-compose-vs-imparfait" }
        assertTrue(render("note.pdf", grammarNoteToPdfBlocks(note, labels)) >= 1)
        assertTrue(render("tcf.pdf", examToPdfBlocks(loadExams(context.assets).single(), "منابع")) >= 2)
    }

    @Test fun longParagraphFlowsOntoFurtherPages() {
        val text = (1..400).joinToString(" ") { "Phrase numéro $it." }
        assertTrue(render("long.pdf", listOf(PdfBlock.Paragraph(PdfText(text)))) >= 2)
        assertEquals(1, render("empty.pdf", emptyList()))
    }
}
