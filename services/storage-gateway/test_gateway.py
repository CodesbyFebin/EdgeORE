import hashlib
import tempfile
import unittest
from pathlib import Path
from server import Store, MAX_BYTES

class GatewayTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.path = str(Path(self.tmp.name) / 'objects.sqlite3')
        self.store = Store(self.path)
        self.body = b'opaque test ciphertext'
        self.digest = hashlib.sha256(self.body).hexdigest()
    def tearDown(self): self.tmp.cleanup()
    def test_store_survives_restart(self):
        self.store.put('job-1', self.body, self.digest)
        self.assertEqual(Store(self.path).get(self.digest), self.body)
    def test_repeated_upload_is_idempotent(self):
        self.assertTrue(self.store.put('job-1', self.body, self.digest))
        self.assertFalse(self.store.put('job-1', self.body, self.digest))
    def test_reusing_job_for_different_content_fails(self):
        self.store.put('job-1', self.body, self.digest)
        with self.assertRaises(ValueError): self.store.put('job-1', b'other', hashlib.sha256(b'other').hexdigest())
    def test_modified_content_refused(self):
        with self.assertRaises(ValueError): self.store.put('job-1', self.body + b'X', self.digest)
        self.assertIsNone(self.store.status('job-1'))
    def test_cancel_blocks_retrieval_and_reupload(self):
        self.store.put('job-1', self.body, self.digest)
        self.assertTrue(self.store.cancel('job-1'))
        self.assertIsNone(self.store.get(self.digest))
        with self.assertRaises(ValueError): self.store.put('job-1', self.body, self.digest)
    def test_quota_is_transactional_and_counts_retained_canceled_data(self):
        limited = Store(self.path, max_total_bytes=len(self.body))
        limited.put('job-1', self.body, self.digest)
        self.assertFalse(limited.put('job-1', self.body, self.digest))
        limited.cancel('job-1')
        with self.assertRaises(ValueError): limited.put('job-2', self.body, self.digest)
        self.assertIsNone(limited.status('job-2'))
    def test_quota_holds_under_concurrent_uploads(self):
        import threading
        limited = Store(self.path, max_total_bytes=10)
        results = []
        def upload(i):
            body = bytes([i]) * 10
            try: results.append(limited.put(f'job-{i}', body, hashlib.sha256(body).hexdigest()))
            except ValueError: results.append('refused')
        threads = [threading.Thread(target=upload, args=(i,)) for i in range(8)]
        for t in threads: t.start()
        for t in threads: t.join()
        self.assertEqual(results.count(True), 1)
        self.assertEqual(results.count('refused'), 7)
    def test_bounds_and_identifier(self):
        with self.assertRaises(ValueError): self.store.put('../outside', self.body, self.digest)
        large = b'X' * (MAX_BYTES + 1)
        with self.assertRaises(ValueError): self.store.put('job-1', large, hashlib.sha256(large).hexdigest())


class HttpTests(unittest.TestCase):
    def setUp(self):
        import threading
        from http.server import ThreadingHTTPServer
        from server import Handler
        self.tmp = tempfile.TemporaryDirectory()
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.server.token = 'a' * 40
        self.server.store = Store(str(Path(self.tmp.name) / 'db.sqlite3'))
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()
        self.tmp.cleanup()
    def request(self, method, path, body=None, headers=None):
        import http.client
        c = http.client.HTTPConnection('127.0.0.1', self.server.server_port, timeout=3)
        c.request(method, path, body, headers or {})
        r = c.getresponse()
        result = r.status, r.read()
        c.close()
        return result
    def test_capability_discovery_does_not_claim_pinning_or_payments(self):
        import json
        code, body = self.request('GET','/v1/capabilities',headers={'Authorization':'Bearer '+self.server.token})
        self.assertEqual(code,200)
        caps = json.loads(body)
        self.assertFalse(caps['pinning'])
        self.assertEqual(caps['settlement'],'not_integrated')
    def test_connection_cap_refuses_extra_connections(self):
        import socket, threading
        self.server.slots = threading.BoundedSemaphore(1)
        idle = socket.create_connection(('127.0.0.1', self.server.server_port))
        try:
            import time
            for _ in range(50):
                if self.server.slots._value == 0: break
                time.sleep(0.02)
            self.assertEqual(self.request('GET', '/v1/capabilities')[0], 503)
        finally:
            idle.close()
    def test_idle_connection_is_closed_by_timeout(self):
        import socket
        from server import Handler
        self.assertTrue(Handler.timeout is not None and 0 < Handler.timeout <= 60)  # the default is bounded
        # Shorten it for the test only; the mechanism is the same.
        self.server.RequestHandlerClass = type('ShortTimeout', (self.server.RequestHandlerClass,), {'timeout': 0.5})
        idle = socket.create_connection(('127.0.0.1', self.server.server_port))
        idle.settimeout(5)
        try:
            self.assertEqual(idle.recv(1), b'')  # server closed it without a request
        finally:
            idle.close()
    def test_auth_required(self):
        self.assertEqual(self.request('GET', '/v1/jobs/job')[0], 401)
    def test_upload_observe_retrieve_cancel(self):
        import json
        body = b'ciphertext fixture'
        digest = hashlib.sha256(body).hexdigest()
        headers = {'Authorization': 'Bearer ' + self.server.token, 'X-Ciphertext-SHA256': digest}
        code, data = self.request('PUT', '/v1/jobs/job', body, headers)
        self.assertEqual(code, 200)
        self.assertEqual(json.loads(data)['state'], 'acknowledged')
        self.assertEqual(self.request('GET', '/v1/objects/' + digest, headers=headers), (200, body))
        self.assertEqual(self.request('POST', '/v1/jobs/job/cancel', headers=headers)[0], 200)
        self.assertEqual(self.request('GET', '/v1/objects/' + digest, headers=headers)[0], 404)

if __name__ == '__main__': unittest.main()
