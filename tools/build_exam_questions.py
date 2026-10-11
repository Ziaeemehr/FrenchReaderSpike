#!/usr/bin/env python3
"""Compile the frequent-question exports in content/exams/<exam>/ to one JSON asset.

Each export is a text file per task (EE_Tache_1_<date>.txt, EO_Tache_3_<date>.txt, ...) listing
subjects with how often candidates reported them and in which months. Re-run after dropping in a
newer export; when several dates exist for a task, the newest wins.
"""
import argparse
import hashlib
import json
import re
import sys
import unicodedata
from pathlib import Path

MONTHS = {'janvier': 1, 'fevrier': 2, 'mars': 3, 'avril': 4, 'mai': 5, 'juin': 6, 'juillet': 7,
          'aout': 8, 'septembre': 9, 'octobre': 10, 'novembre': 11, 'decembre': 12}
FILE = re.compile(r'^(EE|EO)_Tache_([123])_(\d{4})(\d{2})(\d{2})\.txt$')
HEADER = re.compile(r'^\s*#(\d+)\s*\|\s*Frequency:\s*(\d+)\s*\|\s*Months:\s*(.*)$')
TITLE = re.compile(r'Tâche \d\s*:\s*(.+)$')
# "Sujet (FR):", "ترجمه فارسی:", "Document 1 (FR):", "Document 2 (FA):"
FIELD = re.compile(r'^\s*(?:(Sujet) \(FR\)|(ترجمه فارسی)|Document ([12]) \((FR|FA)\)):\s*$')
RULE = re.compile(r'^[─=]{20,}\s*$')
# Cloudflare replaces e-mail addresses with this text when a page is scraped.
EMAIL = '[email\xa0protected]'


def plain(text):
    return ''.join(c for c in unicodedata.normalize('NFD', text.lower()) if unicodedata.category(c) != 'Mn')


def month(label):
    name, year = plain(label).split()
    return f'{int(year):04d}-{MONTHS[name]:02d}'


def clean(lines):
    text = ' '.join(' '.join(line.split()) for line in lines if line.strip())
    text = text.replace(EMAIL, '…@…').replace('[email protected]', '…@…')
    # Leftovers of the export: a leading arrow or full stop before the first word.
    return re.sub(r'^[→.]\s+', '', text).strip()


def parse(path):
    title, items, current, field = None, [], None, None
    for line in path.read_text(encoding='utf-8').splitlines():
        if title is None and TITLE.search(line):
            title = TITLE.search(line).group(1).strip()
        header = HEADER.match(line)
        if header:
            current = {'n': int(header.group(1)), 'count': int(header.group(2)),
                       'months': sorted({month(m) for m in header.group(3).split('|') if m.strip()}),
                       'fields': {}}
            items.append(current)
            field = None
            continue
        if RULE.match(line):
            field = None
            continue
        marker = FIELD.match(line)
        if marker and current is not None:
            sujet, fa, doc, lang = marker.groups()
            field = 'fr' if sujet else 'fa' if fa else f'doc{doc}_{lang.lower()}'
            current['fields'][field] = []
        elif field and current is not None:
            current['fields'][field].append(line)
    return title, items


def build(source_dir):
    newest = {}
    for path in sorted(source_dir.glob('*.txt')):
        match = FILE.match(path.name)
        if not match:
            raise ValueError(f'{path.name}: unexpected file name')
        skill, task, y, m, d = match.groups()
        key = (skill, int(task))
        date = f'{y}-{m}-{d}'
        if key not in newest or date > newest[key][0]:
            newest[key] = (date, path)
    tasks, warnings = [], []
    for (skill, task), (date, path) in sorted(newest.items()):
        title, raw = parse(path)
        prefix = f'{skill.lower()}{task}'
        seen, items = {}, []
        for entry in raw:
            f = {k: clean(v) for k, v in entry['fields'].items()}
            where = f'{path.name} #{entry["n"]}'
            if 'fr' in f:
                texts = [{'fr': f['fr'], 'fa': f.get('fa', '')}]
            else:
                texts = [{'fr': f.get(f'doc{i}_fr', ''), 'fa': f.get(f'doc{i}_fa', '')} for i in (1, 2)]
            if not all(t['fr'] for t in texts):
                raise ValueError(f'{where}: missing French text')
            key = plain(' '.join(t['fr'] for t in texts))
            item_id = prefix + '-' + hashlib.sha1(key.encode()).hexdigest()[:10]
            if item_id in seen:
                warnings.append(f'{where}: same subject as #{seen[item_id]}, counts merged')
                kept = next(i for i in items if i['id'] == item_id)
                kept['count'] += entry['count']
                kept['months'] = sorted(set(kept['months']) | set(entry['months']))
                continue
            seen[item_id] = entry['n']
            for t in texts:
                if not t['fa']:
                    warnings.append(f'{where}: no Persian translation')
                elif len(t['fa']) < 0.45 * len(t['fr']):
                    warnings.append(f'{where}: Persian translation looks cut short')
                foreign = [w for w in re.findall(r'[A-Za-zÀ-ÿ]{5,}', t['fa']) if plain(w) not in plain(t['fr'])]
                if foreign:
                    warnings.append(f'{where}: untranslated word(s) in Persian: {", ".join(foreign)}')
            if not entry['months']:
                raise ValueError(f'{where}: no months')
            items.append({'id': item_id, 'count': entry['count'], 'months': entry['months'], 'texts': texts})
        if items:
            # Most reported first; among equals, the most recently seen first.
            items.sort(key=lambda i: i['months'][-1], reverse=True)
            items.sort(key=lambda i: -i['count'])
            tasks.append({'id': prefix, 'skill': skill, 'task': task, 'titleFr': title or '',
                          'exportedOn': date, 'items': items})
    return tasks, warnings


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exam', default='tcf')
    parser.add_argument('--variant', default='TCF Canada')
    parser.add_argument('--source-title', default='french-tcf.com')
    parser.add_argument('--source-url', default='https://french-tcf.com')
    parser.add_argument('--check', action='store_true', help='fail if the asset is out of date')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    try:
        tasks, warnings = build(root / 'content' / 'exams' / args.exam)
    except ValueError as error:
        print(f'error: {error}', file=sys.stderr)
        sys.exit(1)
    output = json.dumps({
        'version': 1, 'exam': args.exam, 'variant': args.variant,
        'source': {'title': args.source_title, 'url': args.source_url},
        'tasks': tasks,
    }, ensure_ascii=False, separators=(',', ':')) + '\n'
    target = root / 'app' / 'src' / 'main' / 'assets' / 'exams' / f'{args.exam}-questions.json'
    # The Home screen only needs the total; this tiny file saves it from parsing every subject.
    meta_target = target.with_name(f'{args.exam}-questions-meta.json')
    meta = json.dumps({'total': sum(len(t['items']) for t in tasks),
                       'tasks': {t['id']: len(t['items']) for t in tasks}}) + '\n'
    for warning in warnings:
        print(f'warning: {warning}', file=sys.stderr)
    if args.check:
        if (not target.exists() or target.read_text(encoding='utf-8') != output
                or not meta_target.exists() or meta_target.read_text(encoding='utf-8') != meta):
            print(f'{target} is out of date; run tools/build_exam_questions.py', file=sys.stderr)
            sys.exit(1)
        return
    target.write_text(output, encoding='utf-8')
    meta_target.write_text(meta, encoding='utf-8')
    print(f'{target.relative_to(root)}: ' + ', '.join(f'{t["id"]} {len(t["items"])}' for t in tasks))


if __name__ == '__main__':
    main()
