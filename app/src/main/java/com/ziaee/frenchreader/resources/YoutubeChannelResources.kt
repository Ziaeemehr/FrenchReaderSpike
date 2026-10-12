package com.ziaee.frenchreader.resources

/** A YouTube channel, linked by its permanent channel id so a renamed handle keeps working. */
private fun yt(
    title: String, channelId: String, category: ResourceCategory, position: Long,
    level: String?, description: Map<String, String>
) = DefaultResource(title, "https://www.youtube.com/channel/$channelId", category, position, level, description)

/** French YouTube channels, grouped by what a learner uses them for. */
internal val YOUTUBE_CHANNEL_RESOURCES: List<DefaultResource> = listOf(
    // French lessons
    yt(
        "innerFrench – YouTube", "UCI4xp8qHD1MDErkqxb1dPbA", ResourceCategory.YOUTUBE_LESSONS, 200, "B1–B2",
        d("Hugo talks in clear, natural French about culture, society and how to learn; made for intermediate learners.",
            "Hugo parle de culture, de société et d'apprentissage dans un français clair et naturel ; pour le niveau intermédiaire.",
            "هوگو با فرانسوی واضح و طبیعی دربارهٔ فرهنگ، جامعه و روش یادگیری حرف می‌زند؛ برای سطح متوسط.")
    ),
    yt(
        "Français avec Nelly", "UClEGQZlQURxTiMdwBPqiKDQ", ResourceCategory.YOUTUBE_LESSONS, 201, "A2–B2",
        d("Everyday spoken French: expressions, pronunciation and the gap between textbook French and real-life French.",
            "Le français parlé du quotidien : expressions, prononciation et écart entre le français des manuels et celui de la vraie vie.",
            "فرانسوی گفتاری روزمره: اصطلاح‌ها، تلفظ و تفاوت فرانسوی کتاب‌ها با فرانسوی واقعی.")
    ),
    yt(
        "Français Authentique – YouTube", "UCQpM25U6iqaRSO-SZxd5oDw", ResourceCategory.YOUTUBE_LESSONS, 202, "B1–C1",
        d("Johan explains expressions and shares learning advice, entirely in French.",
            "Johan explique des expressions et donne des conseils d'apprentissage, entièrement en français.",
            "یوهان اصطلاح‌ها را توضیح می‌دهد و نکته‌های یادگیری می‌گوید؛ تماماً به فرانسوی.")
    ),
    yt(
        "French Comprehensible Input", "UC-XUpEBvcQcRqMdtLhoXmOA", ResourceCategory.YOUTUBE_LESSONS, 203, "A1–B1",
        d("Stories and topics told slowly with drawings and gestures, so beginners can follow without translation.",
            "Histoires et sujets racontés lentement, avec dessins et gestes, pour suivre sans traduction dès le niveau débutant.",
            "داستان‌ها و موضوع‌هایی که آرام و با نقاشی و حرکت دست تعریف می‌شوند تا مبتدی‌ها بدون ترجمه بفهمند.")
    ),
    yt(
        "Alice Ayel", "UCbC9Utt1c6UXRqWw2lOTCBg", ResourceCategory.YOUTUBE_LESSONS, 204, "A1–B1",
        d("French taught through stories that Alice tells and draws, from complete beginner upwards.",
            "Le français par des histoires qu'Alice raconte et dessine, à partir du niveau grand débutant.",
            "یادگیری فرانسه با داستان‌هایی که آلیس تعریف و نقاشی می‌کند؛ از سطح کاملاً مبتدی.")
    ),
    yt(
        "Français avec Fluidité", "UCftkAUwCOQM_FhQ4216qFhA", ResourceCategory.YOUTUBE_LESSONS, 205, "B1–B2",
        d("Fabien helps intermediate learners understand fast, natural spoken French.",
            "Fabien aide les apprenants de niveau intermédiaire à comprendre le français parlé, rapide et naturel.",
            "فابین به زبان‌آموزان سطح متوسط کمک می‌کند فرانسوی گفتاری سریع و طبیعی را بفهمند.")
    ),
    yt(
        "Parlez-vous FRENCH", "UC5E3cis5nJyfuiU5_bzvIwg", ResourceCategory.YOUTUBE_LESSONS, 206, "A2–B2",
        d("Anne's lessons in French on grammar, vocabulary, pronunciation and correct usage.",
            "Les leçons d'Anne, en français, sur la grammaire, le vocabulaire, la prononciation et le bon usage.",
            "درس‌های آن به زبان فرانسوی دربارهٔ دستور، واژگان، تلفظ و کاربرد درست.")
    ),
    yt(
        "Madame à Paname", "UC59NqFOvritu4Rw93tQT4LQ", ResourceCategory.YOUTUBE_LESSONS, 207, "A2–B2",
        d("Grammar and conjugation explained in French with humour, one point per video.",
            "La grammaire et la conjugaison expliquées en français avec humour, un point par vidéo.",
            "دستور و صرف فعل به زبان فرانسوی و با چاشنی طنز؛ هر ویدیو یک نکته.")
    ),
    yt(
        "Ma prof de français", "UC_XYAAXeuD_3JPDJ-QSv1jg", ResourceCategory.YOUTUBE_LESSONS, 208, "A2–B2",
        d("Québec French explained by a teacher from Québec: expressions, pronunciation and culture.",
            "Le français québécois expliqué par une professeure du Québec : expressions, prononciation et culture.",
            "فرانسوی کبکی به زبان یک معلم اهل کبک: اصطلاح‌ها، تلفظ و فرهنگ.")
    ),
    yt(
        "French School TV", "UCb3u5vctZOjajpr39xy2jMA", ResourceCategory.YOUTUBE_LESSONS, 209, "A1–B1",
        d("Themed lessons on vocabulary, grammar and everyday situations.",
            "Leçons thématiques sur le vocabulaire, la grammaire et les situations du quotidien.",
            "درس‌های موضوعی دربارهٔ واژگان، دستور و موقعیت‌های روزمره.")
    ),
    yt(
        "Le Monde des Langues", "UC1ipKNAWcHhmcqKUEGg1RMg", ResourceCategory.YOUTUBE_LESSONS, 210, "B1–B2",
        d("Advice in French on how to learn a language well: methods, memory and motivation.",
            "Des conseils en français pour bien apprendre une langue : méthodes, mémoire et motivation.",
            "توصیه‌هایی به فرانسوی برای یادگیری بهتر زبان: روش‌ها، حافظه و انگیزه.")
    ),
    yt(
        "Carlito – Learn French Through Culture", "UCNw6J4vA8lLExo46eqBo4kg", ResourceCategory.YOUTUBE_LESSONS, 211, null,
        d("French taught through French culture.",
            "Le français à travers la culture française.",
            "یادگیری فرانسه از راه فرهنگ فرانسه.")
    ),
    yt(
        "Guillaume Posé – Professeur de français", "UCCD-nJGAxKmh1GnSuUx8Gig", ResourceCategory.YOUTUBE_LESSONS, 212, null,
        d("French lessons for learners on YouTube.",
            "Cours de français pour apprenants sur YouTube.",
            "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")
    ),
    yt(
        "Le French Club", "UCXJhsHwX3IeKrP6iS8KnboQ", ResourceCategory.YOUTUBE_LESSONS, 213, null,
        d("French lessons for learners on YouTube.",
            "Cours de français pour apprenants sur YouTube.",
            "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")
    ),
    yt(
        "Français avec Fred", "UCEuI3o4ZGS0W-aQU_T9vB5A", ResourceCategory.YOUTUBE_LESSONS, 214, null,
        d("French lessons for learners on YouTube.",
            "Cours de français pour apprenants sur YouTube.",
            "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")
    ),
    yt(
        "Le français by Alex", "UCIUS_2a-HFSHmV_QVVHWp9A", ResourceCategory.YOUTUBE_LESSONS, 215, null,
        d("French lessons for learners on YouTube.",
            "Cours de français pour apprenants sur YouTube.",
            "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")
    ),
    yt(
        "Ohlala French Course", "UCSpvTtFPjWhSHiOlH5FyVzA", ResourceCategory.YOUTUBE_LESSONS, 216, null,
        d("French lessons for learners on YouTube.",
            "Cours de français pour apprenants sur YouTube.",
            "درس‌های فرانسوی برای زبان‌آموزان در یوتیوب.")
    ),
    // News
    yt(
        "HugoDécrypte – Actus du jour", "UCAcAnMF0OrCtUep3Y4M-ZPw", ResourceCategory.YOUTUBE_NEWS, 300, "B2–C1",
        d("The day's news summed up in about ten minutes.",
            "L'actualité du jour résumée en une dizaine de minutes.",
            "خلاصهٔ خبرهای روز در حدود ده دقیقه.")
    ),
    yt(
        "HugoDécrypte – Grands formats", "UCUo1RqYV8tGjV38sQ8S5p9A", ResourceCategory.YOUTUBE_NEWS, 301, "B2–C1",
        d("Long interviews and in-depth explainers on current affairs.",
            "Longs entretiens et décryptages approfondis de l'actualité.",
            "گفت‌وگوهای بلند و تحلیل‌های عمیق دربارهٔ رویدادهای روز.")
    ),
    yt(
        "Le Monde – YouTube", "UCYpRDnhk5H8h16jpS84uqsA", ResourceCategory.YOUTUBE_NEWS, 302, "B2–C1",
        d("Video explainers from the daily Le Monde, with maps and graphics.",
            "Les vidéos explicatives du quotidien Le Monde, avec cartes et infographies.",
            "ویدیوهای توضیحی روزنامهٔ لوموند، با نقشه و نمودار.")
    ),
    yt(
        "Brut", "UCSKdvgqdnj72_SLggp7BDTg", ResourceCategory.YOUTUBE_NEWS, 303, "B1–C1",
        d("Short news and society videos with on-screen French subtitles.",
            "Courtes vidéos d'actualité et de société, sous-titrées en français à l'écran.",
            "ویدیوهای کوتاه خبری و اجتماعی با زیرنویس فرانسوی روی تصویر.")
    ),
    yt(
        "France 24", "UCCCPCZNChQdGa9EkATeye4g", ResourceCategory.YOUTUBE_NEWS, 304, "B2–C1",
        d("International news in French around the clock, with a live stream.",
            "L'actualité internationale en français 24 heures sur 24, avec un direct.",
            "خبرهای بین‌المللی به فرانسوی در تمام شبانه‌روز، همراه با پخش زنده.")
    ),
    yt(
        "TV5MONDE Info", "UCzKH70qfN_yuXq3s91fdwmg", ResourceCategory.YOUTUBE_NEWS, 305, "B2–C1",
        d("News from the French-speaking world by TV5MONDE.",
            "L'actualité du monde francophone par TV5MONDE.",
            "خبرهای جهان فرانسوی‌زبان از TV5MONDE.")
    ),
    yt(
        "franceinfo – YouTube", "UCO6K_kkdP-lnSCiO3tPx7WA", ResourceCategory.YOUTUBE_NEWS, 306, "B2–C1",
        d("News, reports and interviews from France's public broadcaster.",
            "Infos, reportages et interviews du service public français.",
            "خبر، گزارش و مصاحبه از رسانهٔ عمومی فرانسه.")
    ),
    yt(
        "euronews (en français)", "UCW2QcKZiU8aUGg4yxCIditg", ResourceCategory.YOUTUBE_NEWS, 307, "B2–C1",
        d("European and world news in short reports.",
            "L'actualité européenne et internationale en courts reportages.",
            "خبرهای اروپا و جهان در گزارش‌های کوتاه.")
    ),
    yt(
        "AFP", "UCckz6n8QccTd6K_xdwKqa0A", ResourceCategory.YOUTUBE_NEWS, 308, "B2–C1",
        d("Short news reports from the Agence France-Presse news agency.",
            "Courts reportages d'actualité de l'Agence France-Presse.",
            "گزارش‌های خبری کوتاه از خبرگزاری فرانسه.")
    ),
    yt(
        "Radio-Canada Info", "UClxaaAzHu1B5EoTtBYea7ig", ResourceCategory.YOUTUBE_NEWS, 309, "B2–C1",
        d("News from Canada's public broadcaster, in Canadian French.",
            "L'actualité du diffuseur public canadien, en français du Canada.",
            "خبرهای رسانهٔ عمومی کانادا، با فرانسوی کانادایی.")
    ),
    yt(
        "28 minutes – ARTE", "UC8EzKGkEiusTm7g0mTfkWkg", ResourceCategory.YOUTUBE_NEWS, 310, "C1–C2",
        d("ARTE's daily current-affairs programme: a guest, then a debate.",
            "Le magazine d'actualité quotidien d'ARTE : un invité, puis un débat.",
            "برنامهٔ روزانهٔ ARTE دربارهٔ رویدادهای روز: یک مهمان و سپس یک مناظره.")
    ),
    yt(
        "Public Sénat", "UCTa1bPArjpvNMgSHAb_pflA", ResourceCategory.YOUTUBE_NEWS, 311, "C1–C2",
        d("The French parliamentary channel: debates, interviews and documentaries.",
            "La chaîne parlementaire : débats, entretiens et documentaires.",
            "شبکهٔ پارلمانی فرانسه: مناظره، مصاحبه و مستند.")
    ),
    yt(
        "Le Parisien", "UCfHn_8-ehdem86fEvlFg-Gw", ResourceCategory.YOUTUBE_NEWS, 312, "B2–C1",
        d("Reports and short news videos from the daily Le Parisien.",
            "Reportages et courtes vidéos d'actualité du quotidien Le Parisien.",
            "گزارش‌ها و ویدیوهای خبری کوتاه روزنامهٔ لو پاریزین.")
    ),
    yt(
        "Le Figaro", "UCCDz_XYeKWd0OIyjp95dqyQ", ResourceCategory.YOUTUBE_NEWS, 313, "B2–C1",
        d("News videos, interviews and debates from the daily Le Figaro.",
            "Vidéos d'actualité, entretiens et débats du quotidien Le Figaro.",
            "ویدیوهای خبری، مصاحبه‌ها و مناظره‌های روزنامهٔ لو فیگارو.")
    ),
    yt(
        "TF1 INFO", "UCsrPUA0ZSDCNZC6wyRlR7ZA", ResourceCategory.YOUTUBE_NEWS, 314, "B2–C1",
        d("Reports from the TF1 television news.",
            "Les reportages des journaux télévisés de TF1.",
            "گزارش‌های اخبار تلویزیونی شبکهٔ TF1.")
    ),
    yt(
        "BFMTV", "UCXwDLMDV86ldKoFVc_g8P0g", ResourceCategory.YOUTUBE_NEWS, 315, "B2–C1",
        d("Rolling news channel: live coverage, reports and interviews.",
            "Chaîne d'information en continu : directs, reportages et interviews.",
            "شبکهٔ خبری شبانه‌روزی: پوشش زنده، گزارش و مصاحبه.")
    ),
    // Culture, history and science
    yt(
        "ARTE", "UCwI-JbGNsojunnHbFAc0M4Q", ResourceCategory.YOUTUBE_CULTURE, 400, "B2–C1",
        d("Documentaries and reports on culture, society, history and science.",
            "Documentaires et reportages sur la culture, la société, l'histoire et les sciences.",
            "مستندها و گزارش‌هایی دربارهٔ فرهنگ، جامعه، تاریخ و علم.")
    ),
    yt(
        "Le Dessous des Cartes – ARTE", "UCHGMBrXUzClgjEzBMei-Jdw", ResourceCategory.YOUTUBE_CULTURE, 401, "B2–C1",
        d("Geopolitics explained with maps, in short episodes.",
            "La géopolitique expliquée par les cartes, en épisodes courts.",
            "ژئوپلیتیک به کمک نقشه‌ها، در قسمت‌های کوتاه.")
    ),
    yt(
        "Karambolage – ARTE", "UCh5JF-qt2ZXg8acejcJ15Xw", ResourceCategory.YOUTUBE_CULTURE, 402, "B1–B2",
        d("Short animated pieces on everyday habits, objects and words in France and Germany.",
            "Courtes séquences animées sur les habitudes, objets et mots du quotidien en France et en Allemagne.",
            "قطعه‌های کوتاه انیمیشنی دربارهٔ عادت‌ها، اشیا و واژه‌های روزمره در فرانسه و آلمان.")
    ),
    yt(
        "Nota Bene", "UCP46_MXP_WG_auH88FnfS1A", ResourceCategory.YOUTUBE_CULTURE, 403, "B2–C1",
        d("History told in an accessible, lively way.",
            "L'histoire racontée de façon accessible et vivante.",
            "تاریخ به زبانی ساده و پرشور.")
    ),
    yt(
        "Axolot", "UC2_OG1L8DLTzQ7UrZVOk7OA", ResourceCategory.YOUTUBE_CULTURE, 404, "B2–C1",
        d("Strange and astonishing true stories from history, science and nature.",
            "Histoires vraies, étranges et étonnantes, tirées de l'histoire, des sciences et de la nature.",
            "داستان‌های واقعی عجیب و شگفت‌انگیز از تاریخ، علم و طبیعت.")
    ),
    yt(
        "France Culture", "UCd5DKToXYTKAQ6khzewww2g", ResourceCategory.YOUTUBE_CULTURE, 405, "C1–C2",
        d("Interviews, ideas and short explainers from the public cultural radio station.",
            "Entretiens, idées et courtes vidéos explicatives de la radio culturelle publique.",
            "گفت‌وگو، اندیشه و ویدیوهای توضیحی کوتاه از رادیوی فرهنگی عمومی فرانسه.")
    ),
    yt(
        "Tout Simplement – Kurzgesagt", "UCzMHLUJ8xTqDq6xsk0N5IdQ", ResourceCategory.YOUTUBE_CULTURE, 406, "B2–C1",
        d("Animated science explainers; the French version of Kurzgesagt.",
            "Vidéos de vulgarisation scientifique animées ; la version française de Kurzgesagt.",
            "ویدیوهای انیمیشنی علمی؛ نسخهٔ فرانسوی Kurzgesagt.")
    ),
    yt(
        "Jamy – Epicurieux", "UCG9G2dyRv04FDSH1FSYuLBg", ResourceCategory.YOUTUBE_CULTURE, 407, "B1–B2",
        d("Jamy answers everyday science questions simply and clearly.",
            "Jamy répond simplement et clairement aux questions de science du quotidien.",
            "ژامی به پرسش‌های علمی روزمره ساده و روشن پاسخ می‌دهد.")
    ),
    yt(
        "ScienceEtonnante", "UCaNlbnghtwlsGF-KzAFThqA", ResourceCategory.YOUTUBE_CULTURE, 408, "C1–C2",
        d("Physics, mathematics and other sciences explained in depth.",
            "La physique, les mathématiques et d'autres sciences expliquées en profondeur.",
            "توضیح عمیق فیزیک، ریاضی و علوم دیگر.")
    ),
    yt(
        "e-penser 2.0", "UCcziTK2NKeWtWQ6kB5tmQ8Q", ResourceCategory.YOUTUBE_CULTURE, 409, "B2–C1",
        d("Science explained with humour.",
            "La science expliquée avec humour.",
            "علم به زبان طنز.")
    ),
    yt(
        "DirtyBiology", "UCtqICqGbPSbTN09K1_7VZ3Q", ResourceCategory.YOUTUBE_CULTURE, 410, "B2–C1",
        d("Biology and evolution explained with humour.",
            "La biologie et l'évolution expliquées avec humour.",
            "زیست‌شناسی و تکامل به زبان طنز.")
    ),
    yt(
        "C'est une autre histoire", "UCKjDY4joMPcoRMmd-G1yz1Q", ResourceCategory.YOUTUBE_CULTURE, 411, "B2–C1",
        d("History, art and mythology told with humour.",
            "L'histoire, l'art et la mythologie racontés avec humour.",
            "تاریخ، هنر و اسطوره‌شناسی به زبان طنز.")
    ),
    yt(
        "L'Histoire nous le dira", "UCN4TCCaX-gqBNkrUqXdgGRA", ResourceCategory.YOUTUBE_CULTURE, 412, "B2–C1",
        d("History explained by a Québec historian, in Canadian French.",
            "L'histoire expliquée par un historien québécois, en français du Canada.",
            "تاریخ به روایت یک تاریخ‌دان کبکی، با فرانسوی کانادایی.")
    ),
    yt(
        "Le Précepteur", "UCvRgiAmogg7a_BgQ_Ftm6fA", ResourceCategory.YOUTUBE_CULTURE, 413, "C1–C2",
        d("Philosophy explained: major thinkers and their ideas.",
            "La philosophie expliquée : les grands penseurs et leurs idées.",
            "فلسفه به زبان ساده: اندیشمندان بزرگ و ایده‌هایشان.")
    ),
    yt(
        "Stupid Economics", "UCyJDHgrsUKuWLe05GvC2lng", ResourceCategory.YOUTUBE_CULTURE, 414, "B2–C1",
        d("Economics made understandable through concrete questions.",
            "L'économie rendue compréhensible à partir de questions concrètes.",
            "اقتصاد به زبان قابل‌فهم، با پرسش‌های ملموس.")
    ),
    yt(
        "Linguisticae", "UCofQxJWd4qkqc7ZgaLkZfcw", ResourceCategory.YOUTUBE_CULTURE, 415, "B2–C1",
        d("Linguistics for everyone: the history of French, accents, spelling and the world's languages.",
            "La linguistique pour tous : histoire du français, accents, orthographe et langues du monde.",
            "زبان‌شناسی برای همه: تاریخ زبان فرانسه، لهجه‌ها، املا و زبان‌های جهان.")
    ),
    yt(
        "Dr Nozman", "UCWnfDPdZw6A23UtuBpYBbAg", ResourceCategory.YOUTUBE_CULTURE, 416, "B1–B2",
        d("Science experiments and curious facts.",
            "Expériences scientifiques et faits étonnants.",
            "آزمایش‌های علمی و دانستنی‌های جالب.")
    ),
    yt(
        "Mamytwink", "UCjxfoYq1vAEGU7KD_C3I48g", ResourceCategory.YOUTUBE_CULTURE, 417, "B2–C1",
        d("History documentaries and exploration of abandoned and historic sites.",
            "Documentaires d'histoire et exploration de lieux abandonnés et historiques.",
            "مستندهای تاریخی و کاوش در مکان‌های متروک و تاریخی.")
    ),
    yt(
        "Bruno Maltor", "UCSdJiWi0tq9CAx1bizne6mA", ResourceCategory.YOUTUBE_CULTURE, 418, "B1–B2",
        d("Travel vlogs in France and around the world.",
            "Vlogs de voyage en France et autour du monde.",
            "ولاگ‌های سفر در فرانسه و سراسر جهان.")
    ),
    yt(
        "imineo Documentaires", "UCJ9tHJbCa8ZoMbapfJLy0ZQ", ResourceCategory.YOUTUBE_CULTURE, 419, "B2–C1",
        d("Full-length documentaries on nature, history, travel and society.",
            "Documentaires complets sur la nature, l'histoire, le voyage et la société.",
            "مستندهای کامل دربارهٔ طبیعت، تاریخ، سفر و جامعه.")
    ),
    yt(
        "Documentaire Société", "UCYFI5PGuw_34XaA3SGDkQOQ", ResourceCategory.YOUTUBE_CULTURE, 420, "B2–C1",
        d("Full-length documentaries and reports on French society.",
            "Documentaires et reportages complets sur la société française.",
            "مستندها و گزارش‌های کامل دربارهٔ جامعهٔ فرانسه.")
    ),
    // Cartoons
    yt(
        "Peppa Pig Français", "UCXptamDYEVcU4JCio30hYTw", ResourceCategory.YOUTUBE_KIDS, 500, "A1–A2",
        d("Short cartoon episodes with simple, clearly spoken everyday French.",
            "Courts épisodes de dessin animé, en français simple et bien articulé.",
            "قسمت‌های کوتاه کارتونی با فرانسوی سادهٔ روزمره و تلفظ واضح.")
    ),
    yt(
        "Caillou Français", "UCBwSqx6A83sNQ5SIYWpmucQ", ResourceCategory.YOUTUBE_KIDS, 501, "A1–A2",
        d("A cartoon about a little boy's daily life, with simple sentences.",
            "Un dessin animé sur le quotidien d'un petit garçon, avec des phrases simples.",
            "کارتونی دربارهٔ زندگی روزمرهٔ یک پسربچه، با جمله‌های ساده.")
    ),
    yt(
        "Masha et Michka", "UCjd32KVfRCli1d9iqo4YZ5A", ResourceCategory.YOUTUBE_KIDS, 502, "A1–A2",
        d("A cartoon with short, simple dialogues in French.",
            "Un dessin animé aux dialogues courts et simples, en français.",
            "کارتونی با گفت‌وگوهای کوتاه و ساده به فرانسوی.")
    ),
    yt(
        "Titounis", "UC8I-UIlXPNS4luC4iV7dRdQ", ResourceCategory.YOUTUBE_KIDS, 503, "A1–A2",
        d("Nursery rhymes and children's songs with easy-to-follow lyrics.",
            "Comptines et chansons pour enfants, aux paroles faciles à suivre.",
            "ترانه‌ها و شعرهای کودکانه با متن آسان.")
    ),
    yt(
        "Miraculous – Ladybug et Chat Noir", "UCIpBLlcyR1W6oEji4_qdrQA", ResourceCategory.YOUTUBE_KIDS, 504, "A2–B1",
        d("A French animated series with natural dialogue; a step up from preschool cartoons.",
            "Une série d'animation française aux dialogues naturels ; un cran au-dessus des dessins animés pour tout-petits.",
            "یک مجموعهٔ انیمیشن فرانسوی با گفت‌وگوهای طبیعی؛ یک پله بالاتر از کارتون‌های خردسالان.")
    )
)
