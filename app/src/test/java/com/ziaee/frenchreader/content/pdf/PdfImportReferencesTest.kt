package com.ziaee.frenchreader.content.pdf

import org.junit.Assert.*
import org.junit.Test

class PdfImportReferencesTest {
    @Test fun pendingDialogsProtectSharedFileUntilAllAreSavedOrCancelled() {
        val pending = PdfImportReferences()
        pending.retain("pdfs/a.pdf")
        pending.retain("pdfs/a.pdf")
        pending.release("pdfs/a.pdf")
        assertTrue(pending.contains("pdfs/a.pdf"))
        pending.release("pdfs/a.pdf")
        assertFalse(pending.contains("pdfs/a.pdf"))
    }
    @Test fun releaseDoesNotAffectOtherFilesAndNeverCreatesNegativeOwnership() {
        val pending = PdfImportReferences()
        pending.retain("pdfs/b.pdf")
        pending.release("pdfs/a.pdf")
        assertTrue(pending.contains("pdfs/b.pdf"))
        pending.retain("pdfs/a.pdf")
        assertTrue(pending.contains("pdfs/a.pdf"))
    }
}
