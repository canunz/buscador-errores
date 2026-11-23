"""Pruebas de la definición en memoria; no Docker, SQL, archivos de datos o secretos."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location('mysql_definition', HERE / 'verify_mysql_preparation.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class DefinitionTest(unittest.TestCase):
    def setUp(self):
        self.original = json.loads((HERE / 'compose.qa-local.json').read_text(encoding='utf-8'))

    def test_definition_accepted_without_running(self):
        MODULE.validate(self.original)

    def test_rejects_reused_ports(self):
        for port in [13316, 13317]:
            sample = copy.deepcopy(self.original)
            sample['services']['mysql']['ports'] = [f'127.0.0.1:{port}:3306']
            with self.assertRaises(AssertionError):
                MODULE.validate(sample)

    def test_rejects_public_binding(self):
        sample = copy.deepcopy(self.original)
        sample['services']['mysql']['ports'] = ['0.0.0.0:13318:3306']
        with self.assertRaises(AssertionError):
            MODULE.validate(sample)

    def test_rejects_plaintext_secret(self):
        sample = copy.deepcopy(self.original)
        sample['services']['mysql']['environment']['MYSQL_ROOT_PASSWORD'] = 'synthetic-not-a-secret'
        with self.assertRaises(AssertionError):
            MODULE.validate(sample)

    def test_rejects_automatic_app_schema(self):
        sample = copy.deepcopy(self.original)
        sample['services']['mysql']['environment']['MYSQL_DATABASE'] = 'synthetic'
        with self.assertRaises(AssertionError):
            MODULE.validate(sample)

    def test_rejects_existing_volume(self):
        sample = copy.deepcopy(self.original)
        sample['volumes']['mysql-data']['name'] = 'dimabug-qa016-mysql-data'
        with self.assertRaises(AssertionError):
            MODULE.validate(sample)

    def test_rejects_external_network(self):
        sample = copy.deepcopy(self.original)
        sample['networks']['qa-internal']['internal'] = False
        with self.assertRaises(AssertionError):
            MODULE.validate(sample)

    def test_rejects_download_or_automatic_restart(self):
        for key, value in [('pull_policy', 'always'), ('restart', 'always')]:
            sample = copy.deepcopy(self.original)
            sample['services']['mysql'][key] = value
            with self.assertRaises(AssertionError):
                MODULE.validate(sample)

    def test_rejects_mutable_image(self):
        sample = copy.deepcopy(self.original)
        sample['services']['mysql']['image'] = 'mysql:latest'
        with self.assertRaises(AssertionError):
            MODULE.validate(sample)

    def test_rejects_host_network_and_environment_files(self):
        for key, value in [('network_mode', 'host'), ('env_file', 'synthetic.env')]:
            sample = copy.deepcopy(self.original)
            sample['services']['mysql'][key] = value
            with self.assertRaises(AssertionError):
                MODULE.validate(sample)

    def test_rejects_other_architecture(self):
        sample = copy.deepcopy(self.original)
        sample['services']['mysql']['platform'] = 'linux/arm64'
        with self.assertRaises(AssertionError):
            MODULE.validate(sample)


if __name__ == '__main__':
    unittest.main()
