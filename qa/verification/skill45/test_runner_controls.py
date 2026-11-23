import ast, unittest
from pathlib import Path
from types import SimpleNamespace
SOURCE=Path(__file__).with_name('run_mysql8.py')
class RunnerControls(unittest.TestCase):
 def setUp(self):
  tree=ast.parse(SOURCE.read_text(encoding='utf-8'))
  funcs=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in ['require','sql','rows']]
  self.response=SimpleNamespace(returncode=0,stdout='',stderr='')
  import re
  self.ns={'re':re,'DB':'s45_ticket_012345abcdef','NAME':'dimabug-s45-test','docker':lambda *a,**kw:self.response}
  exec(compile(ast.Module(body=funcs,type_ignores=[]),str(SOURCE),'exec'),self.ns)
 def test_trailing_empty_metadata_cell_is_preserved(self):
  self.response.stdout='fecha_cierre\tdatetime\tYES\t<NULL>\t\n'
  self.assertEqual(self.ns['rows']('SELECT metadata'),[['fecha_cierre','datetime','YES','<NULL>','']])
 def test_empty_values_and_crlf_are_preserved(self):
  self.response.stdout='a\t\t\r\nb\tvalue\t\r\n'
  self.assertEqual(self.ns['rows']('SELECT metadata'),[['a','',''],['b','value','']])
 def test_appended_columns_do_not_change_old_metadata(self):
  row='fecha_cierre\tdatetime\tYES\t<NULL>\t\n'
  self.response.stdout=row;before=self.ns['rows']('before')
  self.response.stdout=row+'sol_contacto\tvarchar(50)\tYES\t<NULL>\t\n'
  self.assertEqual(before,self.ns['rows']('after')[:1])
 def test_shared_database_is_rejected(self):
  with self.assertRaises(AssertionError):self.ns['sql']('SELECT 1','dima_bug_staging')
 def test_sql_error_is_not_accepted_as_empty_result(self):
  self.response.returncode=1
  with self.assertRaises(RuntimeError):self.ns['sql']('SELECT 1')
if __name__=='__main__':unittest.main()
