# Fixed French quality-rubric prompt set (spike, Priority 5)

Fixed set per ROADMAP.md section 9, "پروتکل spike دستیار محلی": used to judge French
output quality of a given model/runtime combination. Same texts + same task prompts
run against every candidate so results are comparable. This file is data for the
spike, not app content — do not wire it into the app.

Each of the 3 source texts below is run through all 5 tasks = 15 generations per
model/runtime candidate. Judged by a fluent-French reader (Claude, in this spike)
against the pass/fail notes under each task.

## Source text A — news style (B1), ~140 words

> Le gouvernement a annoncé mardi un nouveau plan pour encourager les transports en
> commun dans les grandes villes françaises. Ce plan prévoit la construction de
> nouvelles lignes de tramway à Lyon, Nantes et Strasbourg d'ici 2029, ainsi qu'une
> baisse du prix des abonnements pour les étudiants et les personnes âgées. Selon le
> ministre des Transports, l'objectif est de réduire la pollution en ville et de
> convaincre davantage d'automobilistes de laisser leur voiture au garage. Les
> associations écologistes ont salué cette décision, tout en regrettant qu'elle
> n'aille pas assez loin. De leur côté, plusieurs maires ont demandé des précisions
> sur le financement du projet, qui devrait coûter plusieurs milliards d'euros.

## Source text B — educational/Vikidia style (A2–B1), ~120 words

> Le Mont-Blanc est la plus haute montagne d'Europe occidentale. Il se trouve à la
> frontière entre la France et l'Italie, dans les Alpes. Son sommet mesure environ
> 4 810 mètres. Beaucoup d'alpinistes viennent chaque année pour essayer de
> l'escalader, mais ce n'est pas sans danger : le froid, le vent et les crevasses
> rendent l'ascension difficile. La première personne à atteindre le sommet fut
> Jacques Balmat, en 1786, avec le médecin Michel Paccard. Aujourd'hui, la montagne
> attire aussi des touristes qui préfèrent simplement admirer le paysage depuis le
> téléphérique de l'Aiguille du Midi, sans grimper jusqu'en haut.

## Source text C — short narrative (B2), ~160 words

> Quand Sophie est arrivée à Marseille, elle ne connaissait personne. Elle avait
> quitté son petit village de Bretagne pour suivre des études de médecine, et la
> ville lui semblait immense et bruyante. Les premières semaines furent difficiles :
> elle se perdait souvent dans les ruelles du Vieux-Port et avait du mal à comprendre
> l'accent chantant de ses nouveaux voisins. Pourtant, peu à peu, elle se lia
> d'amitié avec Karim, un étudiant en pharmacie qui partageait son appartement, et
> avec Élise, une bibliothécaire passionnée d'histoire locale. Un soir, alors qu'ils
> regardaient le coucher de soleil sur la mer depuis les hauteurs de Notre-Dame de la
> Garde, Sophie réalisa qu'elle commençait, enfin, à se sentir chez elle.

## Task prompts (run each against A, B, and C)

1. **Résumé** — "Résume ce texte en 2 phrases maximum, en français."
   Pass: faithful, ≤2 sentences, no invented facts. Fail: hallucinated detail not in
   source, or summary longer than source.

2. **Explication grammaticale** — "Choisis une phrase de ce texte contenant un temps
   verbal composé (passé composé, plus-que-parfait, etc.) et explique en français
   simple pourquoi ce temps est utilisé ici."
   Pass: correctly identifies an actual compound tense present in the text and gives
   a coherent (even if simple) explanation. Fail: cites a sentence/tense not in the
   text, or grammatically wrong explanation.

3. **Simplification CEFR** — "Réécris ce texte pour un niveau A2, avec des phrases
   courtes et un vocabulaire simple."
   Pass: meaning preserved, noticeably shorter/simpler sentences and vocabulary vs.
   source. Fail: meaning drift, or output not meaningfully simpler than source.

4. **Question de compréhension** — "Crée une question à choix multiple (4 réponses,
   une seule correcte) sur ce texte, en français."
   Pass: valid well-formed MCQ, exactly one answer correct and verifiable from the
   text. Fail: no unambiguous correct answer, or answer not supported by the text.

5. **Extraction JSON structurée** — "Extrais les 5 mots ou expressions les plus
   difficiles de ce texte pour un apprenant de niveau B1, sous forme de JSON avec les
   clés `mot` et `definition_simple` (définition en français simple)."
   Pass: valid parseable JSON, exactly the requested shape, words actually appear in
   the source text. Fail: invalid JSON, wrong keys, or invented words not in the
   source.

## Recording results

For each (model/runtime, text, task) triple, record: pass/fail per the criteria
above, generation time, and the raw output. Append to
`docs/superpowers/spikes/llm-assistant-spike/qualitative-results.md`.
