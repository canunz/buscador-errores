"""Security regression checks in memory; no Docker, SQL or credentials."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('proxy_checks', HERE / 'verify_proxy_preparation.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ProxyIsolationTest(unittest.TestCase):
    def setUp(self):
        self.data = json.loads((HERE / 'compose.qa-proxy.json').read_text())

    def test_accepts_prepared_definition(self):
        MODULE.validate(self.data)

    def test_host_rejection_is_pending_not_authenticated(self):
        packet = b'\x03\x00\x00\x00\xff' + (1130).to_bytes(2, 'little')
        self.assertEqual(MODULE.classify_greeting(packet), 'AUTH_PENDING')

    def test_rejects_other_errors_truncated_packets_and_wrong_version(self):
        for packet in [b'', b'\x00'*5,
                       b'\x03\x00\x00\x00\xff'+(1045).to_bytes(2, 'little'),
                       b'\x08\x00\x00\x00\x0a8.0.36\0']:
            with self.subTest(packet_length=len(packet)), self.assertRaises(ValueError):
                MODULE.classify_greeting(packet)

    def test_expected_server_greeting(self):
        self.assertEqual(MODULE.classify_greeting(b'\x08\x00\x00\x00\x0a8.0.46\0'), 'GREETING_VERIFIED')

    def test_rejects_security_boundary_changes(self):
        mutations = [
            ('mysql', 'networks', ['qa-internal', 'qa-access']),
            ('mysql', 'ports', ['127.0.0.1:13318:3306']),
            ('mysql-proxy', 'ports', ['0.0.0.0:13318:13318']),
            ('mysql-proxy', 'secrets', ['mysql_root_password']),
            ('mysql-proxy', 'volumes', ['/var/run/docker.sock:/var/run/docker.sock']),
            ('mysql-proxy', 'user', '0:0'),
            ('mysql-proxy', 'cap_drop', []),
            ('mysql-proxy', 'read_only', False),
            ('mysql-proxy', 'image', 'haproxy:latest'),
            ('mysql-proxy', 'platform', 'linux/arm64'),
            ('mysql-proxy', 'sysctls', {'net.ipv4.ip_forward': '1'}),
            ('mysql-proxy', 'environment', {'MYSQL_PASSWORD': 'public-test-value'}),
        ]
        for service, key, value in mutations:
            with self.subTest(service=service, key=key):
                changed = copy.deepcopy(self.data)
                changed['services'][service][key] = value
                with self.assertRaises(ValueError):
                    MODULE.validate(changed)

    def test_rejects_new_or_reused_data_volume(self):
        for volume in [{'name': 'dimabug-qa-mvp-13318-mysql-data'},
                       {'name': 'dimabug-qa016-mysql-data', 'external': True}]:
            changed = copy.deepcopy(self.data)
            changed['volumes']['mysql-data'] = volume
            with self.assertRaises(ValueError):
                MODULE.validate(changed)


if __name__ == '__main__':
    unittest.main()
