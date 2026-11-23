"""Offline review plan only. Does not import SQL tools and has no execution mode."""
import argparse,copy,hashlib,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from fixture_tools import ROOT,FILE,validate,assert_rows,require

KEYS={'u_rol':'rol_id','u_departamento':'departamento_id','u_departamento_contacto':'contacto_id',
    'u_responsable':'responsable_id','cl_hardware':'hardware_id','cl_modulo':'modulo_id',
    'co_conocimiento':'conocimiento_id','co_sintoma':'sintoma_id','co_causa':'causa_id',
    'co_solucion':'solucion_id','pr_procedimiento':'procedimiento_id','co_conocimiento_busqueda':'conocimiento_id',
    'ticket':'id','ticket_etiqueta':'id','ticket_comentario':'id'}
ALLOWED={'u_rol':{'rol_descripcion'},'u_departamento':{'departamento_nombre'},
    'u_departamento_contacto':{'contacto_valor'},'u_responsable':{'responsable_contacto'},
    'cl_hardware':{'hardware_sistema_operativo'},'cl_modulo':{'modulo_nombre'},
    'co_conocimiento':{'conocimiento_descripcion'},'co_sintoma':{'sintoma_descripcion'},
    'co_causa':{'causa_descripcion'},'co_solucion':{'solucion_titulo','solucion_descripcion'},
    'pr_procedimiento':{'procedimiento_descripcion'},'co_conocimiento_busqueda':{'contenido_busqueda'},
    'ticket':{'descripcion'},'ticket_etiqueta':{'nombre'},'ticket_comentario':{'texto'}}

def corrected(value):
    if not isinstance(value,str):return value
    try:return value.encode('latin1').decode('utf-8')
    except UnicodeError:return value

def prepare():
    source_bytes=FILE.read_bytes();source=json.loads(source_bytes.decode('utf-8'));validate(source)
    target=copy.deepcopy(source);target['version']='mvp-qa-v2';changes=[]
    for table,rows in source['rows'].items():
        for index,row in enumerate(rows):
            for column,before in row.items():
                after=corrected(before)
                if before==after:continue
                require(table in KEYS and column in ALLOWED[table],'REPAIR_UNAPPROVED_COLUMN')
                require(isinstance(row[KEYS[table]],int),'REPAIR_PRIMARY_KEY')
                target['rows'][table][index][column]=after
                changes.append({'table':table,'primary_key':KEYS[table],'id':row[KEYS[table]],'column':column,
                    'expected_before_utf8_hex':before.encode('utf-8').hex().upper(),
                    'replacement_utf8_hex':after.encode('utf-8').hex().upper(),
                    'statement':'UPDATE `dima_bug_qa_mvp`.`'+table+'` SET `'+column+'`=CONVERT(UNHEX(%s) USING utf8mb4) WHERE `'+KEYS[table]+'`=%s AND HEX(`'+column+'`)=%s',
                    'expected_affected_rows':1})
    require(len(changes)==35 and len({c['table'] for c in changes})==15,'REPAIR_SCOPE_CHANGED')
    validate(target)
    target_bytes=(json.dumps(target,ensure_ascii=False,indent=2)+'\n').encode('utf-8')
    plan={'format':'dimabug-qa-encoding-repair-plan-v1','from_version':source['version'],'to_version':target['version'],
        'source_fixture_sha256':hashlib.sha256(source_bytes).hexdigest(),'target_fixture_sha256':hashlib.sha256(target_bytes).hexdigest(),
        'database':'dima_bug_qa_mvp','host':'127.0.0.1','port':13318,'expected_total_rows':67,'expected_updated_cells':35,
        'expected_tables':15,'execution_authorized':False,'changes':changes}
    return target_bytes,plan

def verify_preconditions(actual_rows):
    # For a FUTURE separately authorized executor: full v1 match before starting any mutation.
    source=json.loads(FILE.read_text(encoding='utf-8'));assert_rows(source,actual_rows)

def simulate(actual_rows,plan):
    require(plan==prepare()[1],'REPAIR_PLAN_CHANGED')
    verify_preconditions(actual_rows);result=copy.deepcopy(actual_rows)
    for change in plan['changes']:
        matches=[r for r in result[change['table']] if r[change['primary_key']]==change['id']]
        require(len(matches)==1,'REPAIR_ROW_IDENTITY')
        row=matches[0];require(row[change['column']].encode('utf-8').hex().upper()==change['expected_before_utf8_hex'],'REPAIR_OLD_BYTES_CHANGED')
        row[change['column']]=bytes.fromhex(change['replacement_utf8_hex']).decode('utf-8')
    return result

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--write-plan',action='store_true');a=p.parse_args()
    target,plan=prepare()
    if a.write_plan:
        for path,data in [(FILE.with_name('mvp-v2.json'),target), (FILE.with_name('encoding-repair-v1-to-v2.json'),(json.dumps(plan,ensure_ascii=False,indent=2)+'\n').encode('utf-8'))]:
            if path.exists():require(path.read_bytes()==data,'REPAIR_REVIEW_FILE_EXISTS_CHANGED')
            else:
                with path.open('xb') as f:f.write(data)
    print(json.dumps({'status':'QA_ENCODING_REPAIR_PLAN_PREPARED','updates':35,'tables':15,'insertions':0,'sql_executed':False,'database_modified':False}))
