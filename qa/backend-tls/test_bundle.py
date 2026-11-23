"""Integrity regression tests with synthetic public files; no Docker, SQL or custody."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

WORKSPACE=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('qa_manifest',WORKSPACE/'qa/qa_manifest.py')
manifest=importlib.util.module_from_spec(spec);spec.loader.exec_module(manifest)

class BundleIntegrity(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(dir=WORKSPACE/'qa/runtime/tmp')
        self.root=Path(self.temp.name)
        self.folder=self.root/'qa/artifacts/test';self.folder.mkdir(parents=True)
        self.main=self.root/'dima-bug-backend/src/main/java/Synthetic.java';self.main.parent.mkdir(parents=True);self.main.write_bytes(b'public synthetic source')
        self.helper=self.root/'qa/backend-tls/src/Synthetic.java';self.helper.parent.mkdir(parents=True);self.helper.write_bytes(b'public synthetic helper')
        self.document={'format':'dimabug-qa-backend-bundle-v1','connector_version':'9.7.0',
            'leaf_sha256':'afee3b9f5f0b8ad162198bfe96b3b5f5be45499afa7f69acb1d89a9a812d7b9e',
            'source_snapshot':{self.main.relative_to(self.root).as_posix():self.sha(self.main)},
            'helper_source_snapshot':{self.helper.relative_to(self.root).as_posix():self.sha(self.helper)},'files':{}}
        for role in ('backend','helper','ca','truststore','loader'):
            f=self.folder/role;f.write_bytes(role.encode())
            self.document['files'][role]={'path':f.relative_to(self.root).as_posix(),'sha256':self.sha(f)}
        self.file=self.folder/'bundle.json';self.save()
        self.patcher=patch.object(manifest,'ROOT',self.root);self.patcher.start()
    def tearDown(self):self.patcher.stop();self.temp.cleanup()
    @staticmethod
    def sha(file):return hashlib.sha256(file.read_bytes()).hexdigest()
    def save(self):self.file.write_text(json.dumps(self.document))
    def test_intact_bundle_accepted(self):manifest.validate_bundle(self.file)
    def test_tampered_helper_rejected(self):
        (self.folder/'helper').write_bytes(b'changed')
        with self.assertRaises(ValueError):manifest.validate_bundle(self.file)
    def test_stale_main_source_rejected(self):
        self.main.write_bytes(b'changed')
        with self.assertRaises(ValueError):manifest.validate_bundle(self.file)
    def test_stale_tls_source_rejected(self):
        self.helper.write_bytes(b'changed')
        with self.assertRaises(ValueError):manifest.validate_bundle(self.file)
    def test_artifact_escape_rejected(self):
        self.document['files']['helper']={'path':self.main.relative_to(self.root).as_posix(),'sha256':self.sha(self.main)};self.save()
        with self.assertRaises(ValueError):manifest.validate_bundle(self.file)
    def add_otp(self):
        source=self.root/'qa/auth/src/Synthetic.java';source.parent.mkdir(parents=True);source.write_bytes(b'public synthetic otp adapter')
        plugin=self.folder/'qa-otp.jar';plugin.write_bytes(b'public synthetic plugin')
        self.document['files']['qa_otp']={'path':plugin.relative_to(self.root).as_posix(),'sha256':self.sha(plugin)}
        self.document['otp_source_snapshot']={source.relative_to(self.root).as_posix():self.sha(source)}
        self.save();return plugin
    def test_otp_extension_registered(self):
        self.add_otp();manifest.validate_bundle(self.file)
    def test_otp_extension_tampering_rejected(self):
        plugin=self.add_otp();plugin.write_bytes(b'changed')
        with self.assertRaises(ValueError):manifest.validate_bundle(self.file)

if __name__=='__main__':unittest.main()
