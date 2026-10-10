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
