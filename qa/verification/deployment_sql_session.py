"""A single non-reconnecting mysql CLI connection. SQL results never go to logs."""
import queue,subprocess,threading,time,uuid

class SqlSessionError(RuntimeError):pass
class SqlSession:
 def __init__(self,args,env=None,timeout=20,started=None,finished=None):
  self.timeout=timeout;self.finished=finished;self.q=queue.Queue();self.error=False;self.closed=False
  self.p=subprocess.Popen(args,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env,start_new_session=True)
  if started:started(self.p)
  def out():
   for line in iter(self.p.stdout.readline,b''):self.q.put(line.rstrip(b'\r\n'))
   self.q.put(None)
  def err():
   for line in iter(self.p.stderr.readline,b''):
    if line.lstrip().startswith(b'ERROR'):self.error=True
  threading.Thread(target=out,daemon=True).start();threading.Thread(target=err,daemon=True).start()
 def query(self,sql):
  marker=('DIMABUG_RESULT_'+uuid.uuid4().hex).encode()
  try:self.p.stdin.write((sql.rstrip(';')+";\nSELECT '"+marker.decode()+"';\n").encode('utf-8'));self.p.stdin.flush()
  except (OSError,BrokenPipeError):raise SqlSessionError('SQL_CONNECTION_LOST') from None
  rows=[];deadline=time.monotonic()+self.timeout
  while True:
   try:line=self.q.get(timeout=max(.01,deadline-time.monotonic()))
   except queue.Empty:raise SqlSessionError('SQL_RESPONSE_TIMEOUT') from None
   if line is None:raise SqlSessionError('SQL_CONNECTION_LOST')
   if line==marker:
    if self.error:raise SqlSessionError('SQL_ERROR')
    return [line.decode('utf-8').split('\t') for line in rows if line]
   rows.append(line)
 def close(self):
  if self.closed:return
  self.closed=True
  try:self.p.stdin.close()
  except OSError:pass
  try:self.p.wait(timeout=5)
  except subprocess.TimeoutExpired:self.p.kill();self.p.wait(timeout=5)
  if self.finished:self.finished(self.p)
 def __enter__(self):return self
 def __exit__(self,*args):self.close()

def quarantine(session,db,expected_uuid,plan,on_attempt,on_confirm):
 """The only COMMIT is after all explicit in-transaction safety checks succeed."""
 if session.query('SELECT @@server_uuid,DATABASE()')!=[[expected_uuid,db]]:raise SqlSessionError('SQL_WRONG_TARGET')
 session.query('START TRANSACTION')
 committing=False
 try:
  for stmt in plan:
   if stmt.upper().startswith('SELECT COUNT'):
    if session.query(stmt)!=[['0']]:raise SqlSessionError('QUARANTINE_ASSERTION_FAILED')
   else:session.query(stmt)
  on_attempt();committing=True;session.query('COMMIT');on_confirm()
 except BaseException:
  if not committing:
   try:session.query('ROLLBACK')
   except BaseException:pass
  raise
