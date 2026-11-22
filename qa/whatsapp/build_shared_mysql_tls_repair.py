"""Paquete de reparación local; conserva nombre/esquema y no ejecuta SQL/SSH."""
from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[2]
def main():
    source=ROOT/'qa/runtime/shared-mysql-pilot/wa_durable_0eb3331f3918'
    out=ROOT/'qa/runtime/shared-mysql-pilot-repair/wa_durable_0eb3331f3918'
    if out.exists():raise RuntimeError('REPAIR_BUNDLE_ALREADY_EXISTS')
    old=json.loads((source/'manifest.json').read_text())
    assert all(hashlib.sha256((source/k).read_bytes()).hexdigest()==v for k,v in old['sha256'].items())
    shutil.copytree(source,out)
    template=ROOT/'qa/whatsapp/ubuntu/shared-mysql-pilot'
    for name in ['repair_tls.py','install.sh','pilot.py','SharedMysqlPinnedSocketFactory.java','SharedMysqlTlsProbe.java','SharedMysqlPinTest.java']:
        (out/name).write_text((template/name).read_text(encoding='utf-8'),encoding='utf-8',newline='\n')
    (out/'tls').mkdir();(out/'lib').mkdir()
    with zipfile.ZipFile(out/'whatsapp-qa.jar') as jar:
        name=next(n for n in jar.namelist() if n.startswith('BOOT-INF/lib/mysql-connector-j-') and n.endswith('.jar'))
        (out/'lib/mysql-connector-j.jar').write_bytes(jar.read(name))
    javac=str(Path(shutil.which('java')).with_name('javac.exe'));cp=str(out/'lib/mysql-connector-j.jar')
    subprocess.run([javac,'-encoding','UTF-8','-cp',cp,'-d',str(out/'tls'),*[str(out/n) for n in ['SharedMysqlPinnedSocketFactory.java','SharedMysqlTlsProbe.java','SharedMysqlPinTest.java']]],capture_output=True,check=True)
    test=subprocess.run(['java','-cp',str(out/'tls')+';'+cp,'dimabug.qa.SharedMysqlPinTest'],capture_output=True,text=True,check=True)
    # Clases QA de TLS, sin modificar fuentes del backend ni configuración de producción.
    original=out/'whatsapp-qa.jar';patched=out/'whatsapp-qa-pinned.jar'
    with zipfile.ZipFile(original) as src,zipfile.ZipFile(patched,'w') as dest:
        for item in src.infolist():dest.writestr(item,src.read(item.filename))
        for file in (out/'tls/dimabug/qa').glob('SharedMysqlPinnedSocketFactory*.class'):
            dest.write(file,'BOOT-INF/classes/dimabug/qa/'+file.name)
    patched.replace(original)
    m={'database':old['database'],'mysql_user':'wa_qa_pilot','real_send':False,'original_jar_sha256':old['sha256']['whatsapp-qa.jar'],'sha256':{}}
    for f in sorted(out.rglob('*')):
        if f.is_file() and f.name!='manifest.json':m['sha256'][f.relative_to(out).as_posix()]=hashlib.sha256(f.read_bytes()).hexdigest()
    (out/'manifest.json').write_text(json.dumps(m,indent=2)+'\n',encoding='utf-8')
    print(test.stdout.strip());print(json.dumps({'status':'PREPARED_NOT_APPLIED','bundle':str(out),'database_unchanged':True,'account_unlocked':False}))
if __name__=='__main__':main()
