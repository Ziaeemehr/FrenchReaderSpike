package com.ziaee.frenchreader.exams

import com.ziaee.frenchreader.data.ExamAttempt
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ExamPracticeTest {
    private fun attempt(id: Long, question: String = "a", score: Double? = null, at: Long = id) =
        ExamAttempt(id, "tcf", question, "Subject", "", score, "", at, at)
    private fun item(id: String, month: String = "2026-01", count: Int = 1) =
        ExamQuestionItem(id, count, listOf(month), listOf(ExamQuestionText("Les études $id", "تحصیل")))

    @Test fun `bundled subjects have five valid ordered tasks`() {
        val set = parseExamQuestions(File("src/main/assets/exams/tcf-questions.json").readText())
        assertEquals(setOf("ee1", "ee2", "ee3", "eo2", "eo3"), set.tasks.map { it.id }.toSet())
        assertEquals("french-tcf.com", set.source.title)
        assertEquals("https://french-tcf.com", set.source.url)
        assertEquals("questions", set.source.id)
        assertEquals(897, set.tasks.sumOf { it.items.size })
        // The count file Home reads must agree with the subjects themselves.
        val meta = org.json.JSONObject(File("src/main/assets/exams/tcf-questions-meta.json").readText())
        assertEquals(set.tasks.sumOf { it.items.size }, meta.getInt("total"))
        set.tasks.forEach { task ->
            assertTrue(task.items.zipWithNext().all { (a, b) -> a.count >= b.count })
            task.items.forEach { item ->
                assertTrue(item.months.isNotEmpty())
                assertTrue(item.texts.all { it.fr.isNotBlank() })
                assertEquals(item.months.last(), item.lastSeen)
                if (task.id == "ee3") assertEquals(2, item.texts.size)
            }
        }
    }

    @Test fun `malformed items and empty tasks are skipped`() {
        val json = """{"version":1,"variant":"TCF","source":{"title":"s","url":"https://s"},"tasks":[
            {"id":"ee1","skill":"EE","task":1,"titleFr":"t","exportedOn":"2026-01-01","items":[
            {"id":"bad","count":1,"months":[],"texts":[{"fr":"ok"}]},
            {"id":"bad2","count":1,"months":["2026-01"],"texts":[]},
            {"id":"bad3","count":1,"months":["2026-01"],"texts":[{"fr":" "}]},
            {"id":"ok","count":1,"months":["2026-01"],"texts":[{"fr":"ok"}]}]},
            {"id":"ee2","skill":"EE","task":2,"titleFr":"t","exportedOn":"2026-01-01","items":[]}]}"""
        val set = parseExamQuestions(json)
        assertEquals(listOf("ok"), set.tasks.single().items.map { it.id })
        assertEquals("", set.tasks.single().items.single().texts.single().fa)
        assertThrows(IllegalArgumentException::class.java) { parseExamQuestions(json.replace("\"version\":1", "\"version\":2")) }
    }

    @Test fun `progress follows creation time then id and keeps best score`() {
        val p = examProgress(listOf(attempt(3, score = null, at = 2), attempt(2, score = 12.0, at = 2), attempt(1, score = 14.0, at = 1), attempt(4, "b", 9.0)))
        assertEquals(ExamQuestionProgress(3, 14.0, null, 2), p["a"])
        assertEquals(9.0, p["b"]!!.lastScore!!, 0.0)
        assertTrue(examProgress(emptyList()).isEmpty())
    }

    @Test fun `filters search both languages and preserve stable sort ties`() {
        val items = listOf(item("a", "2026-01", 5), item("b", "2026-03", 2), item("c", "2026-03", 3), item("d"), item("e"))
        val progress = examProgress(listOf(attempt(1, "a", 14.0), attempt(2, "b", 8.0), attempt(3, "c")))
        fun filtered(query: String = "", status: ExamQuestionStatus = ExamQuestionStatus.ALL, sort: ExamQuestionSort = ExamQuestionSort.FREQUENT) =
            filterExamQuestions(items, progress, query, status, sort).map { it.id }
        assertEquals(listOf("a", "b", "c", "d", "e"), filtered())
        assertEquals(filtered(), filtered("ETUDES"))
        assertEquals(filtered(), filtered("تحصیل"))
        assertTrue(filtered("absent").isEmpty())
        assertEquals(listOf("d", "e"), filtered(status = ExamQuestionStatus.TODO))
        assertEquals(listOf("a", "b", "c"), filtered(status = ExamQuestionStatus.DONE))
        assertEquals(listOf("c", "b", "a", "d", "e"), filtered(sort = ExamQuestionSort.RECENT))
        assertEquals(listOf("b", "a", "c", "d", "e"), filtered(sort = ExamQuestionSort.WEAKEST))
    }

    @Test fun `word counting ignores standalone punctuation`() {
        assertEquals(0, frenchWordCount(" ? ! — \n"))
        assertEquals(4, frenchWordCount("l’école est belle ! 123"))
        assertEquals(3, frenchWordCount("un\tdeux\ntrois"))
    }

    @Test fun `scores accept decimal separators and localized digits`() {
        listOf("14", "14.0", "۱۴", "١٤").forEach { assertEquals(ExamScoreResult(14.0, true), parseScore(it)) }
        listOf("14.5", "14,5", "۱۴,۵", "١٤.٥").forEach { assertEquals(ExamScoreResult(14.5, true), parseScore(it)) }
        assertEquals(ExamScoreResult(null, true), parseScore(" "))
        listOf("-1", "20.1", "21", "NaN", "Infinity", "hello", "1,2,3").forEach { assertFalse(it, parseScore(it).valid) }
        assertTrue(parseScore("0").valid)
        assertTrue(parseScore("20").valid)
        assertEquals("14", formatScore(14.0))
        assertEquals("14.5", formatScore(14.5))
    }
}
