package com.ziaee.frenchreader.translate

import org.junit.Assert.*
import org.junit.Test

class MeaningPolishTest {
    private val z = "‌"

    @Test fun dropsTheArticleOfAFrenchIndefiniteNoun() {
        assertEquals("محله", polishPersianMeaning("un quartier", "یک محله"))
        assertEquals("برادر دوقلو", polishPersianMeaning("un frère jumeau", "یک برادر دوقلو"))
        assertEquals("مسئولیت", polishPersianMeaning("une responsabilité", "یک مسئولیت."))
    }

    @Test fun keepsANumberAndWordsWithoutAnArticle() {
        assertEquals("یک کیلو آرد", polishPersianMeaning("un kilo de farine", "یک کیلو آرد"))
        assertEquals("یک", polishPersianMeaning("un", "یک"))
        assertEquals("یکشنبه", polishPersianMeaning("dimanche", "یکشنبه"))
    }

    @Test fun turnsImperativesIntoInfinitives() {
        assertEquals("ورزش کردن", polishPersianMeaning("faire du sport", "ورزش کن"))
        assertEquals("تصمیم گرفتن", polishPersianMeaning("prendre une décision", "تصمیم بگیر"))
        assertEquals("به فرانسه آمدن", polishPersianMeaning("venir en France", "به فرانسه بیاید"))
        assertEquals("قدم زدن", polishPersianMeaning("se promener", "قدم بزن"))
        assertEquals("شنیدن", polishPersianMeaning("entendre", "بشنو"))
        assertEquals("نوشتن", polishPersianMeaning("écrire", "برای نوشتن"))
    }

    @Test fun handlesDashteBashAndPresentTense() {
        assertEquals("امتحان داشتن", polishPersianMeaning("avoir des examens", "امتحان داشته باشند"))
        assertEquals("در لیون زندگی کردن", polishPersianMeaning("habiter à Lyon", "در لیون زندگی می${z}کنند"))
        assertEquals("در لیون زندگی کردن", polishPersianMeaning("habiter à Lyon", "در لیون زندگی می کنند"))
    }

    @Test fun leavesSentencesNounsAndOtherLanguagesAlone() {
        val sentence = "Un croissant chaud, c’est parfait le matin."
        assertEquals("یک کروسان داغ در صبح عالی است.", polishPersianMeaning(sentence, "یک کروسان داغ در صبح عالی است."))
        assertEquals("خاطره", polishPersianMeaning("souvenir", "خاطره"))
        assertEquals("برای همیشه", polishPersianMeaning("pour toujours", "برای همیشه"))
        assertEquals("a neighborhood", polishMeaning("un quartier", "a neighborhood", "en"))
    }

    @Test fun optionPartOfSpeechKeepsNounsAsNouns() {
        assertEquals("بده", polishPersianMeaning("devoir", "بده", "noun"))
        assertEquals("دادن", polishPersianMeaning("donner", "بده", "verb"))
        assertEquals("باشد", polishPersianMeaning("aller", "باشد", "interjection"))
    }

    @Test fun presentTenseAndIrregularStems() {
        assertEquals("رفتن", polishPersianMeaning("aller", "می${z}روند"))
        assertEquals("داشتن", polishPersianMeaning("avoir", "دارند"))
        assertEquals("گفتن", polishPersianMeaning("dire", "بگویید"))
        assertEquals("با ما آمدن", polishPersianMeaning("venir avec nous", "با ما بیایید"))
        assertEquals("آوردن", polishPersianMeaning("apporter", "بیاور"))
        assertEquals("انداختن", polishPersianMeaning("jeter", "بینداز"))
        assertEquals("شام خوردن", polishPersianMeaning("dîner", "شام بخور"))
    }

    @Test fun articleExceptionOnlyForQuantities() {
        assertEquals("دسر", polishPersianMeaning("un dessert", "یک دسر"))
        assertEquals("کلاس درس", polishPersianMeaning("une salle de classe", "یک کلاس درس"))
        assertEquals("یک لیوان آب", polishPersianMeaning("un verre d'eau", "یک لیوان آب"))
    }

    @Test fun longDictionaryPhraseIsNotASentence() {
        assertEquals("اینجا با کسی آشنا شدن",
            polishPersianMeaning("faire la connaissance de quelqu’un ici", "اینجا با کسی آشنا شوید"))
    }
}
