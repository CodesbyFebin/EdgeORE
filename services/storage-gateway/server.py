"""EdgeORE owned-host ciphertext gateway. HTTPS must be terminated by the owner."""
import hashlib
import hmac
import json
import os
import re
import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BYTES = 8_001_024
ID = re.compile(r'[A-Za-z0-9_-]{1,80}')
DIGEST = re.compile(r'[0-9a-f]{64}')

class Store:
    def __init__(self, path):
        self.path = path
        with self.connect() as db:
            db.execute('CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, digest TEXT NOT NULL, body BLOB NOT NULL, canceled INTEGER NOT NULL DEFAULT 0)')
    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        try:
            db.execute('PRAGMA synchronous=FULL')
            with db:
                yield db
        finally:
            db.close()
    def put(self, job, body, digest):
        if not ID.fullmatch(job) or not DIGEST.fullmatch(digest):
            raise ValueError('Invalid identifier or digest')
        if len(body) > MAX_BYTES or hashlib.sha256(body).hexdigest() != digest:
            raise ValueError('Ciphertext length or digest refused')
        with self.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            old = db.execute('SELECT digest, canceled FROM jobs WHERE id=?', (job,)).fetchone()
            if old:
                if old[0] != digest or old[1]:
                    raise ValueError('Job identifier already used or canceled')
                return False
            db.execute('INSERT INTO jobs(id,digest,body) VALUES(?,?,?)', (job, digest, body))
            return True
    def status(self, job):
        with self.connect() as db:
            row = db.execute('SELECT digest,canceled FROM jobs WHERE id=?', (job,)).fetchone()
            return None if row is None else {'state': 'canceled' if row[1] else 'acknowledged', 'remoteRef': row[0], 'ciphertextSha256': row[0], 'observedAt': datetime.now(timezone.utc).isoformat()}
    def cancel(self, job):
        with self.connect() as db:
            return db.execute('UPDATE jobs SET canceled=1 WHERE id=?', (job,)).rowcount == 1
    def get(self, digest):
        with self.connect() as db:
            row = db.execute('SELECT body FROM jobs WHERE digest=? AND canceled=0 LIMIT 1', (digest,)).fetchone()
            return None if row is None else row[0]

class Handler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args):
        pass  # Do not log bearer credentials, object references or request bodies.
    def reply(self, code, value, binary=False):
        body = value if binary else json.dumps(value).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/octet-stream' if binary else 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Connection', 'close')
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True
    def route(self):
        self.connection.settimeout(20)
        auth = self.headers.get('Authorization', '')
        if not hmac.compare_digest(auth, 'Bearer ' + self.server.token):
            return self.reply(401, {'error': 'Unauthorized'})
        parts = self.path.split('/')
        if len(parts) == 4 and parts[:3] == ['', 'v1', 'objects'] and DIGEST.fullmatch(parts[3]) and self.command == 'GET':
            body = self.server.store.get(parts[3])
            return self.reply(404, {'error': 'Not found'}) if body is None else self.reply(200, body, True)
        if len(parts) not in (4, 5) or parts[:3] != ['', 'v1', 'jobs'] or not ID.fullmatch(parts[3]):
            return self.reply(404, {'error': 'Unknown route'})
        job = parts[3]
        if self.command == 'POST' and len(parts) == 5 and parts[4] == 'cancel':
            ok = self.server.store.cancel(job)
            return self.reply(200 if ok else 404, {'canceled': ok})
        if len(parts) != 4:
            return self.reply(404, {'error': 'Unknown route'})
        if self.command == 'GET':
            status = self.server.store.status(job)
            return self.reply(404, {'state': 'not_found'}) if status is None else self.reply(200, status)
        if self.command != 'PUT':
            return self.reply(405, {'error': 'Method refused'})
        length = self.headers.get('Content-Length', '')
        if self.headers.get('Transfer-Encoding') or not length.isdigit() or int(length) > MAX_BYTES:
            return self.reply(413, {'error': 'Bounded Content-Length required'})
        body = self.rfile.read(int(length))
        if len(body) != int(length):
            return self.reply(400, {'error': 'Truncated upload'})
        try:
            self.server.store.put(job, body, self.headers.get('X-Ciphertext-SHA256', ''))
        except ValueError as e:
            return self.reply(409, {'error': str(e)})
        return self.reply(200, self.server.store.status(job))
    def do_GET(self): self.route()
    def do_PUT(self): self.route()
    def do_POST(self): self.route()

if __name__ == '__main__':
    token = os.environ.get('EDGEORE_STORAGE_TOKEN', '')
    if len(token) < 32:
        raise SystemExit('Set EDGEORE_STORAGE_TOKEN to a random secret of at least 32 characters')
    server = ThreadingHTTPServer(('127.0.0.1', 19844), Handler)
    server.token = token
    server.store = Store(os.environ.get('EDGEORE_STORAGE_DB', 'storage.sqlite3'))
    server.serve_forever()
