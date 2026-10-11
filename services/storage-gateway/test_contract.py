"""Contract test: openapi.yaml and server.py must agree on routes, status codes, patterns and size limits.

openapi.yaml is written in YAML's JSON-compatible syntax, so the standard library reads it.
Every status code the spec declares for an operation must be produced by a real request here, and every status code
a request here produces must be declared. Undeclared methods and paths must never succeed.
"""
import hashlib
import http.client
import json
import socket
import tempfile
import threading
import time
import unittest
from http.server import ThreadingHTTPServer
from pathlib import Path

import server
from server import Handler, Store

SPEC = json.loads((Path(__file__).with_name('openapi.yaml')).read_text())
TOKEN = 'c' * 40
AUTH = {'Authorization': 'Bearer ' + TOKEN}


def resolve(node):
    while isinstance(node, dict) and '$ref' in node:
        cur = SPEC
        for part in node['$ref'].lstrip('#/').split('/'):
            cur = cur[part]
        node = cur
    return node


class ContractTests(unittest.TestCase):
    observed = {}  # (method, template) -> set of status codes produced

    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.srv = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        cls.srv.token = TOKEN
        cls.srv.store = Store(str(Path(cls.tmp.name) / 'db.sqlite3'), max_total_bytes=64)
        cls.srv.slots = threading.BoundedSemaphore(4)
        cls.thread = threading.Thread(target=cls.srv.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown(); cls.srv.server_close(); cls.thread.join(); cls.tmp.cleanup()

    def call(self, method, path, template, body=None, headers=None):
        c = http.client.HTTPConnection('127.0.0.1', self.srv.server_port, timeout=5)
        c.request(method, path, body, headers or {})
        r = c.getresponse(); data = r.read(); c.close()
        if template:
            self.observed.setdefault((method.lower(), template), set()).add(str(r.status))
        return r.status, data

    def raw(self, payload, template, method):
        s = socket.create_connection(('127.0.0.1', self.srv.server_port), timeout=5)
        s.sendall(payload); s.shutdown(socket.SHUT_WR)
        data = s.recv(4096); s.close()
        code = data.split(b' ', 2)[1].decode()
        self.observed.setdefault((method, template), set()).add(code)
        return int(code)

    def busy(self, method, path, template):
        """Fill every connection slot, then make one more request: it must get 503."""
        held = []
        try:
            while self.srv.slots._value:
                held.append(socket.create_connection(('127.0.0.1', self.srv.server_port)))
                time.sleep(0.05)
            return self.call(method, path, template, headers=AUTH)[0]
        finally:
            for h in held: h.close()
            time.sleep(0.2)

    def test_1_every_operation_and_status(self):
        body = b'contract fixture'
        d = hashlib.sha256(body).hexdigest()
        put = dict(AUTH, **{'X-Ciphertext-SHA256': d})
        J, C, O, K = '/v1/jobs/{jobId}', '/v1/jobs/{jobId}/cancel', '/v1/objects/{sha256}', '/v1/capabilities'
        # capabilities
        self.call('GET', '/v1/capabilities', K, headers=AUTH)
        self.call('GET', '/v1/capabilities', K)
        self.busy('GET', '/v1/capabilities', K)
        # put
        self.assertEqual(self.call('PUT', '/v1/jobs/c1', J, body, put)[0], 200)
        self.call('PUT', '/v1/jobs/c1', J, b'other', dict(AUTH, **{'X-Ciphertext-SHA256': hashlib.sha256(b'other').hexdigest()}))  # 409 reuse
        self.call('PUT', '/v1/jobs/c2', J, b'x' * 60, dict(AUTH, **{'X-Ciphertext-SHA256': hashlib.sha256(b'x' * 60).hexdigest()}))  # 409 quota
        self.call('PUT', '/v1/jobs/c1', J, body)  # 401
        self.raw(b'PUT /v1/jobs/c3 HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer ' + TOKEN.encode() + b'\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n\r\n', J, 'put')  # 413
        self.raw(b'PUT /v1/jobs/c3 HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer ' + TOKEN.encode() + b'\r\nContent-Length: ' + str(server.MAX_BYTES + 1).encode() + b'\r\n\r\n', J, 'put')  # 413
        self.raw(b'PUT /v1/jobs/c3 HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer ' + TOKEN.encode() + b'\r\nX-Ciphertext-SHA256: ' + d.encode() + b'\r\nContent-Length: 10\r\n\r\nshort', J, 'put')  # 400
        self.busy('PUT', '/v1/jobs/c1', J)
        # get job
        self.call('GET', '/v1/jobs/c1', J, headers=AUTH)
        self.call('GET', '/v1/jobs/nope', J, headers=AUTH)
        self.call('GET', '/v1/jobs/c1', J)
        self.busy('GET', '/v1/jobs/c1', J)
        # post on job path
        self.call('POST', '/v1/jobs/c1', J, b'', AUTH)
        self.call('POST', '/v1/jobs/c1', J, b'')
        self.busy('POST', '/v1/jobs/c1', J)
        # objects
        self.call('GET', '/v1/objects/' + d, O, headers=AUTH)
        self.call('GET', '/v1/objects/' + 'f' * 64, O, headers=AUTH)
        self.call('GET', '/v1/objects/' + d, O)
        self.busy('GET', '/v1/objects/' + d, O)
        # cancel
        self.call('POST', '/v1/jobs/c1/cancel', C, b'', AUTH)
        self.call('POST', '/v1/jobs/never/cancel', C, b'', AUTH)
        self.call('POST', '/v1/jobs/c1/cancel', C, b'')
        self.busy('POST', '/v1/jobs/c1/cancel', C)

        declared = {(m, p): set(op['responses']) for p, item in SPEC['paths'].items() for m, op in item.items() if m != 'parameters'}
        self.assertEqual(set(self.observed), set(declared), 'operations exercised must be exactly the spec operations')
        for key, codes in declared.items():
            self.assertEqual(self.observed[key], codes, f'{key}: produced vs declared status codes')

    def test_2_undeclared_routes_and_methods_never_succeed(self):
        for method, path in [('GET', '/v1/jobs'), ('GET', '/v1/jobs/a/b'), ('PUT', '/v1/objects/' + 'a' * 64), ('GET', '/v1/objects/XYZ'),
                             ('GET', '/v1/jobs/../x'), ('GET', '/v1/capabilities?x=1'), ('DELETE', '/v1/jobs/c1'), ('PATCH', '/v1/jobs/c1'),
                             ('HEAD', '/v1/capabilities'), ('GET', '/'), ('GET', '/v2/capabilities')]:
            code, _ = self.call(method, path, None, headers=AUTH)
            self.assertGreaterEqual(code, 400, f'{method} {path} must not succeed')

    def test_3_limits_and_patterns_match_the_server(self):
        comp = SPEC['components']
        self.assertEqual(comp['parameters']['ContentLength']['schema']['maximum'], server.MAX_BYTES)
        self.assertEqual(SPEC['paths']['/v1/jobs/{jobId}']['put']['requestBody']['content']['application/octet-stream']['schema']['maxLength'], server.MAX_BYTES)
        self.assertEqual(comp['schemas']['Capabilities']['properties']['maxObjectBytes']['const'], server.MAX_BYTES)
        self.assertEqual(comp['parameters']['JobId']['schema']['pattern'], '^' + server.ID.pattern + '$')
        self.assertEqual(comp['schemas']['Digest']['pattern'], '^' + server.DIGEST.pattern + '$')

    def test_4_response_bodies_match_schemas(self):
        body = b'schema fixture'
        d = hashlib.sha256(body).hexdigest()
        _, caps = self.call('GET', '/v1/capabilities', None, headers=AUTH)
        caps = json.loads(caps)
        schema = SPEC['components']['schemas']['Capabilities']
        self.assertEqual(set(caps), set(schema['properties']))
        for k, v in schema['properties'].items():
            if 'const' in v: self.assertEqual(caps[k], v['const'], k)
        _, st = self.call('PUT', '/v1/jobs/s1', None, body, dict(AUTH, **{'X-Ciphertext-SHA256': d}))
        st = json.loads(st)
        js = SPEC['components']['schemas']['JobStatus']
        self.assertEqual(set(st), set(js['properties']))
        self.assertIn(st['state'], js['properties']['state']['enum'])
        _, nf = self.call('GET', '/v1/jobs/none', None, headers=AUTH)
        self.assertEqual(json.loads(nf), {'state': 'not_found'})
        _, e = self.call('GET', '/v1/jobs/x', None)
        self.assertEqual(set(json.loads(e)), {'error'})


if __name__ == '__main__':
    unittest.main()
