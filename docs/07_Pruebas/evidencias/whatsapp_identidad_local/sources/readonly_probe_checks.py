"""Checks of proposed read-only probe; no SSH and no live systemd or /proc calls."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

# Import definitions on Windows as well; no pwd lookup occurs in stopped-process tests.
if sys.platform == 'win32':
    sys.modules.setdefault('pwd', types.SimpleNamespace())
spec = importlib.util.spec_from_file_location('readonly_probe', Path(__file__).with_name('inspect_identity_readonly.py'))
probe = importlib.util.module_from_spec(spec); spec.loader.exec_module(probe)

class ReadOnlyProbeChecks(unittest.TestCase):
    def test_nonroot_exits_before_any_command(self):
        with patch.object(probe.os, 'geteuid', return_value=1000, create=True), patch.object(probe.subprocess, 'check_output') as command:
            with self.assertRaises(SystemExit):
                probe.main()
            command.assert_not_called()

    def test_unit_lookup_is_only_systemctl_show(self):
        with patch.object(probe.subprocess, 'check_output', return_value='MainPID=0\n') as command:
            self.assertEqual(probe.read_unit()['MainPID'], '0')
            args = command.call_args.args[0]
            self.assertEqual(args[:3], ['systemctl', 'show', probe.UNIT])
            self.assertTrue(all(a.startswith('--property=') for a in args[3:]))

    def test_stopped_process_never_reads_proc_or_claims_a_cause(self):
        fixture = dict(MainPID='0', User=probe.USER, Type='simple', ActiveState='inactive',
                       ExecStart='{ path=/usr/bin/java ; argv[]=/usr/bin/java -jar /opt/dimabug-wa-pilot/whatsapp-qa.jar ; }',
                       Environment='PRIVATE_SENTINEL')
        with tempfile.TemporaryDirectory() as directory, patch.object(probe, 'ROOT', Path(directory)), \
             patch.object(probe.os, 'geteuid', return_value=0, create=True), \
             patch.object(probe, 'read_unit', side_effect=lambda: dict(fixture)), \
             patch.object(probe.subprocess, 'check_output') as command:
            out = io.StringIO()
            with contextlib.redirect_stdout(out):
                probe.main()
            data = json.loads(out.getvalue())
            self.assertEqual(data['samples'], [{'result': 'NOT_OBSERVABLE_QA_STOPPED', 'pid_present': False}])
            self.assertNotIn('PRIVATE_SENTINEL', out.getvalue())
            command.assert_not_called()

    def test_status_output_uses_explicit_allowlist(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(probe, 'ROOT', Path(directory)):
            (Path(directory) / 'thread-status.json').write_text(json.dumps(dict(
                status='FAIL', error_code='QA_UID_IDENTITY_MISMATCH', token='PRIVATE_SENTINEL',
                captures=[], qa_stopped=True, qa_account_locked=True)))
            data = probe.safe_status()
            self.assertEqual(data['error_code'], 'QA_UID_IDENTITY_MISMATCH')
            self.assertNotIn('PRIVATE_SENTINEL', json.dumps(data))

if __name__ == '__main__':
    unittest.main(verbosity=2)
