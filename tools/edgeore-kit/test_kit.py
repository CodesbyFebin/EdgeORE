import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from edgeore_kit import consent_bytes, plan, verify_bundle, strict_json

class ConsentTests(unittest.TestCase):
    def setUp(self):
        self.c = {'version':1,'workload':'ai.inference','destination':'on-device','dataCategories':['document','prompt'],'maxBytes':100,'maxSeconds':30,'retentionSeconds':0,'expiresAt':1000,'revocationId':'consent-1'}
    def test_canonical_order(self):
        self.assertEqual(consent_bytes(self.c), consent_bytes(dict(reversed(list(self.c.items())))))
    def test_meaning_change_changes_digest(self):
        self.assertNotEqual(consent_bytes(self.c),consent_bytes(dict(self.c,maxBytes=99)))
    def test_unknown_fields_refused(self):
        with self.assertRaises(ValueError): consent_bytes(dict(self.c, secret='x'))
    def test_bool_not_integer(self):
        with self.assertRaises(ValueError): consent_bytes(dict(self.c,maxBytes=True))
    def test_unqualified_default_refused(self):
        self.assertIn('RUNTIME_NOT_QUALIFIED',plan(self.c,'ai.inference',1,requested_seconds=1)['reasons'])
    def test_review_not_execution(self):
        r=plan(self.c,'ai.inference',1,qualified=True,available=True,requested_seconds=1)
        self.assertEqual(r['decision'],'ELIGIBLE_FOR_REVIEW');self.assertEqual(r['execution'],'NOT_RUN')
    def test_expiration_revocation_budget(self):
        r=plan(self.c,'ai.inference',1000,revoked=True,requested_bytes=101,requested_seconds=31)
        for code in ['CONSENT_EXPIRED','CONSENT_REVOKED','BUDGET_EXCEEDED']:self.assertIn(code,r['reasons'])
    def test_string_flags_refused(self):
        with self.assertRaises(ValueError):plan(self.c,'ai.inference',1,qualified='true',requested_seconds=1)
    def test_origin_credentials_refused(self):
        r=plan(dict(self.c,destination='https://secret@example.com'),'ai.inference',1,requested_seconds=1)
        self.assertIn('INVALID_DESTINATION',r['reasons'])
    def test_no_cloud_fallback(self):
        r=plan(dict(self.c,destination='https://example.com'),'ai.inference',1,requested_seconds=1)
        self.assertIn('DESTINATION_NOT_ALLOWED',r['reasons'])

class EvidenceTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.root=Path(self.tmp.name)
        (self.root/'log.txt').write_bytes(b'fixture log')
        self.doc={'version':1,'sourceCommit':'a'*40,'environment':'unit fixture, not a device','files':[{'path':'log.txt','sha256':hashlib.sha256(b'fixture log').hexdigest(),'bytes':11}]}
        self.save()
    def tearDown(self):self.tmp.cleanup()
    def save(self):(self.root/'manifest.json').write_text(json.dumps(self.doc))
    def test_unsigned_integrity_only(self):
        self.assertEqual(verify_bundle(self.root)['authorship'],'NOT_VERIFIED')
    def test_modified_artifact_fails(self):
        (self.root/'log.txt').write_bytes(b'fixture loX')
        with self.assertRaises(ValueError):verify_bundle(self.root)
    def test_traversal_refused(self):
        self.doc['files'][0]['path']='../log.txt';self.save()
        with self.assertRaises(ValueError):verify_bundle(self.root)
    def test_duplicate_artifact_refused(self):
        self.doc['files']*=2;self.save()
        with self.assertRaises(ValueError):verify_bundle(self.root)
    def test_symlink_refused(self):
        (self.root/'link.txt').symlink_to(self.root/'log.txt');self.doc['files'][0]['path']='link.txt';self.save()
        with self.assertRaises(ValueError):verify_bundle(self.root)
    def test_duplicate_json_refused(self):
        with self.assertRaises(ValueError):strict_json('{"version":1,"version":2}')

if __name__=='__main__':unittest.main()
