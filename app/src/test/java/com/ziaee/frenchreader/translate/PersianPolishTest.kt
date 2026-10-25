package com.ziaee.frenchreader.translate

import org.junit.Assert.*
import org.junit.Test

class PersianPolishTest {
    private val z = "‌"

    @Test fun joinsVerbPrefixes() {
        assertEquals("آیا می${z}دانید این برای چیست؟", polishPersian("آیا می دانید این برای چیست ?"))
        assertEquals("ما نمی${z}خواهیم", polishPersian("ما نمی  خواهیم"))
    }

    @Test fun joinsPluralAndSuperlativeSuffixes() {
        assertEquals("کتاب${z}های خوب", polishPersian("کتاب های خوب"))
        assertEquals("بزرگ${z}ترین شهر", polishPersian("بزرگ ترین شهر"))
    }

    @Test fun leavesWordsThatOnlyStartLikeAPrefixOrSuffix() {
        assertEquals("میز و هاله", polishPersian("میز و هاله"))
        assertEquals("نمیر", polishPersian("نمیر"))
        assertEquals("کمی بعد", polishPersian("کمی بعد"))
    }

    @Test fun joinsEndingsAfterSilentHe() {
        assertEquals("مکالمه${z}ای که", polishPersian("مکالمه ای که"))
        assertEquals("خسته${z}ام", polishPersian("خسته ام"))
        assertEquals("خانه${z}اش", polishPersian("خانه اش"))
        assertEquals("خانه ایران", polishPersian("خانه ایران"))
        assertEquals("کتاب ای", polishPersian("کتاب ای"))
        assertEquals("به ماه ای دوست من نگاه کن.", polishPersian("به ماه ای دوست من نگاه کن."))
        assertEquals("او گفت که ای کاش زودتر آمده بود.", polishPersian("او گفت که ای کاش زودتر آمده بود."))
        assertEquals("به گروه ای دوستان", polishPersian("به گروه ای دوستان"))
        assertEquals("دوره${z}ای بسیار تاریک", polishPersian("دوره ای بسیار تاریک"))
    }

    @Test fun persianLettersAndPunctuation() {
        assertEquals("کتاب، دفتر؛ قلم.", polishPersian("كتاب , دفتر ; قلم ."))
        assertEquals("یک", polishPersian("يك"))
    }

    @Test fun leavesFrenchAndNumbersAlone() {
        val french = "Bonjour , ça va ? 1,5 kg ; oui ."
        assertEquals(french, polishPersian(french))
        assertEquals("قیمت 1,5 یورو", polishPersian("قیمت 1,5 یورو"))
    }

    @Test fun keepsLineBreaksForLineBatches() {
        assertEquals("می${z}روم\nمی${z}آیم", polishPersian("می روم \nمی آیم"))
    }

    @Test fun tabsAndSpacesGiveTheSameResultFreshAndFromCache() {
        val fresh = polishPersian("می\t روم")
        assertEquals("می${z}روم", fresh)
        assertEquals(fresh, polishPersian(fresh))
    }

    @Test fun echoedSourceIsStillTheSameTextAfterLetterNormalization() {
        assertTrue(TranslationService.isSameText(polishPersian("Ali (علي)"), "Ali (علي)"))
    }

    @Test fun isIdempotentAndOnlyForPersian() {
        val once = polishPersian("آنها می خواهند کتاب ها را ببینند ?")
        assertEquals(once, polishPersian(once))
        assertEquals("they want , ok ?", polishTranslation("they want , ok ?", "en"))
        assertEquals("می${z}خواهند", polishTranslation("می خواهند", "fa"))
    }
}
