"""Read-only comparison of MySQL QA metadata with the approved DDL; no credentials output."""
import importlib.util
import json
from pathlib import Path
import re

HERE=Path(__file__).resolve().parent
SPEC=importlib.util.spec_from_file_location('qa_sql_reader',HERE/'apply_admin_grants.py')
READER=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(READER)


def require(condition,message):
    if not condition:raise ValueError(message)


def parts(text):
    result=[];start=0;level=0;quote=False
    for i,char in enumerate(text):
        if char=="'":quote=not quote
        elif not quote:
            if char=='(':level+=1
            elif char==')':level-=1
            elif char==',' and level==0:result.append(text[start:i].strip());start=i+1
    result.append(text[start:].strip());return result


def model():
    source=re.sub(r'--[^\n]*','',(HERE/'20_schema_final.sql').read_text())
    tables={};foreign=set();unique=set()
    for match in re.finditer(r'CREATE TABLE\s+(\w+)\s*\(([\s\S]*?)\) ENGINE',source,re.I):
        name,body=match[1],match[2];columns={}
        for item in parts(body):
            field=re.match(r'(\w+)\s+((?:ENUM\([^)]*\))|(?:\w+(?:\([^)]*\))?))',item,re.I)
            if field and field[1].upper() not in {'PRIMARY','FOREIGN','UNIQUE','CONSTRAINT','INDEX','KEY','CHECK','FULLTEXT'}:
                column,kind=field[1],field[2].lower().replace(' ','')
                if kind=='boolean':kind='tinyint(1)'
                if kind=='bit':kind='bit(1)'
                columns[column]=(kind, 'NO' if re.search(r'\bNOT NULL\b|\bPRIMARY KEY\b',item,re.I) else 'YES', bool(re.search('AUTO_INCREMENT',item,re.I)))
                if re.search(r'\bPRIMARY KEY\b|\bUNIQUE\b',item,re.I):unique.add((name,(column,)))
            key=re.search(r'(?:PRIMARY KEY|UNIQUE(?: KEY)?(?:\s+\w+)?)\s*\(([^)]+)\)',item,re.I)
            if key:unique.add((name,tuple(x.strip() for x in key[1].split(','))))
            fk=re.search(r'FOREIGN KEY\s*\(([^)]+)\)\s*REFERENCES\s+(\w+)\s*\(([^)]+)\)',item,re.I)
            if fk:
                for col,ref in zip(fk[1].split(','),fk[3].split(',')):
                    foreign.add((name,col.strip(),fk[2],ref.strip()))
        tables[name]=columns
    return tables,foreign,unique


def check():
    tables,expected_fk,expected_unique=model()
    rows=READER.sql("SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA='dima_bug_qa_mvp';")
    actual_tables={}
    for line in rows.splitlines():
        name,engine,collation=line.split('\t');actual_tables[name]=(engine,collation)
    require(set(actual_tables)==set(tables) and len(tables)==44,'TABLE_INVENTORY_MISMATCH')
    require(all(v==('InnoDB','utf8mb4_unicode_ci') for v in actual_tables.values()),'STORAGE_COLLATION_MISMATCH')
    actual={name:{} for name in tables}
    rows=READER.sql("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,EXTRA FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='dima_bug_qa_mvp';")
    for line in rows.splitlines():
        fields=line.split('\t');fields+=['']*(5-len(fields));name,column,kind,nullable,extra=fields
        actual[name][column]=(kind.lower().replace(' ',''),nullable,'auto_increment' in extra)
    require(actual==tables,'COLUMN_TYPE_NULLABILITY_IDENTITY_MISMATCH')
    rows=READER.sql("SELECT TABLE_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='dima_bug_qa_mvp' AND REFERENCED_TABLE_NAME IS NOT NULL;")
    actual_fk={tuple(row.split('\t')) for row in rows.splitlines()}
    require(actual_fk==expected_fk,'FOREIGN_KEY_MISMATCH')
    indexes={};fulltext=False
    rows=READER.sql("SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,INDEX_TYPE,COLUMN_NAME,SEQ_IN_INDEX FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='dima_bug_qa_mvp' ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;")
    for row in rows.splitlines():
        name,index,non_unique,kind,column,seq=row.split('\t')
        if non_unique=='0':indexes.setdefault((name,index),[]).append(column)
        if name=='co_conocimiento_busqueda' and kind=='FULLTEXT' and column=='contenido_busqueda':fulltext=True
    require({(name,tuple(cols)) for (name,index),cols in indexes.items()}==expected_unique,'PRIMARY_UNIQUE_KEYS_MISMATCH')
    require(fulltext,'FULLTEXT_MISSING')
    counts={name:int(READER.sql('SELECT COUNT(*) FROM `dima_bug_qa_mvp`.`'+name+'`;')) for name in tables}
    require(counts['u_organizacion_control']==1 and sum(counts.values())==1,'UNEXPECTED_DATA')
    require(READER.sql('SELECT control_id FROM dima_bug_qa_mvp.u_organizacion_control;')=='1','CONTROL_ROW_MISMATCH')
    print(json.dumps({'estado':'ESQUEMA_QA_CONTRASTADO_CON_DDL','tablas':44,'columnas':sum(len(c) for c in tables.values()),
        'columnas_tipos_nulabilidad_autoincremento_verificados':True,'foreign_keys_verificadas':len(expected_fk),
        'claves_primarias_y_unicas_verificadas':True,'fulltext_verificado':True,'filas_totales':1,
        'fila_control_unica':True,'validacion_hibernate_pendiente':True}))


if __name__=='__main__':
    try:check()
    except Exception as exc:
        message=str(exc);control=message if re.fullmatch(r'[A-Z_0-9]+',message) else type(exc).__name__
        print(json.dumps({'estado':'ESQUEMA_QA_NO_VERIFICADO','control':control}));raise SystemExit(1)
