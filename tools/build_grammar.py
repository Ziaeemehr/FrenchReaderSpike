#!/usr/bin/env python3
"""Compile the deliberately small grammar Markdown format; no YAML dependency."""
import argparse
import json
import re
import sys
from pathlib import Path

GROUPS = {'basics', 'tenses', 'pronouns', 'structures', 'comparisons'}
REQUIRED = {'summary', 'usage', 'structure', 'examples'}
SECTIONS = REQUIRED | {'mistake', 'more', 'infographic'}


def scalar(value):
    value = value.strip()
    if value.startswith('[') and value.endswith(']'):
        # Only double quotes group an item: French items are full of apostrophes (j'en ai, qu'il).
        return [scalar(v) for v in re.split(r',(?=(?:[^"]|"[^"]*")*$)', value[1:-1]) if v.strip()]
    if value.startswith('"'):
        return json.loads(value)
    if value.startswith("'") and value.endswith("'"):
        return value[1:-1].replace("''", "'")
    return None if value == 'null' else value


def highlighted(value):
    parts = value.split('**')
    if len(parts) % 2 == 0:
        raise ValueError('unbalanced ** markers')
    text, ranges, offset = '', [], 0
    for i, part in enumerate(parts):
        end = offset + len(part.encode('utf-16-le')) // 2
        if i % 2:
            ranges.append([offset, end])
        text += part
        offset = end
    return {'text': text, 'highlights': ranges}


def prose(body):
    result, paragraph = [], []
    def flush():
        if paragraph:
            result.append({'kind': 'p', 'text': ' '.join(paragraph)})
            paragraph.clear()
    for line in body.splitlines():
        line = line.strip()
        if not line:
            flush()
        elif line.startswith('- '):
            flush()
            result.append({'kind': 'li', 'text': line[2:]})
        else:
            paragraph.append(line)
    flush()
    return result


def parse_note(path):
    source = path.read_text(encoding='utf-8')
    match = re.match(r'\A---\s*\n(.*?)\n---\s*\n(.*)\Z', source, re.S)
    if not match:
        raise ValueError('missing front matter')
    meta = {}
    for line in match[1].splitlines():
        key, separator, value = line.partition(':')
        if not separator:
            raise ValueError('invalid front matter line: ' + line)
        meta[key.strip()] = scalar(value)
    if meta.get('id') != path.stem:
        raise ValueError('id must equal file name')
    if meta.get('group') not in GROUPS:
        raise ValueError('unknown group: ' + str(meta.get('group')))
    chunks = re.split(r'^## (\w+)\s*$', match[2], flags=re.M)
    names = chunks[1::2]
    if REQUIRED - set(names):
        raise ValueError('missing required section: ' + ', '.join(sorted(REQUIRED - set(names))))
    if len(names) != len(set(names)) or set(names) - SECTIONS:
        raise ValueError('duplicate or unknown section')
    blocks = []
    for name, body in zip(names, chunks[2::2]):
        highlighted(body)  # Validate markers even in prose and unpublished notes.
        block = {'type': name}
        if name in {'summary', 'usage', 'more'}:
            block['markdown'] = prose(body)
        elif name == 'structure':
            patterns = re.findall(r'^`([^`]+)`\s*$', body, re.M)
            block.update(patterns=patterns, text=prose(re.sub(r'^`[^`]+`\s*$', '', body, flags=re.M)))
        elif name == 'examples':
            items = []
            examples = re.split(r'^### (\S+)\s*$', body, flags=re.M)
            for example_id, lines in zip(examples[1::2], examples[2::2]):
                fields = dict(re.findall(r'^(FR|FA):\s*(.+)$', lines, re.M))
                if not fields.get('FR') or not fields.get('FA'):
                    raise ValueError('example ' + example_id + ' missing FR or FA')
                cell = highlighted(fields['FR'])
                items.append(dict(id=example_id, fr=cell['text'], fa=fields['FA'], highlights=cell['highlights']))
            if not items:
                raise ValueError('examples section has no examples')
            block['items'] = items
        elif name == 'mistake':
            fields = dict(re.findall(r'^(WRONG|RIGHT|WHY):\s*(.+)$', body, re.M))
            block.update({key.lower(): fields[key] for key in ('WRONG', 'RIGHT', 'WHY')})
        else:
            kind = re.search(r'^type:\s*(\w+)', body, re.M)
            if not kind or kind[1] not in {'comparison', 'timeline', 'decision', 'steps'}:
                raise ValueError('invalid infographic type')
            table = [[highlighted(cell.strip()) for cell in line.strip()[1:-1].split('|')]
                     for line in body.splitlines() if line.strip().startswith('|')]
            if len(table) < 2:
                raise ValueError('infographic missing table header')
            if any(len(row) != len(table[0]) for row in table[1:]):
                raise ValueError('table row cell count differs from header')
            block.update(kind=kind[1], header=table[0], rows=table[2:])
        blocks.append(block)
    note = {key: meta[key] for key in ('id', 'group', 'level')}
    for source_key, target in [('title_fr', 'titleFr'), ('title_fa', 'titleFa'), ('aliases_fr', 'aliasesFr'), ('aliases_fa', 'aliasesFa'), ('related', 'related')]:
        note[target] = meta[source_key]
    for key in ('aliasesFr', 'aliasesFa'):
        # A comma inside an alias means the list was not split into separate items.
        if any(',' in alias or '،' in alias for alias in note[key]):
            raise ValueError(f'{path.name}: alias list in {key} was not split correctly')
    note['blocks'] = blocks
    return note, meta.get('status')


def compile_notes(directory):
    notes, ids = [], set()
    for path in sorted(directory.glob('*.md')):
        try:
            note, status = parse_note(path)
            if note['id'] in ids:
                raise ValueError('duplicate id: ' + note['id'])
            ids.add(note['id'])
            notes.append((note, status))
        except (ValueError, KeyError) as error:
            raise ValueError(f'{path.name}: {error}') from error
    for note, _ in notes:
        for related in note['related']:
            if related not in ids:
                raise ValueError(f"{note['id']}: related id does not exist: {related}")
    return {'version': 1, 'notes': sorted([n for n, s in notes if s == 'reviewed'], key=lambda n: (n['group'], n['id']))}


def self_check():
    assert highlighted('😀 **été**') == {'text': '😀 été', 'highlights': [[3, 6]]}
    assert prose('a\nb\n\n- c') == [{'kind': 'p', 'text': 'a b'}, {'kind': 'li', 'text': 'c'}]
    assert scalar('["a, b", c]') == ['a, b', 'c']
    try:
        highlighted('**bad')
    except ValueError:
        pass
    else:
        raise AssertionError('unbalanced markers accepted')


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--notes', type=Path, default=root / 'content/grammar/notes')
    parser.add_argument('--output', type=Path, default=root / 'app/src/main/assets/grammar/notes.json')
    parser.add_argument('--self-check', action='store_true')
    args = parser.parse_args()
    try:
        if args.self_check:
            self_check()
        payload = compile_notes(args.notes)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(f"Generated {len(payload['notes'])} reviewed notes: {args.output}")
    except (ValueError, AssertionError) as error:
        print(f'Grammar validation failed: {error}', file=sys.stderr)
        sys.exit(1)
