import importlib.util
import tempfile
import shutil
import unittest
from pathlib import Path
spec = importlib.util.spec_from_file_location('policy', Path(__file__).with_name('build-policy.py'))
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)

class PolicyTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        files = ['gradle/libs.versions.toml','docs/build/dependency-baseline.json','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties','build.gradle.kts','app/build.gradle.kts','ondevice-llm/build.gradle.kts','settings.gradle.kts']
        for name in files:
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(policy.ROOT / name, target)
    def tearDown(self): self.tmp.cleanup()
    def change(self, path, old, new):
        p = self.root / path
        p.write_text(p.read_text().replace(old, new))
    def test_current_declarations_match(self): self.assertEqual(policy.check(self.root), [])
    def test_version_change_fails(self):
        self.change('gradle/libs.versions.toml','version = "2.0.3"','version = "2.0.4"')
        self.assertTrue(policy.check(self.root))
    def test_wrong_scope_fails(self):
        self.change('ondevice-llm/build.gradle.kts','runtimeOnly(', 'implementation(')
        self.assertTrue(policy.check(self.root))
    def test_wrapper_mutation_fails(self):
        with (self.root / 'gradle/wrapper/gradle-wrapper.jar').open('ab') as f: f.write(b'X')
        self.assertTrue(policy.check(self.root))
    def test_credentials_in_buildconfig_fail(self):
        with (self.root / 'app/build.gradle.kts').open('a') as f: f.write('\nbuildConfigField("String", "API_TOKEN", "value")\n')
        self.assertTrue(policy.check(self.root))
    def test_dynamic_version_fails(self):
        self.change('gradle/libs.versions.toml','version = "2.0.3"','version = "2.+"')
        self.assertTrue(policy.check(self.root))
    # Added in review: gaps the original six tests did not cover.
    def add_dependency(self, line):
        self.change('app/build.gradle.kts', 'dependencies {', 'dependencies {\n    ' + line, )
    def test_new_dependency_in_unlisted_configuration_fails(self):
        self.add_dependency('testRuntimeOnly("com.example:unreviewed:1.0")')
        self.assertTrue(policy.check(self.root))
    def test_new_dependency_through_kapt_fails(self):
        self.add_dependency('kapt("com.example:unreviewed:1.0")')
        self.assertTrue(policy.check(self.root))
    def test_removed_project_dependency_fails(self):
        self.change('app/build.gradle.kts', 'implementation(project(":ondevice-llm"))', '')
        self.assertTrue(policy.check(self.root))
    def test_removed_dependency_exclusion_fails(self):
        self.change('ondevice-llm/build.gradle.kts', 'exclude(group = "com.google.errorprone", module = "error_prone_annotations")', '')
        self.assertTrue(policy.check(self.root))
    def test_api_key_buildconfig_field_fails(self):
        with (self.root / 'app/build.gradle.kts').open('a') as f: f.write('\nbuildConfigField("String", "MAPS_API_KEY", "value")\n')
        self.assertTrue(policy.check(self.root))
    def test_added_repository_fails(self):
        self.change('settings.gradle.kts', 'repositories { google(); mavenCentral() }', 'repositories { google(); mavenCentral(); maven("https://jitpack.io") }')
        self.assertTrue(policy.check(self.root))

if __name__ == '__main__': unittest.main()
