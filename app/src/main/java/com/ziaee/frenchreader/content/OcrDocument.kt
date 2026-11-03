package com.ziaee.frenchreader.content

import com.ziaee.frenchreader.content.pdf.PdfPoint
import com.ziaee.frenchreader.content.pdf.PdfRect

data class OcrWord(val text: String, val box: PdfRect?, val cornerPoints: List<PdfPoint>, val confidence: Float?)
data class OcrLine(val text: String, val box: PdfRect?, val cornerPoints: List<PdfPoint>, val confidence: Float?, val words: List<OcrWord>)
data class OcrDocument(val blocks: List<List<OcrLine>>)
