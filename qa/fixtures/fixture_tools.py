"""Versioned synthetic data plans and exact verification. Never runs SQL at import or default CLI."""
import argparse,hashlib,json,re,unicodedata
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
FILE=Path(__file__).with_name('mvp-v1.json')
def require(ok,code):
    if not ok:raise RuntimeError(code)
def fixture_file(version='mvp-qa-v2'):
    require(version in ('mvp-qa-v1','mvp-qa-v2'),'FIXTURE_VERSION')
    return FILE if version=='mvp-qa-v1' else FILE.with_name('mvp-v2.json')
def load(version='mvp-qa-v2'):
    data=json.loads(fixture_file(version).read_text(encoding='utf-8'));validate(data);return data
def fingerprint(version='mvp-qa-v2'):return hashlib.sha256(fixture_file(version).read_bytes()).hexdigest()
def parts(text):
    result=[];start=0;level=0;quote=False
    for i,c in enumerate(text):
        if c=="'":quote=not quote
        elif not quote:
            if c=='(':level+=1
            elif c==')':level-=1
            elif c==',' and level==0:result.append(text[start:i].strip());start=i+1
    result.append(text[start:].strip());return result
def validate_schema(data):
    ddl=(ROOT/'qa/mysql/schema/20_schema_final.sql').read_text(encoding='utf-8');foreign=[]
    for table,body in re.findall(r'CREATE TABLE (\w+)\s*\(([\s\S]*?)\) ENGINE',ddl):
        columns={};unique=[]
        for field in parts(body):
            m=re.match(r'(\w+)\s+((?:ENUM\([^)]*\))|(?:\w+(?:\([^)]*\))?))',field,re.I)
            if m and m[1].upper() not in {'PRIMARY','FOREIGN','UNIQUE','CONSTRAINT','INDEX','KEY','CHECK','FULLTEXT'}:
                columns[m[1]]=(m[2],field)
                if re.search(r'\bPRIMARY KEY\b|\bUNIQUE\b',field,re.I):unique.append([m[1]])
            u=re.search(r'(?:PRIMARY KEY|UNIQUE(?: KEY)?(?:\s+\w+)?)\s*\(([^)]+)\)',field,re.I)
            if u:unique.append([s.strip() for s in u[1].split(',')])
            fk=re.search(r'FOREIGN KEY\s*\((\w+)\)\s*REFERENCES\s+(\w+)\s*\((\w+)\)',field,re.I)
            if fk:foreign.append((table,*fk.groups()))
        for row in data['rows'][table]:
            require(set(row)<=set(columns),'FIXTURE_UNKNOWN_COLUMN')
            for name,(kind,definition) in columns.items():
                if 'NOT NULL' in definition and 'DEFAULT' not in definition and 'AUTO_INCREMENT' not in definition:require(name in row and row[name] is not None,'FIXTURE_REQUIRED_COLUMN')
                if name not in row or row[name] is None:continue
                value=row[name]
                limit=re.match(r'VARCHAR\((\d+)\)',kind,re.I)
                if limit:require(len(str(value))<=int(limit[1]),'FIXTURE_LENGTH')
                if kind.upper().startswith('ENUM'):require(value in re.findall("'([^']+)'",kind),'FIXTURE_ENUM')
        for key in unique:
            seen=set()
            for row in data['rows'][table]:
                if any(row.get(k) is None for k in key):continue
                values=tuple(''.join(c for c in unicodedata.normalize('NFD',v.casefold()) if not unicodedata.combining(c)) if isinstance(v,str) else v for v in (row[k] for k in key))
                require(values not in seen,'FIXTURE_DUPLICATE');seen.add(values)
    foreign += [('u_departamento_contacto','departamento_id','u_departamento','departamento_id'),('u_responsable','departamento_id','u_departamento','departamento_id'),('pr_paso','procedimiento_id','pr_procedimiento','procedimiento_id')]
    foreign += [(t,'conocimiento_id','co_conocimiento','conocimiento_id') for t in ['co_sintoma','co_causa','co_solucion','co_conocimiento_prueba','co_conocimiento_busqueda']]
    for table,column,parent,target in foreign:
        ids={r[target] for r in data['rows'][parent]}
        require(all(r.get(column) is None or r[column] in ids for r in data['rows'][table]),'FIXTURE_REFERENCE')
