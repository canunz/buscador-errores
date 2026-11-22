"""Local identity tests: synthetic /proc snapshots, no service or database calls."""
import copy
import unittest
from unittest.mock import patch
import thread_check as check


def qa_snapshot():
    root = '/run/credentials/' + check.QA
    return dict(pid=4321, start_ticks=123456, invocation='a' * 32,
                active='active', unit_user='dimabug-wa-pilot', uids=(991, 991, 991, 991), expected_uid=991,
                exe='/usr/lib/jvm/java-21-openjdk-amd64/bin/java',
                expected_exe='/usr/lib/jvm/java-21-openjdk-amd64/bin/java',
                args=[b'/usr/bin/java', b'-Xmx192m', b'-jar',
                      b'/opt/dimabug-wa-pilot/whatsapp-qa.jar',
                      ('--spring.config.location=file:' + root + '/application.properties').encode(),
                      ('--spring.config.import=configtree:' + root + '/').encode()],
                group='/system.slice/dimabug-wa-pilot.service',
                cgroup='0::/system.slice/dimabug-wa-pilot.service\n')


class ThreadIdentityTest(unittest.TestCase):
    def test_exec_during_proc_read_is_never_accepted(self):
        changing = qa_snapshot(); changing['stable'] = False
        with self.assertRaisesRegex(RuntimeError, 'QA_PROCESS_EXEC_TRANSITION'):
            check.validate_identity(changing)
        snaps = iter([changing, qa_snapshot()])
        self.assertEqual(check.wait_for_java(snapshot=lambda: next(snaps), clock=lambda: 0,
                                            sleep=lambda _: None).pid, 4321)

    def test_unstable_foreign_unit_is_not_retried(self):
        s = qa_snapshot(); s.update(stable=False, unit_user='staging')
        with patch.object(check.time, 'sleep') as sleep:
            with self.assertRaisesRegex(RuntimeError, 'QA_SYSTEMD_USER_MISMATCH'):
                check.wait_for_java(snapshot=lambda: s, sleep=sleep)
            sleep.assert_not_called()

    def test_status_uids_uses_all_four_credentials(self):
        self.assertEqual(check.status_uids('Name:\tjava\nUid:\t991\t991\t991\t991\nGid:\t991\n'),
                         (991, 991, 991, 991))

    def test_rejects_malformed_uid_status(self):
        for status in ('', 'Uid: 991 991', 'Uid: root root root root',
                       'Uid: 991 991 991 991\nUid: 991 991 991 991'):
            with self.subTest(status=status), self.assertRaises(RuntimeError):
                check.status_uids(status)

    def test_rejects_any_real_effective_saved_or_filesystem_uid_mismatch(self):
        for index in range(4):
            s = qa_snapshot(); uids = list(s['uids']); uids[index] = 0; s['uids'] = tuple(uids)
            with self.subTest(index=index), self.assertRaisesRegex(RuntimeError, 'QA_UID_IDENTITY_MISMATCH'):
                check.validate_identity(s)

    def test_preexec_root_to_service_transition_never_accepts_root(self):
        early = qa_snapshot(); early.update(exe='/usr/lib/systemd/systemd', args=[b'(java)'],
                                           uids=(0, 0, 0, 0))
        mixed = copy.deepcopy(early); mixed['uids'] = (991, 0, 991, 0)
        snaps = iter([early, mixed, qa_snapshot()])
        accepted = check.wait_for_java(snapshot=lambda: next(snaps), clock=lambda: 0, sleep=lambda _: None)
        self.assertEqual(accepted, check.validate_identity(qa_snapshot()))
        with self.assertRaisesRegex(RuntimeError, 'QA_UID_IDENTITY_MISMATCH'):
            check.validate_identity(early)

    def test_preexec_cannot_switch_pid_or_invocation(self):
        for key, value in [('pid', 5555), ('start_ticks', 9999), ('invocation', 'b'*32)]:
            early = qa_snapshot(); early.update(exe='/usr/lib/systemd/systemd', args=[b'(java)'])
            later = qa_snapshot(); later[key] = value
            snaps = iter([early, later])
            with self.subTest(key=key), self.assertRaisesRegex(RuntimeError, 'QA_PID_CHANGED_OR_ABSENT'):
                check.wait_for_java(snapshot=lambda: next(snaps), clock=lambda: 0, sleep=lambda _: None)

    def test_accepts_exact_java_qa(self):
        self.assertEqual(check.validate_identity(qa_snapshot()),
                         check.ProcessIdentity(4321, 123456, 'a' * 32))

    def test_accepts_same_process_again(self):
        s = qa_snapshot()
        self.assertEqual(check.validate_identity(s, check.validate_identity(s)).pid, 4321)

    def test_rejects_foreign_and_changed_processes(self):
        cases = dict(pid=0, active='failed', invocation='', unit_user='staging', uids=(992, 992, 992, 992),
                     exe='/bin/sleep', group='', cgroup='0::/system.slice/dimabug@staging.service\n')
        for key, value in cases.items():
            with self.subTest(key=key):
                s = qa_snapshot(); s[key] = value
                with self.assertRaises(RuntimeError):
                    check.validate_identity(s)

    def test_rejects_pid_reuse_restart_and_invocation_change(self):
        initial = check.validate_identity(qa_snapshot())
        for key, value in [('pid', 4322), ('start_ticks', 123457), ('invocation', 'b' * 32)]:
            with self.subTest(key=key):
                s = qa_snapshot(); s[key] = value
                with self.assertRaisesRegex(RuntimeError, 'QA_PID_CHANGED_OR_ABSENT'):
                    check.validate_identity(s, initial)

    def test_rejects_jar_only_mentioned_as_property(self):
        s = qa_snapshot()
        s['args'] = [b'/usr/bin/java', b'-Dlabel=/opt/dimabug-wa-pilot/whatsapp-qa.jar',
                     b'-jar', b'/opt/staging/app.jar']
        with self.assertRaisesRegex(RuntimeError, 'QA_JAR_IDENTITY_MISMATCH'):
            check.validate_identity(s)

    def test_rejects_wrong_jar_even_if_expected_path_is_another_argument(self):
        s = qa_snapshot()
        s['args'][3] = b'/opt/staging/app.jar'
        s['args'].append(b'/opt/dimabug-wa-pilot/whatsapp-qa.jar')
        with self.assertRaisesRegex(RuntimeError, 'QA_JAR_IDENTITY_MISMATCH'):
            check.validate_identity(s)

    def test_rejects_duplicate_jar_switch(self):
        s = qa_snapshot(); s['args'] += [b'-jar', b'/opt/staging/app.jar']
        with self.assertRaises(RuntimeError):
            check.validate_identity(s)

    def test_rejects_wrong_config_missing_config_and_extra_overrides(self):
        for args in ([b'/usr/bin/java', b'-jar', b'/opt/dimabug-wa-pilot/whatsapp-qa.jar'],
                     qa_snapshot()['args'][:-1] + [b'--spring.config.import=configtree:/etc/staging/'],
                     qa_snapshot()['args'] + [b'--dimabug.whatsapp.meta-qa.envio-real=true']):
            with self.subTest(args_length=len(args)):
                s = qa_snapshot(); s['args'] = args
                with self.assertRaisesRegex(RuntimeError, 'QA_CONFIG_ARGUMENTS_MISMATCH'):
                    check.validate_identity(s)

    def test_rejects_empty_cmdline(self):
        s = qa_snapshot(); s['args'] = []
        with self.assertRaises(RuntimeError):
            check.validate_identity(s)

    def test_rejects_cgroup_suffix_or_child_trick(self):
        for group in ('0::/foreign' + qa_snapshot()['group'],
                      qa_snapshot()['cgroup'].strip() + '/child'):
            s = qa_snapshot(); s['cgroup'] = group
            with self.assertRaisesRegex(RuntimeError, 'QA_CGROUP_IDENTITY_MISMATCH'):
                check.validate_identity(s)

    def test_preexec_is_not_accepted_but_java_after_exec_is(self):
        early = qa_snapshot(); early.update(exe='/usr/lib/systemd/systemd', args=[b'(java)'])
        with self.assertRaises(RuntimeError):
            check.validate_identity(early)
        snapshots = iter([early, qa_snapshot()])
        sleeps = []
        result = check.wait_for_java(snapshot=lambda: next(snapshots), clock=lambda: 0,
                                     sleep=sleeps.append)
        self.assertEqual(result.pid, 4321)
        self.assertEqual(sleeps, [0.1])

    def test_preexec_wait_is_bounded(self):
        early = qa_snapshot(); early.update(exe='/usr/lib/systemd/systemd', args=[b'(java)'])
        times = iter([0, 0, 3])
        with self.assertRaisesRegex(RuntimeError, 'QA_JAVA_EXECUTABLE_MISMATCH'):
            check.wait_for_java(snapshot=lambda: copy.deepcopy(early), clock=lambda: next(times),
                                sleep=lambda _: None)

    def test_does_not_retry_foreign_java_or_wrong_jar(self):
        for key, value in [('exe', '/foreign/java'), ('args', [b'/usr/bin/java', b'-jar', b'/foreign.jar'])]:
            s = qa_snapshot(); s[key] = value
            with patch.object(check.time, 'sleep') as sleep:
                with self.assertRaises(RuntimeError):
                    check.wait_for_java(snapshot=lambda: s, sleep=sleep)
                sleep.assert_not_called()

    def test_preexec_in_foreign_cgroup_is_rejected_without_retry(self):
        s = qa_snapshot(); s.update(exe='/usr/lib/systemd/systemd', args=[b'(java)'],
                                   cgroup='0::/system.slice/staging.service')
        with patch.object(check.time, 'sleep') as sleep:
            with self.assertRaisesRegex(RuntimeError, 'QA_CGROUP_IDENTITY_MISMATCH'):
                check.wait_for_java(snapshot=lambda: s, sleep=sleep)
            sleep.assert_not_called()


if __name__ == '__main__':
    unittest.main()
