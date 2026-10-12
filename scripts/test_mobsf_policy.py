import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location('policy', Path(__file__).with_name('mobsf-policy.py'))
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)
CLEAR = 'Domain config is insecurely configured to permit clear text traffic to these domains in scope'
CBC = 'The App uses the encryption mode CBC with PKCS5/PKCS7 padding. This configuration is vulnerable to padding oracle attacks.'


class PolicyTest(unittest.TestCase):
    def evaluate(self, title, config=None, files=None):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'network.xml'
            path.write_text(config or '<network-security-config><base-config cleartextTrafficPermitted="false"/><domain-config cleartextTrafficPermitted="true"><domain includeSubdomains="false">127.0.0.1</domain></domain-config></network-security-config>')
            report = {'code_analysis': {'findings': {'cbc_padding_oracle': {'files': files or {}}}}}
            return policy.evaluate(report, {'high': [{'title': title}]}, path)

    def test_loopback_is_explicitly_reviewed(self):
        self.assertEqual(0, self.evaluate(CLEAR))

    def test_remote_cleartext_fails(self):
        config = '<network-security-config><base-config cleartextTrafficPermitted="false"/><domain-config cleartextTrafficPermitted="true"><domain includeSubdomains="false">example.com</domain></domain-config></network-security-config>'
        self.assertEqual(1, self.evaluate(CLEAR, config))

    def test_biometric_helper_is_bounded(self):
        self.assertEqual(0, self.evaluate(CBC, files={'androidx/biometric/CryptoObjectUtils.java': '1'}))
        self.assertEqual(1, self.evaluate(CBC, files={'com/edgeore/app/LocalVault.java': '1'}))
        self.assertEqual(1, self.evaluate(CBC))

    def test_unsigned_apk_and_unknown_findings_fail(self):
        self.assertEqual(1, self.evaluate('Missing Code Signing certificate'))
        self.assertEqual(1, self.evaluate('New vulnerability'))

    def test_missing_scorecard_is_error(self):
        with self.assertRaises(ValueError):
            policy.evaluate({}, {}, 'unused')


if __name__ == '__main__':
    unittest.main()
