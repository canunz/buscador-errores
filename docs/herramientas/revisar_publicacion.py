"""Auditoría de indicadores de secretos, sin imprimir valores.
Complementa, no sustituye, revisión manual. No accede al entorno ni al remoto.
"""
import argparse,json,re,subprocess
from pathlib import Path
PATTERNS={
 'google_client_id':r'\b[0-9]{8,}-[a-zA-Z0-9_-]{15,}\.apps\.googleusercontent\.com\b',
 'google_client_secret':r'GOCSPX-[A-Za-z0-9_-]{12,}',
 'google_access_token':r'ya29\.[A-Za-z0-9._-]{15,}',
 'google_refresh_token':r'1//[A-Za-z0-9_-]{20,}',
 'google_api_key':r'AIza[0-9A-Za-z_-]{30,}',
 'private_key':r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',
 'github_token':r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,})',
 'jwt_literal':r'\beyJ[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}\.[A-Za-z0-9_-]{12,}',
}
def audit(staged=False):
 args=['git','ls-files','-z'] if staged else ['git','ls-files','-co','--exclude-standard','-z']
 names=sorted(set(n for n in subprocess.check_output(args).decode().split('\0') if n))
 findings=[];skipped=[];count=0
 for name in names:
  if staged:data=subprocess.check_output(['git','show',':'+name])
  else:
   p=Path(name)
   if not p.is_file():continue
   data=p.read_bytes()
  if len(data)>20_000_000:skipped.append(name);continue
  count+=1;text=data.decode('utf-8',errors='ignore')
  for category,pattern in PATTERNS.items():
   for match in re.finditer(pattern,text):findings.append({'file':name,'line':text.count('\n',0,match.start())+1,'category':category})
  if name.endswith('.json'):
   try:obj=json.loads(text)
   except ValueError:continue
   def walk(value):
    if isinstance(value,dict):
     for key,item in value.items():
      if re.fullmatch(r'(?:client_secret|client_id|access_token|refresh_token|jwt_secret|db_password|private_key)',key,re.I) and isinstance(item,str) and item and not re.search(r'\$\{|example|invalid|dummy|fake|test|sint[eé]tic|placeholder|<|NO_LOGIN',item,re.I):findings.append({'file':name,'category':'credential_json_review','field':key})
      walk(item)
    elif isinstance(value,list):
     for item in value:walk(item)
   walk(obj)
 return {'staged':staged,'files_scanned':count,'findings':findings,'skipped_large_files':skipped,'limits':'Complementar con revisión manual de configuración, fixtures, binarios y .gitignore; no inspecciona valores del entorno o historial remoto.'}
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--staged',action='store_true');parser.add_argument('--report');opts=parser.parse_args();result=audit(opts.staged)
 if opts.report:Path(opts.report).write_text(json.dumps(result,indent=2,ensure_ascii=False),encoding='utf-8')
 print(json.dumps(result,ensure_ascii=False));raise SystemExit(1 if result['findings'] or result['skipped_large_files'] else 0)
