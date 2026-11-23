"""Solo hashes e inventario del JAR; no configura ni ejecuta la aplicación."""
import hashlib
import json
from pathlib import Path
import zipfile

try:
    jar = Path('/opt/dimabug/staging/backend.jar')
    with jar.open('rb') as f:
        digest = hashlib.file_digest(f, 'sha256').hexdigest()
    if digest != '21df61ccb07090d36417fe3032583a38fe21a3c5721f2dac797387b2d82bd91f':
        raise RuntimeError()
    with zipfile.ZipFile(jar) as z:
        for cls in ['identidad/infrastructure/web/AuthController',
                    'identidad/application/service/AutenticarUsuarioService',
                    'identidad/infrastructure/persistence/entity/CodigoAccesoEntity',
                    'identidad/infrastructure/persistence/adapter/CodigoAccesoRepositoryAdapter']:
            member = 'BOOT-INF/classes/cl/casol/backend/' + cls + '.class'
            print(json.dumps({'clase': member, 'sha256': hashlib.sha256(z.read(member)).hexdigest()}))
        print(json.dumps({'despachador_persistente_presente':
            'BOOT-INF/classes/cl/casol/backend/identidad/application/service/DespacharCodigosService.class' in z.namelist(),
            'jar_verificado': True, 'solo_lectura': True}))
except Exception as exc:
    print(json.dumps({'incompleto': True, 'categoria': type(exc).__name__}))
    raise SystemExit(1)
