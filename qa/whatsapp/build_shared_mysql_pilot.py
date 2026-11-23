"""Prepara paquete local sin secretos. No SSH, Docker, SQL ni servicios."""
from pathlib import Path
import hashlib,json,secrets,shutil,subprocess,sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'qa/whatsapp'))
from build_autoatencion_base_qa import generar,NOMBRE

def main():
    db='wa_durable_'+secrets.token_hex(6);assert NOMBRE.fullmatch(db)
    out=ROOT/'qa/runtime/shared-mysql-pilot'/db;out.mkdir(parents=True)
    template=ROOT/'qa/whatsapp/ubuntu/shared-mysql-pilot'
    for name in ['provision.py','pilot.py','install.sh','rollback.sh','dimabug-wa-pilot.service','dimabug-wa-pilot-graph.service']:
        # LF/UTF-8 explícitos: ejecutable en Ubuntu, sin CRLF heredados de Windows.
        (out/name).write_text((template/name).read_text(encoding='utf-8'),encoding='utf-8',newline='\n')
    text=(template/'application.properties.template').read_text(encoding='utf-8').replace('__QA_DATABASE__',db)
    (out/'application.properties').write_text(text,encoding='utf-8',newline='\n')
    (out/'schema.sql').write_text(generar(db),encoding='utf-8',newline='\n')
    prior=json.loads((ROOT/'qa/runtime/autoatencion/wa-auto-base-6cbea583bc/result.json').read_text())
    jar=Path(prior['jar']);assert hashlib.sha256(jar.read_bytes()).hexdigest()==prior['jar_sha256']
    shutil.copy2(jar,out/'whatsapp-qa.jar')
    (out/'proxy').mkdir()
    javac=str(Path(shutil.which('java')).with_name('javac.exe'))
    subprocess.run([javac,'-encoding','UTF-8','-d',str(out/'proxy'),str(ROOT/'qa/whatsapp/GraphHttpsProxy.java')],capture_output=True,check=True)
    manifest={'database':db,'mysql_user':'wa_qa_pilot','real_send':False,'sha256':{}}
    for p in sorted(out.rglob('*')):
        if p.is_file():manifest['sha256'][p.relative_to(out).as_posix()]=hashlib.sha256(p.read_bytes()).hexdigest()
    (out/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps({'status':'PREPARED_NOT_EXECUTED','bundle':str(out),'database':db,'jar_sha256':prior['jar_sha256']}))

if __name__=='__main__':main()