def validate(data):
    require(data['format']=='dimabug-qa-fixtures-v1' and data['version'] in ('mvp-qa-v1','mvp-qa-v2'),'FIXTURE_VERSION')
    require((data['host'],data['port'],data['database'])==('127.0.0.1',13318,'dima_bug_qa_mvp'),'FIXTURE_SCOPE')
    tables=re.findall(r'CREATE TABLE (\w+)\s*\(',(ROOT/'qa/mysql/schema/20_schema_final.sql').read_text(encoding='utf-8'))
    require(data['table_order']==tables and set(data['rows'])==set(tables) and len(tables)==44,'FIXTURE_TABLES')
    require([r['rol_nombre'] for r in data['rows']['u_rol']]==['ADMINISTRADOR','TECNICO','SOPORTE','USUARIO'],'FIXTURE_ROLES')
    users=data['rows']['u_usuario'];require(len(users)==8 and len({u['usuario_email'] for u in users})==8,'FIXTURE_USERS')
    require(all(u['usuario_email'].endswith('@qa.dimabug.test') and u['usuario_password_hash'].startswith('@RUNTIME_BCRYPT:') for u in users),'FIXTURE_IDENTITIES')
    roleids={r['rol_id'] for r in data['rows']['u_rol']};userids={u['usuario_id'] for u in users}
    require(all(u['rol_id'] in roleids for u in users),'FIXTURE_USER_ROLE')
    require(all(not data['rows'][t] for t in tables if t.startswith('gm_') or t in ('u_sucursal','u_codigo_acceso','u_correo_codigo','se_solicitud_evidencia')),'FIXTURE_FORBIDDEN_DATA')
    require(data['rows']['u_organizacion_control']==[{'control_id':1}],'FIXTURE_CONTROL')
    for table,records in data['rows'].items():
        for record in records:require(all(re.fullmatch('[a-z0-9_]+',column) for column in record),'FIXTURE_COLUMNS')
    for ticket in data['rows']['ticket']:
        require(ticket['solicitante_id'] in userids and (ticket['asignado_id'] is None or ticket['asignado_id'] in userids),'FIXTURE_TICKET_USERS')
        require(ticket['estado'] in ('ABIERTO','EN_PROGRESO','EN_ESPERA','RESUELTO','CERRADO','CANCELADO'),'FIXTURE_TICKET_STATE')
    steps=data['rows']['pr_paso'];require(len({(p['procedimiento_id'],p['paso_orden']) for p in steps})==len(steps),'FIXTURE_STEP_ORDER')
    validate_schema(data)

def plan(data,reset=False):
    validate(data);result=[]
    if reset:
        for table in reversed(data['table_order']):
            if not table.startswith('gm_') and table!='u_organizacion_control':result.append(('DELETE FROM `dima_bug_qa_mvp`.`'+table+'`',[]))
    for table in data['table_order']:
        if table=='u_organizacion_control':continue # Existing structural row, never duplicated/deleted.
        for record in data['rows'][table]:
            columns=list(record)
            result.append(('INSERT INTO `dima_bug_qa_mvp`.`'+table+'` ('+','.join('`'+c+'`' for c in columns)+') VALUES ('+','.join(['%s']*len(columns))+')',[record[c] for c in columns]))
    return result

def canonical_rows(records):
    return sorted(json.dumps(r,ensure_ascii=False,sort_keys=True,separators=(',',':')) for r in records)
def assert_rows(data,actual):
    validate(data);require(set(actual)==set(data['rows']),'FIXTURE_ACTUAL_TABLES')
    for table,expected in data['rows'].items():
        normalized=[]
        for row in actual[table]:
            item=dict(row)
            if table=='u_usuario':
                alias=item['usuario_email'].split('@')[0]
                require(bool(re.fullmatch(r'\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}',item['usuario_password_hash'])),'FIXTURE_PASSWORD_HASH')
                item['usuario_password_hash']='@RUNTIME_BCRYPT:'+alias
            for key,value in list(item.items()):
                if isinstance(value,bool):item[key]=int(value)
                elif isinstance(value,str) and re.fullmatch(r'\d{4}-\d\d-\d\d \d\d:\d\d:\d\d\.0+',value):item[key]=value.split('.')[0]
            normalized.append(item)
        normalized_expected=[{k:int(v) if isinstance(v,bool) else v for k,v in r.items()} for r in expected]
        require(canonical_rows(normalized)==canonical_rows(normalized_expected),'FIXTURE_CONTENT_MISMATCH')

