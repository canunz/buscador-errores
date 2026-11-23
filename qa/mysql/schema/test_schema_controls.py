"""Offline regression checks against accidental expansion of grants and incomplete DDL."""
import contextlib
import importlib.util
import io
from pathlib import Path
import shutil
import tempfile
import unittest

HERE=Path(__file__).resolve().parent
SPEC=importlib.util.spec_from_file_location('schema_checks',HERE/'verify_schema_proposal.py')
MODULE=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class SchemaControls(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory()
        self.folder=Path(self.temp.name)
        for name in ['20_schema_final.sql','schema_inventory.json','privileges.json','18_admin_grants.sql','40_app_grants.sql.template','15_create_users.sql.template']:
            shutil.copyfile(HERE/name,self.folder/name)
        MODULE.HERE=self.folder

    def tearDown(self):
        MODULE.HERE=HERE
        self.temp.cleanup()

    def check(self):
        with contextlib.redirect_stdout(io.StringIO()): MODULE.check()

    def alter(self,file,old,new):
        p=self.folder/file
        text=p.read_text();self.assertIn(old,text)
        p.write_text(text.replace(old,new,1))

    def test_reviewed_proposal(self): self.check()

    def test_rejects_global_grant(self):
        self.alter('40_app_grants.sql.template','`dima_bug_qa_mvp`.`cl_frecuencia`','*.*')
        with self.assertRaises(ValueError): self.check()

    def test_rejects_host_wildcard(self):
        self.alter('40_app_grants.sql.template','{{QA_PROXY_HOST_SQL_LITERAL}}',"'%'")
        with self.assertRaises(ValueError): self.check()

    def test_rejects_app_create_permission(self):
        self.alter('40_app_grants.sql.template','GRANT SELECT, INSERT, UPDATE','GRANT SELECT, INSERT, UPDATE, CREATE')
        with self.assertRaises(ValueError): self.check()

    def test_rejects_missing_fulltext(self):
        self.alter('20_schema_final.sql','FULLTEXT KEY ft_conocimiento_contenido','KEY ft_conocimiento_contenido')
        with self.assertRaises(ValueError): self.check()

    def test_rejects_missing_native_column(self):
        self.alter('20_schema_final.sql','usuario_ultimo_ingreso','removed_native_column')
        with self.assertRaises(ValueError): self.check()

    def test_rejects_destructive_sql(self):
        with (self.folder/'20_schema_final.sql').open('a') as f: f.write('\nDROP TABLE u_usuario;\n')
        with self.assertRaises(ValueError): self.check()


if __name__=='__main__': unittest.main()
