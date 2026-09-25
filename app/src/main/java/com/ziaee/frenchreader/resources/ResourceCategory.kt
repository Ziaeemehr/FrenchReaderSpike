package com.ziaee.frenchreader.resources

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import com.ziaee.frenchreader.R

/** Display order is declaration order. [key] is what the database stores -- never rename one. */
enum class ResourceCategory(
    val key: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    PODCASTS("podcasts", R.string.resource_category_podcasts, Icons.Default.Headphones),
    VIDEO("video", R.string.resource_category_video, Icons.Default.OndemandVideo),
    PERSIAN("persian", R.string.resource_category_persian, Icons.Default.RecordVoiceOver),
    TELEGRAM("telegram", R.string.resource_category_telegram, Icons.Default.Send),
    READING("reading", R.string.resource_category_reading, Icons.Default.MenuBook),
    NEWS("news", R.string.resource_category_news, Icons.Default.Newspaper),
    GRAMMAR("grammar", R.string.resource_category_grammar, Icons.Default.Spellcheck),
    DICTIONARIES("dictionaries", R.string.resource_category_dictionaries, Icons.Default.Translate),
    EXAMS("exams", R.string.resource_category_exams, Icons.Default.WorkspacePremium),
    COURSES("courses", R.string.resource_category_courses, Icons.Default.School),
    TEXTBOOKS("textbooks", R.string.resource_category_textbooks, Icons.Default.AutoStories),
    BOOKS("books", R.string.resource_category_books, Icons.Default.LocalLibrary),
    OTHER("other", R.string.resource_category_other, Icons.Default.Link);

    companion object {
        fun fromKey(key: String?): ResourceCategory =
            entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** A built-in resource. [description] is keyed by language ("en", "fr", "fa"); the UI picks the
 * app language and falls back to English. [level] is a CEFR range such as "A2–B1". */
data class DefaultResource(
    val title: String,
    val url: String,
    val category: ResourceCategory,
    val createdAtMs: Long,
    val level: String? = null,
    val description: Map<String, String> = emptyMap(),
    val imageUrl: String? = null
)

private fun d(en: String, fr: String, fa: String) = mapOf("en" to en, "fr" to fr, "fa" to fa)

/**
 * Built-in resources. New entries are added once to existing installs (see ResourcesViewModel);
 * entries whose URL a user already has only lend it their description and level. createdAtMs is
 * the catalog position, which keeps built-ins in this order below the user's own links.
 */
val DEFAULT_RESOURCES: List<DefaultResource> = listOf(
    // Podcasts
    DefaultResource(
        "InnerFrench", "https://innerfrench.com/podcast/", ResourceCategory.PODCASTS, 1, "B1–B2",
        d("Slow, clear podcast in French about culture and society, made for intermediate learners.",
            "Podcast en français clair et posé sur la culture et la société, pour niveau intermédiaire.",
            "پادکستی به فرانسوی آرام و واضح دربارهٔ فرهنگ و جامعه، برای سطح متوسط.")
    ),
    DefaultResource(
        "Journal en français facile (RFI)",
        "https://francaisfacile.rfi.fr/fr/podcasts/journal-en-fran%C3%A7ais-facile/",
        ResourceCategory.PODCASTS, 2, "A2–B1",
        d("Ten minutes of world news every day in simple French, with transcripts.",
            "Dix minutes d'actualité chaque jour en français simple, avec transcription.",
            "هر روز ده دقیقه خبر جهان به فرانسوی ساده، همراه با متن.")
    ),
    DefaultResource(
        "Coffee Break French", "https://coffeebreakfrench.com/", ResourceCategory.PODCASTS, 3, "A1–B1",
        d("Structured audio lessons that explain grammar and vocabulary step by step.",
            "Leçons audio structurées qui expliquent grammaire et vocabulaire pas à pas.",
            "درس‌های صوتی منظم که دستور و واژگان را قدم‌به‌قدم توضیح می‌دهند.")
    ),
    DefaultResource(
        "Français Authentique", "https://www.francaisauthentique.com/podcast/", ResourceCategory.PODCASTS, 4, "B1–C1",
        d("Everyday spoken French and learning tips from a native teacher.",
            "Le français parlé du quotidien et des conseils d'apprentissage par un professeur natif.",
            "فرانسوی گفتاری روزمره و نکته‌های یادگیری از یک معلم فرانسوی‌زبان.")
    ),
    DefaultResource(
        "Podcast Français Facile", "https://www.podcastfrancaisfacile.com/", ResourceCategory.PODCASTS, 5, "A1–B1",
        d("Short dialogues and texts with audio, transcripts and exercises.",
            "Dialogues et textes courts avec audio, transcription et exercices.",
            "گفت‌وگوها و متن‌های کوتاه با صدا، متن و تمرین.")
    ),
    // Video
    DefaultResource(
        "TV5MONDE Apprendre le français", "https://apprendre.tv5monde.com/fr", ResourceCategory.VIDEO, 10, "A1–C1",
        d("Short news and culture videos sorted by level, each with exercises.",
            "Courtes vidéos d'actualité et de culture classées par niveau, avec exercices.",
            "ویدیوهای کوتاه خبری و فرهنگی بر اساس سطح، هرکدام با تمرین.")
    ),
    DefaultResource(
        "TV5MONDE", "https://www.tv5monde.com/", ResourceCategory.VIDEO, 11, "B2–C2",
        d("French-language international TV: news, series and documentaries.",
            "La chaîne internationale francophone : infos, séries et documentaires.",
            "شبکهٔ بین‌المللی فرانسوی‌زبان: خبر، سریال و مستند.")
    ),
    DefaultResource(
        "Easy French", "https://www.youtube.com/@EasyFrench", ResourceCategory.VIDEO, 12, "A2–B2",
        d("Street interviews with real French speakers, subtitled in French and English.",
            "Micro-trottoirs avec de vrais francophones, sous-titrés en français et en anglais.",
            "مصاحبه‌های خیابانی با فرانسوی‌زبان‌ها، با زیرنویس فرانسوی و انگلیسی.")
    ),
    DefaultResource(
        "Français avec Pierre", "https://www.youtube.com/@francaisavecpierre", ResourceCategory.VIDEO, 13, "A2–C1",
        d("Grammar, vocabulary and pronunciation lessons explained in French.",
            "Leçons de grammaire, de vocabulaire et de prononciation expliquées en français.",
            "درس‌های دستور، واژگان و تلفظ، به زبان فرانسوی.")
    ),
    // French taught in Persian (YouTube). Links are channel-name searches, so they keep working
    // if a channel changes its handle.
    DefaultResource(
        "Mostafa Shalchi (مصطفی شالچی)", "https://www.youtube.com/results?search_query=Mostafa+Shalchi",
        ResourceCategory.PERSIAN, 15, "A1–C1",
        d("French lessons in Persian by a teacher with over 20 years of experience; hundreds of videos.",
            "Cours de français en persan par un professeur de plus de 20 ans d'expérience ; des centaines de vidéos.",
            "آموزش فرانسه به فارسی توسط مدرسی با بیش از ۲۰ سال سابقهٔ تدریس و همکاری با یونسکو و سفارت فرانسه؛ صدها ویدیو.")
    ),
    DefaultResource(
        "فرانسه بگو (Fahimeh Asadi Rousi)",
        "https://www.youtube.com/results?search_query=%D9%81%D8%B1%D8%A7%D9%86%D8%B3%D9%87+%D8%A8%DA%AF%D9%88",
        ResourceCategory.PERSIAN, 16, "A1–B2",
        d("French teacher and translator living in France; lessons in Persian and live sessions.",
            "Professeure et traductrice vivant en France ; cours en persan et sessions en direct.",
            "مدرس و مترجم زبان فرانسه، دانش‌آموختهٔ دانشگاه تهران و ساکن فرانسه؛ آموزش به فارسی و جلسه‌های زنده.")
    ),
    DefaultResource(
        "Class Zaban (کلاس زبان)", "https://www.youtube.com/results?search_query=class+zaban",
        ResourceCategory.PERSIAN, 17, "A1–B1",
        d("Free French and English lessons for Persian speakers, by a teacher living in Canada.",
            "Cours gratuits de français et d'anglais pour persanophones, par un professeur vivant au Canada.",
            "آموزش رایگان فرانسه و انگلیسی برای فارسی‌زبانان، از مدرسی ساکن کانادا.")
    ),
    DefaultResource(
        "Mahya Polyglot (محیا میرصادقی)", "https://www.youtube.com/results?search_query=Mahya+polyglot",
        ResourceCategory.PERSIAN, 18, "A1–B1",
        d("Language learning and culture, including French, from a polyglot who speaks Persian.",
            "Apprentissage des langues et culture, dont le français, par une polyglotte persanophone.",
            "یادگیری زبان و فرهنگ، از جمله فرانسه، از یک چندزبانهٔ فارسی‌زبان.")
    ),
    DefaultResource(
        "Avec Faranak", "https://www.youtube.com/@Avec.faranak", ResourceCategory.PERSIAN, 181, null,
        d("An Iranian teacher teaching French in Persian on YouTube.",
            "Un professeur iranien qui enseigne le français en persan sur YouTube.",
            "مدرس ایرانی که زبان فرانسه را به فارسی در یوتیوب آموزش می‌دهد.")
    ),
    DefaultResource(
        "Mohammad Sharifinia (محمد شریفی‌نیا)", "https://www.youtube.com/@mohammadsharifinia",
        ResourceCategory.PERSIAN, 182, null,
        d("An Iranian teacher teaching French in Persian on YouTube.",
            "Un professeur iranien qui enseigne le français en persan sur YouTube.",
            "مدرس ایرانی که زبان فرانسه را به فارسی در یوتیوب آموزش می‌دهد.")
    ),
    DefaultResource(
        "Mlle Zeinab", "https://www.youtube.com/@mlle_zeinab", ResourceCategory.PERSIAN, 183, null,
        d("An Iranian teacher teaching French in Persian on YouTube.",
            "Un professeur iranien qui enseigne le français en persan sur YouTube.",
            "مدرس ایرانی که زبان فرانسه را به فارسی در یوتیوب آموزش می‌دهد.")
    ),
    // Telegram channels
    DefaultResource(
        "@sorbonne_fr", "https://t.me/sorbonne_fr", ResourceCategory.TELEGRAM, 19, null,
        d("Telegram channel for learning French.",
            "Chaîne Telegram pour apprendre le français.",
            "کانال تلگرام برای یادگیری زبان فرانسه.")
    ),
    // Reading
    DefaultResource(
        "Fabulang", "https://www.fabulang.com/en/fr/", ResourceCategory.READING, 20, "A1–C2",
        d("Illustrated graded stories with audio, from beginner to advanced.",
            "Histoires illustrées graduées avec audio, du débutant à l'avancé.",
            "داستان‌های مصور سطح‌بندی‌شده با صدا، از مبتدی تا پیشرفته.")
    ),
    DefaultResource(
        "FluencyDrop", "https://fluencydrop.com/stories/french", ResourceCategory.READING, 21, "A2–B2",
        d("Short graded stories with audio and translations.",
            "Courtes histoires graduées avec audio et traductions.",
            "داستان‌های کوتاه سطح‌بندی‌شده با صدا و ترجمه.")
    ),
    DefaultResource(
        "Lingua.com French reading", "https://lingua.com/french/reading/", ResourceCategory.READING, 22, "A1–B2",
        d("Short texts on everyday topics with comprehension questions.",
            "Textes courts sur la vie quotidienne avec questions de compréhension.",
            "متن‌های کوتاه دربارهٔ زندگی روزمره با پرسش درک مطلب.")
    ),
    DefaultResource(
        "Lawless French reading", "https://www.lawlessfrench.com/reading/", ResourceCategory.READING, 23, "A2–B2",
        d("Reading practice with vocabulary help and translations.",
            "Exercices de lecture avec aide au vocabulaire et traductions.",
            "تمرین خواندن با کمک واژگان و ترجمه.")
    ),
    DefaultResource(
        "Vikidia", "https://fr.vikidia.org/", ResourceCategory.READING, 24, "A2–B1",
        d("A French encyclopedia written for young readers: simple, clear articles.",
            "Encyclopédie pour les jeunes lecteurs : des articles simples et clairs.",
            "دانشنامه‌ای فرانسوی برای نوجوانان با مقاله‌های ساده و روشن.")
    ),
    // News
    DefaultResource(
        "RFI Français facile", "https://francaisfacile.rfi.fr/", ResourceCategory.NEWS, 30, "A1–B2",
        d("News-based lessons and exercises from Radio France Internationale.",
            "Leçons et exercices à partir de l'actualité, par Radio France Internationale.",
            "درس و تمرین بر پایهٔ خبرها، از رادیو بین‌المللی فرانسه.")
    ),
    DefaultResource(
        "1jour1actu", "https://www.1jour1actu.com/", ResourceCategory.NEWS, 31, "A2–B1",
        d("Current events explained simply for young readers.",
            "L'actualité expliquée simplement aux jeunes lecteurs.",
            "رویدادهای روز به زبان ساده برای خوانندگان جوان.")
    ),
    DefaultResource(
        "franceinfo", "https://www.francetvinfo.fr/", ResourceCategory.NEWS, 32, "B2–C2",
        d("National news from France's public broadcaster.",
            "L'actualité nationale par le service public.",
            "خبرهای فرانسه از رسانهٔ عمومی این کشور.")
    ),
    DefaultResource(
        "MotsActu", "https://motsactu.com/stories/", ResourceCategory.NEWS, 33, "A1–B2",
        d("News rewritten in simplified French, with each article adapted to levels A1 to B2.",
            "L'actualité en français simplifié, chaque article adapté aux niveaux A1 à B2.",
            "خبرها به فرانسوی ساده‌شده، هر خبر در سطح‌های A1 تا B2.")
    ),
    // Grammar
    DefaultResource(
        "Le Conjugueur", "https://leconjugueur.lefigaro.fr/", ResourceCategory.GRAMMAR, 40, "A1–C2",
        d("Conjugation tables for every French verb, plus grammar rules.",
            "Tableaux de conjugaison de tous les verbes, et règles de grammaire.",
            "جدول صرف همهٔ فعل‌های فرانسوی و قواعد دستوری.")
    ),
    DefaultResource(
        "Lawless French grammar", "https://www.lawlessfrench.com/grammar/", ResourceCategory.GRAMMAR, 41, "A1–C1",
        d("Clear explanations of French grammar points, with examples and quizzes.",
            "Explications claires des points de grammaire, avec exemples et quiz.",
            "توضیح روشن نکته‌های دستوری با مثال و آزمونک.")
    ),
    DefaultResource(
        "Kwiziq French", "https://french.kwiziq.com/", ResourceCategory.GRAMMAR, 42, "A1–C1",
        d("Grammar lessons and adaptive quizzes that find your weak points.",
            "Leçons de grammaire et quiz adaptatifs qui repèrent vos points faibles.",
            "درس‌های دستور و آزمونک‌هایی که نقاط ضعف شما را پیدا می‌کنند.")
    ),
    DefaultResource(
        "Le Point du FLE", "https://www.lepointdufle.net/", ResourceCategory.GRAMMAR, 43, "A1–C1",
        d("A large directory of free French exercises, sorted by topic.",
            "Un grand répertoire d'exercices de français gratuits, classés par thème.",
            "فهرستی بزرگ از تمرین‌های رایگان فرانسوی، مرتب‌شده بر اساس موضوع.")
    ),
    // Dictionaries
    DefaultResource(
        "WordReference", "https://www.wordreference.com/fren/", ResourceCategory.DICTIONARIES, 50, null,
        d("French–English dictionary with example phrases and a helpful forum.",
            "Dictionnaire français–anglais avec expressions et un forum utile.",
            "فرهنگ فرانسوی–انگلیسی با عبارت‌های نمونه و یک انجمن پرسش‌وپاسخ.")
    ),
    DefaultResource(
        "Larousse", "https://www.larousse.fr/dictionnaires/francais", ResourceCategory.DICTIONARIES, 51, null,
        d("The classic monolingual French dictionary.",
            "Le dictionnaire unilingue de référence.",
            "فرهنگ یک‌زبانهٔ مرجع فرانسوی.")
    ),
    DefaultResource(
        "Linguee", "https://www.linguee.fr/francais-anglais/", ResourceCategory.DICTIONARIES, 52, null,
        d("Shows how words are translated in real bilingual texts.",
            "Montre comment les mots sont traduits dans de vrais textes bilingues.",
            "نشان می‌دهد واژه‌ها در متن‌های واقعی دوزبانه چطور ترجمه شده‌اند.")
    ),
    DefaultResource(
        "Forvo", "https://fr.forvo.com/", ResourceCategory.DICTIONARIES, 53, null,
        d("Hear how native speakers pronounce any word.",
            "Écoutez la prononciation de n'importe quel mot par des natifs.",
            "تلفظ هر واژه را از زبان فرانسوی‌زبان‌ها بشنوید.")
    ),
    // Exams
    DefaultResource(
        "TCF Canada (France Éducation international)",
        "https://www.france-education-international.fr/test/tcf-canada", ResourceCategory.EXAMS, 60, "A1–C2",
        d("Official information and sample tasks for the TCF Canada.",
            "Informations officielles et exemples d'épreuves du TCF Canada.",
            "اطلاعات رسمی و نمونه‌سؤال‌های آزمون TCF کانادا.")
    ),
    DefaultResource(
        "TCF tout public", "https://www.france-education-international.fr/test/tcf-tout-public",
        ResourceCategory.EXAMS, 61, "A1–C2",
        d("Official page of the general TCF test, with sample questions.",
            "Page officielle du TCF tout public, avec exemples de questions.",
            "صفحهٔ رسمی آزمون عمومی TCF با نمونه‌سؤال.")
    ),
    DefaultResource(
        "DELF / DALF", "https://www.france-education-international.fr/diplome/delf-tout-public",
        ResourceCategory.EXAMS, 62, "A1–C2",
        d("Official DELF and DALF diploma information and sample papers.",
            "Informations officielles et sujets d'exemple du DELF et du DALF.",
            "اطلاعات رسمی و نمونه‌آزمون‌های مدرک DELF و DALF.")
    ),
    // Courses
    DefaultResource(
        "Bonjour de France", "https://www.bonjourdefrance.com/", ResourceCategory.COURSES, 70, "A1–C1",
        d("Free online exercises and tests in grammar, vocabulary and comprehension.",
            "Exercices et tests gratuits en grammaire, vocabulaire et compréhension.",
            "تمرین‌ها و آزمون‌های رایگان دستور، واژگان و درک مطلب.")
    ),
    DefaultResource(
        "Français interactif", "https://www.laits.utexas.edu/fi/", ResourceCategory.COURSES, 71, "A1–B1",
        d("A complete first-year university course with videos and grammar.",
            "Un cours universitaire complet de première année, avec vidéos et grammaire.",
            "یک دورهٔ کامل سال اول دانشگاه با ویدیو و دستور زبان.")
    ),
    // Classic French textbooks (covers from Open Library). Links search the title and publisher.
    DefaultResource(
        "Grammaire progressive du français", "https://www.google.com/search?q=Grammaire+progressive+du+fran%C3%A7ais+CLE+International",
        ResourceCategory.TEXTBOOKS, 75, "A1–C1",
        d("Grammar explained on the left page, exercises on the right; levels from beginner to advanced.",
            "La grammaire expliquée en page de gauche, les exercices à droite ; du niveau débutant à avancé.",
            "توضیح دستور در صفحهٔ چپ و تمرین در صفحهٔ راست؛ از مبتدی تا پیشرفته."),
        imageUrl = "https://covers.openlibrary.org/b/id/7426885-M.jpg"
    ),
    DefaultResource(
        "Vocabulaire progressif du français", "https://www.google.com/search?q=Vocabulaire+progressif+du+fran%C3%A7ais+CLE+International",
        ResourceCategory.TEXTBOOKS, 76, "A1–C1",
        d("Vocabulary by theme, each lesson followed by exercises.",
            "Le vocabulaire par thèmes, chaque leçon suivie d'exercices.",
            "واژگان موضوعی؛ هر درس همراه با تمرین."),
        imageUrl = "https://covers.openlibrary.org/b/id/972032-M.jpg"
    ),
    DefaultResource(
        "Communication progressive du français", "https://www.google.com/search?q=Communication+progressive+du+fran%C3%A7ais+CLE+International",
        ResourceCategory.TEXTBOOKS, 77, "A1–B2",
        d("Everyday conversations and useful phrases, with dialogues and activities.",
            "Situations de communication du quotidien et expressions utiles, avec dialogues et activités.",
            "گفت‌وگوهای روزمره و عبارت‌های کاربردی، با دیالوگ و تمرین."),
        imageUrl = "https://covers.openlibrary.org/b/id/971998-M.jpg"
    ),
    DefaultResource(
        "Phonétique progressive du français", "https://www.google.com/search?q=Phon%C3%A9tique+progressive+du+fran%C3%A7ais+CLE+International",
        ResourceCategory.TEXTBOOKS, 78, "A1–B2",
        d("Pronunciation and intonation step by step, with audio.",
            "Prononciation et intonation pas à pas, avec audio.",
            "تلفظ و آهنگ کلام قدم‌به‌قدم، همراه با فایل صوتی."),
        imageUrl = "https://covers.openlibrary.org/b/id/2140971-M.jpg"
    ),
    DefaultResource(
        "Conjugaison progressive du français", "https://www.google.com/search?q=Conjugaison+progressive+du+fran%C3%A7ais+CLE+International",
        ResourceCategory.TEXTBOOKS, 79, "A1–B2",
        d("Verb forms and tenses with hundreds of exercises.",
            "Les formes verbales et les temps, avec des centaines d'exercices.",
            "صرف فعل‌ها و زمان‌ها با صدها تمرین."),
        imageUrl = "https://covers.openlibrary.org/b/id/13974438-M.jpg"
    ),
    DefaultResource(
        "Grammaire en dialogues", "https://www.google.com/search?q=Grammaire+en+dialogues+CLE+International",
        ResourceCategory.TEXTBOOKS, 80, "A1–B2",
        d("Grammar through short everyday dialogues, with audio and exercises.",
            "La grammaire à travers de courts dialogues du quotidien, avec audio et exercices.",
            "دستور زبان از راه گفت‌وگوهای کوتاه روزمره، با فایل صوتی و تمرین."),
        imageUrl = "https://covers.openlibrary.org/b/id/7550378-M.jpg"
    ),
    DefaultResource(
        "Vocabulaire en dialogues", "https://www.google.com/search?q=Vocabulaire+en+dialogues+CLE+International",
        ResourceCategory.TEXTBOOKS, 81, "A1–B1",
        d("Vocabulary learned through dialogues, with audio.",
            "Le vocabulaire appris à travers des dialogues, avec audio.",
            "یادگیری واژگان از راه گفت‌وگو، با فایل صوتی."),
        imageUrl = "https://covers.openlibrary.org/b/id/7735067-M.jpg"
    ),
    DefaultResource(
        "Bescherelle : La conjugaison pour tous", "https://www.google.com/search?q=Bescherelle+%3A+La+conjugaison+pour+tous+Hatier",
        ResourceCategory.TEXTBOOKS, 82, "A1–C2",
        d("The reference conjugation tables for every French verb.",
            "Les tableaux de conjugaison de référence pour tous les verbes.",
            "جدول‌های مرجع صرف همهٔ فعل‌های فرانسوی."),
        imageUrl = "https://covers.openlibrary.org/b/id/974710-M.jpg"
    ),
    // Books
    DefaultResource(
        "Project Gutenberg: French short stories", "https://www.gutenberg.org/ebooks/bookshelf/634",
        ResourceCategory.BOOKS, 90, "B2–C2",
        d("Free public-domain books; this shelf has short stories.",
            "Livres gratuits du domaine public ; cette étagère réunit des nouvelles.",
            "کتاب‌های رایگان مالکیت عمومی؛ این قفسه داستان‌های کوتاه دارد.")
    ),
    DefaultResource(
        "Wikisource", "https://fr.wikisource.org/", ResourceCategory.BOOKS, 91, "B2–C2",
        d("Free French classics: novels, tales, poems and plays.",
            "Les classiques gratuits : romans, contes, poèmes et pièces.",
            "آثار کلاسیک رایگان فرانسوی: رمان، قصه، شعر و نمایشنامه.")
    ),
    DefaultResource(
        "Bibliothèque numérique romande", "https://ebooks-bnr.com/", ResourceCategory.BOOKS, 92, "B2–C2",
        d("Carefully edited free e-books of French literature.",
            "Livres numériques gratuits de littérature française, soigneusement édités.",
            "کتاب‌های الکترونیکی رایگان ادبیات فرانسه با ویرایش دقیق.")
    )
)

/** Built-in entry for a stored URL, if any (trailing slash and case ignored). */
fun defaultResourceFor(url: String): DefaultResource? {
    val key = url.trim().trimEnd('/').lowercase()
    return DEFAULT_RESOURCES.firstOrNull { it.url.trimEnd('/').lowercase() == key }
}