def read_rows(query,data):
    actual={}
    for table in data['table_order']:
        expected=data['rows'][table]
        if not expected:
            require(query('SELECT COUNT(*) FROM dima_bug_qa_mvp.`'+table+'`;')=='0','FIXTURE_UNEXPECTED_ROWS');actual[table]=[];continue
        columns=list(expected[0]);pairs=','.join("'"+c+"',`"+c+'`' for c in columns)
        text=query('SELECT JSON_OBJECT('+pairs+') FROM dima_bug_qa_mvp.`'+table+'`;')
        actual[table]=[json.loads(line) for line in text.splitlines()]
    return actual

def verify_receipt(query,receipt_path):
    receipt,data=receipt_identity(query,receipt_path)
    actual=read_rows(query,data);assert_rows(data,actual)
    hashes={t:hashlib.sha256('\n'.join(canonical_rows(rows)).encode()).hexdigest() for t,rows in actual.items()}
    require(hashes==receipt['data_sha256'],'FIXTURE_BASELINE_CHANGED')
    return True
def schema_hashes(query,data):
    return {t:hashlib.sha256(re.sub(r' AUTO_INCREMENT=\d+','',query('SHOW CREATE TABLE dima_bug_qa_mvp.`'+t+'`;')).encode()).hexdigest() for t in data['table_order']}
def receipt_identity(query,receipt_path):
    original=Path(receipt_path);path=original.resolve(strict=True)
    require(path.is_relative_to(ROOT/'qa/runtime/fixture-runs') and not original.is_symlink(),'FIXTURE_RECEIPT_PATH')
    receipt=json.loads(path.read_text(encoding='utf-8'));data=load(receipt['version'])
    require(receipt['format']=='dimabug-qa-fixture-receipt-v1' and receipt['fixture_sha256']==fingerprint(data['version']) and receipt['version']==data['version'],'FIXTURE_RECEIPT_IDENTITY')
    require(query('SELECT @@server_uuid;')==receipt['server_uuid'],'FIXTURE_INSTANCE')
    require(schema_hashes(query,data)==receipt['schema_sha256'],'FIXTURE_SCHEMA_CHANGED')
    return receipt,data
def verify_reset_scope(query,receipt_path):
    receipt,data=receipt_identity(query,receipt_path)
    require(query('SELECT control_id FROM dima_bug_qa_mvp.u_organizacion_control;')=='1','FIXTURE_CONTROL')
    for table in data['table_order']:
        if table.startswith('gm_') or table=='u_sucursal':require(query('SELECT COUNT(*) FROM dima_bug_qa_mvp.`'+table+'`;')=='0','FIXTURE_UNEXPECTED_EXTERNAL_DATA')
    for email in query('SELECT usuario_email FROM dima_bug_qa_mvp.u_usuario;').splitlines():
        require(email.endswith('@qa.dimabug.test'),'FIXTURE_NON_SYNTHETIC_USER')
    return True

def literal(value,hashes):
    if value is None:return 'NULL'
    if isinstance(value,bool):return '1' if value else '0'
    if isinstance(value,int):return str(value)
    if value.startswith('@RUNTIME_BCRYPT:'):
        value=hashes.get(value.split(':',1)[1],'')
        require(bool(re.fullmatch(r'\$2[aby]\$[0-9]{2}\$[./A-Za-z0-9]{53}',value)),'FIXTURE_RUNTIME_HASH_REQUIRED')
    return 'CONVERT(0x'+value.encode('utf-8').hex()+' USING utf8mb4)'
def sql_transaction(data,hashes,reset=False):
    statements=['START TRANSACTION']
    for statement,values in plan(data,reset):
        for value in values:statement=statement.replace('%s',literal(value,hashes),1)
        statements.append(statement)
    return ';\n'.join(statements)+';\nCOMMIT;\n'

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');p.add_argument('--version',choices=['mvp-qa-v1','mvp-qa-v2'],default='mvp-qa-v2');a=p.parse_args()
    d=load(a.version);print(json.dumps({'status':'QA_FIXTURES_VALIDATED_NOT_LOADED','version':d['version'],'users':8,'roles':4,'rows':sum(map(len,d['rows'].values())),'sql_executed':False}))
