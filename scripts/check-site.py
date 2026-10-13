#!/usr/bin/env python3
"""Buildless website checks: crawlability, structured-data identity and asset links."""
import json
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import urlparse
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SITE = ROOT / 'docs/site'
CANONICAL = 'https://codesbyfebin.github.io/EdgeORE/'

class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.tags = []
        self.schema = ''
        self.in_schema = False
        self.h1 = 0
    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        self.tags.append((tag, attrs))
        self.h1 += tag == 'h1'
        if tag == 'script':
            assert attrs.get('type') == 'application/ld+json', 'No tracking/runtime scripts expected'
            self.in_schema = True
    def handle_endtag(self, tag):
        if tag == 'script': self.in_schema = False
    def handle_data(self, data):
        if self.in_schema: self.schema += data

page = Page()
page.feed((SITE / 'index.html').read_text())
assert page.h1 == 1
assert any(t == 'html' and a.get('lang') == 'en' for t, a in page.tags)
assert [a['href'] for t, a in page.tags if t == 'link' and a.get('rel') == 'canonical'] == [CANONICAL]
assert any(t == 'meta' and a.get('name') == 'description' and 80 <= len(a.get('content','')) <= 170 for t, a in page.tags)
ids = {a['id'] for _, a in page.tags if 'id' in a}
for tag, attrs in page.tags:
    if tag == 'img':
        assert all(k in attrs for k in ('alt', 'width', 'height'))
    for key in ('href', 'src'):
        target = attrs.get(key, '')
        if not target: continue
        if target.startswith('#'): assert target[1:] in ids, target
        elif not urlparse(target).scheme:
            assert (SITE / target).is_file(), target
        else: assert target.startswith('https://'), target
schema = json.loads(page.schema)
assert schema['@context'] == 'https://schema.org'
graph = schema['@graph']
assert len({node['@id'] for node in graph}) == len(graph)
assert all(node['@id'].startswith(CANONICAL + '#') for node in graph)
webpage = next(node for node in graph if 'FAQPage' in node['@type'])
assert len(webpage['mainEntity']) == 7
assert all(q['acceptedAnswer']['text'] in (SITE/'index.html').read_text() for q in webpage['mainEntity'])
assert 'aggregateRating' not in page.schema and 'reviewRating' not in page.schema
sitemap = ET.parse(SITE/'sitemap.xml')
assert [e.text for e in sitemap.findall('.//{*}loc')] == [CANONICAL]
for alias in ['index.html','docs/index.html','docs/judges/index.html']:
    text = (ROOT/alias).read_text()
    assert 'noindex,follow' in text and CANONICAL in text
print('PASS: canonical, metadata, schema/visible FAQ, sitemap, assets, headings and aliases')
