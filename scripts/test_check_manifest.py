#!/usr/bin/env python3
"""Regression tests for scripts/check-manifest.py (run: python3 scripts/test_check_manifest.py)."""
import importlib.util, os, tempfile, unittest, io, contextlib

spec = importlib.util.spec_from_file_location("check_manifest", os.path.join(os.path.dirname(__file__), "check-manifest.py"))
cm = importlib.util.module_from_spec(spec); spec.loader.exec_module(cm)

HEAD = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.edgeore.app"><application{dbg}>'
TAIL = '</application></manifest>'
MAIN = '<activity android:name="com.edgeore.app.MainActivity" android:exported="true"/>'
PROFILE = '<receiver android:name="androidx.profileinstaller.ProfileInstallReceiver" android:exported="true" android:permission="android.permission.DUMP"/>'


def run(body, dbg=""):
    with tempfile.NamedTemporaryFile("w", suffix=".xml", delete=False) as f:
        f.write(HEAD.format(dbg=dbg) + body + TAIL)
    try:
        with contextlib.redirect_stdout(io.StringIO()):
            return cm.main(["check-manifest.py", f.name])
    finally:
        os.unlink(f.name)


class CheckManifestTest(unittest.TestCase):
    def test_release_shape_passes(self):
        self.assertEqual(0, run(MAIN + PROFILE + '<provider android:name="androidx.core.content.FileProvider" android:exported="false"/>'))

    def test_debuggable_fails(self):
        self.assertEqual(1, run(MAIN, dbg=' android:debuggable="true"'))

    def test_exported_test_activity_fails(self):
        self.assertEqual(1, run(MAIN + '<activity android:name="androidx.test.core.app.InstrumentationActivityInvoker$EmptyActivity" android:exported="true"/>'))

    def test_unexported_tooling_activity_still_fails(self):
        self.assertEqual(1, run(MAIN + '<activity android:name="androidx.compose.ui.tooling.PreviewActivity" android:exported="false"/>'))

    def test_unknown_exported_component_fails(self):
        self.assertEqual(1, run(MAIN + '<service android:name="com.example.Leak" android:exported="true"/>'))

    def test_unreadable_input_is_tool_error(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, cm.main(["check-manifest.py", "/nonexistent/AndroidManifest.xml"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
