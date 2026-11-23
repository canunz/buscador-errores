"""Freeze only approved deltas over the deployed source pair; offline isolated builds."""
import ast,hashlib,json,os,shutil,subprocess,sys
from pathlib import Path
R=Path(__file__).resolve().parents[1]
A=R/'qa/artifacts/registro-universal-20261115'
base=R/'qa/artifacts/staging-auth-isolation-20261114'
def paths(script):
    tree=ast.parse((R/script).read_text(encoding='utf-8'))
    return {n.args[0].value for n in ast.walk(tree) if isinstance(n,ast.Call) and isinstance(n.func,ast.Name)
            and n.func.id in ('edit','write') and n.args and isinstance(n.args[0],ast.Constant)
            and isinstance(n.args[0].value,str) and n.args[0].value.endswith(('.java','.ts'))}
back=paths('qa/implement_universal_local.py')|set('''identidad/domain/Usuario.java
identidad/infrastructure/persistence/entity/UsuarioEntity.java
identidad/infrastructure/persistence/repository/UsuarioJpaRepository.java
identidad/application/service/AutenticarUsuarioService.java
identidad/application/service/AdministrarUsuariosService.java
identidad/infrastructure/security/JwtAuthenticationFilter.java
identidad/api/UsuarioPublico.java
tickets/application/port/out/UsuarioTicket.java
tickets/domain/ActorTicket.java
identidad/infrastructure/web/dto/LoginResponse.java
tickets/application/service/ConsultarTicketsService.java'''.splitlines())
front=paths('qa/implement_universal_frontend.py')|{'shared/layout/shell-layout.component.html','features/tickets/solicitudes-basicas.component.ts','features/tickets/solicitudes-basicas.component.spec.ts','core/guards/auth.guard.spec.ts','features/auth/login/login.component.spec.ts'}
if sys.argv[1]=='prepare':
    assert not A.exists(), 'CANDIDATE_EXISTS'
    A.mkdir(); (A/'evidence').mkdir(); (A/'artifacts').mkdir()
    shutil.copytree(base/'sources/dima-bug-backend',A/'sources/dima-bug-backend',ignore=shutil.ignore_patterns('target','.git'))
    shutil.copytree(R/'qa/artifacts/staging-minimo-20261114T025917Z/sources/frontend-dimabug',A/'sources/frontend-dimabug',ignore=shutil.ignore_patterns('node_modules','dist','.angular','.git'))
    shutil.copy2(base/'build_release.py',A/'build_release.py')
if sys.argv[1] in ('prepare','sync'):
    for rel in back:
        src=R/'dima-bug-backend/src/main/java/cl/casol/backend'/rel
        dst=A/'sources/dima-bug-backend/src/main/java/cl/casol/backend'/rel
        dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst)
    for rel in front:
        dst=A/'sources/frontend-dimabug/src/app'/rel;dst.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(R/'frontend-dimabug/src/app'/rel,dst)
    m='src/main/resources/db/migration/V20261115_01__registro_universal.sql'
    (A/'sources/dima-bug-backend'/m).parent.mkdir(parents=True,exist_ok=True)
    shutil.copy2(R/'dima-bug-backend'/m,A/'sources/dima-bug-backend'/m)
    # Restore regression sources verbatim; only new universal suites are overlaid.
    shutil.copytree(base/'sources/dima-bug-backend/src/test',A/'sources/dima-bug-backend/src/test',dirs_exist_ok=True)
    t='src/test/java/cl/casol/backend/identidad/application/service/AdministrarUsuariosServiceTest.java'
    shutil.copy2(R/'dima-bug-backend'/t,A/'sources/dima-bug-backend'/t)
    for src in (R/'dima-bug-backend/src/test').rglob('*Universal*Test.java'):
        dst=A/'sources/dima-bug-backend'/src.relative_to(R/'dima-bug-backend');dst.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(src,dst)
    print(json.dumps({'candidate':str(A),'backend_files':len(back),'frontend_files':len(front),'remote_access':False}))
else:
    raise SystemExit('USE prepare OR sync')
