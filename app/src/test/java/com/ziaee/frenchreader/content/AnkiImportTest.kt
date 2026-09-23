package com.ziaee.frenchreader.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AnkiImportTest {
    private val front = "au moment opportun<br>[sound:a.mp3]"
    private val back = "<div><b>EN:</b> at the right moment</div>\n<div><b>FA:</b> در زمان مناسب</div>\n" +
        "<div style=\"margin-top:8px\">Il est venu au moment opportun.</div><br>[sound:b.mp3]"

    private fun card(type: Int = 2, interval: Int = 20, due: Int = 1050, front: String = this.front, back: String = this.back) =
        AnkiCard("TCF Vocabulary", front, back, type, interval, due, reps = 6, lapses = 0)

    @Test fun `cleanAnkiText strips sound tags html and entities`() {
        assertEquals("au moment opportun", cleanAnkiText(front))
        assertEquals("a & b\nc", cleanAnkiText("<div>a&nbsp;&amp; b</div><div> c </div>[sound:x.mp3]"))
    }

    @Test fun `mapAnkiCard splits EN FA and example sentence`() {
        val m = mapAnkiCard(card())!!
        assertEquals("au moment opportun", m.word)
        assertEquals("at the right moment\nدر زمان مناسب", m.meaning)
        assertEquals("Il est venu au moment opportun.", m.sentence)
    }

    @Test fun `mapAnkiCard without EN FA puts whole back in meaning`() {
        val m = mapAnkiCard(card(front = "un dentiste", back = "la dentisterie / la profession"))!!
        assertEquals("la dentisterie / la profession", m.meaning)
        assertEquals("", m.sentence)
    }

    @Test fun `mapAnkiCard returns null for blank front`() {
        assertNull(mapAnkiCard(card(front = "[sound:a.mp3]")))
    }

    @Test fun `boxForInterval boundaries`() {
        assertEquals(listOf(1, 1, 2, 2, 3, 3, 4, 4, 5, 5),
            listOf(0, 1, 2, 3, 4, 7, 8, 15, 16, 400).map { boxForInterval(it) })
    }

    private val now = 1_800_000_000_000L
    private fun entry(c: AnkiCard, keep: Boolean, todayDay: Int? = 1030) =
        buildVocabEntry(c, mapAnkiCard(c)!!, listId = 7, keepProgress = keep, todayDay = todayDay,
            nowMs = now, dictionaryUrl = "u", zone = ZoneOffset.UTC)

    @Test fun `review card keeps box and due date when keepProgress`() {
        val e = entry(card(type = 2, interval = 20, due = 1050), keep = true)
        assertEquals(5, e.leitnerBox)
        assertEquals(true, e.learned)
        assertEquals(now, e.lastReviewedAtMs)
        val startOfDay = now - now % 86_400_000L
        assertEquals(startOfDay + 20 * 86_400_000L, e.nextReviewAtMs)
        assertEquals(7L, e.listId)
        assertEquals(0L, e.textId)
    }

    @Test fun `review card without todayDay falls back to box interval`() {
        val e = entry(card(type = 2, interval = 3, due = 99), keep = true, todayDay = null)
        assertEquals(2, e.leitnerBox)
        val startOfDay = now - now % 86_400_000L
        assertEquals(startOfDay + 2 * 86_400_000L, e.nextReviewAtMs)
    }

    @Test fun `new learning and no-progress cards use defaults or box 1`() {
        val fresh = entry(card(type = 0), keep = true)
        assertEquals(1, fresh.leitnerBox); assertNull(fresh.lastReviewedAtMs)
        val learning = entry(card(type = 1), keep = true)
        assertEquals(1, learning.leitnerBox); assertEquals(now, learning.lastReviewedAtMs); assertEquals(now, learning.nextReviewAtMs)
        val ignored = entry(card(type = 2, interval = 40), keep = false)
        assertEquals(1, ignored.leitnerBox); assertNull(ignored.lastReviewedAtMs)
    }

    @Test fun `parseAnkiExport reads cards and rejects other versions`() {
        val json = """{"version":1,"todayDay":null,"cards":[{"deck":"D","front":"f","back":"b","type":2,"interval":5,"due":9,"reps":1,"lapses":0}]}"""
        val e = parseAnkiExport(json)
        assertNull(e.todayDay); assertEquals(1, e.cards.size); assertEquals("D", e.cards[0].deck)
        assertTrue(runCatching { parseAnkiExport("""{"version":2,"cards":[]}""") }.isFailure)
    }

    @Test fun `plan skips duplicates blanks and existing words`() {
        val a = card(); val dup = card(); val blank = card(front = "[sound:a.mp3]")
        val b = card(front = "un dentiste", back = "x")
        val plan = planAnkiImport(AnkiExport(1030, listOf(a, dup, blank, b)),
            existingWordKeys = setOf("tcf vocabulary" to "un dentiste"))
        assertEquals(listOf("TCF Vocabulary"), plan.newListNames)
        assertEquals(1, plan.words.size)
        assertEquals(3, plan.skipped)
    }
}
