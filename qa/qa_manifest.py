"""Identidad del candidato; solo lee por defecto. No compila ni inicia aplicaciones."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'qa/candidate.json'
EXCLUDED = {'qa/candidate.json'}


def git(*args):
    return subprocess.check_output([os.environ.get('QA_GIT_EXECUTABLE', 'git'), '-C', str(ROOT), *args], stderr=subprocess.DEVNULL)


def records():
    names = set((git('ls-files', '-z') + git('ls-files', '--others', '--exclude-standard', '-z')).decode('utf-8').split('\0'))
    result = []
    for name in sorted(names - EXCLUDED - {''}):
        path = ROOT / name
        if name.startswith(('.dimabug-private/', 'qa/runtime/', 'qa/artifacts/')):
            raise ValueError('Ruta privada o generada incluida en Git')
        if not path.is_file() or path.is_symlink():
            raise ValueError('Archivo ausente o enlace requiere revisión')
        result.append({'path': name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    return result


def fingerprint(files):
    digest = hashlib.sha256()
    for item in sorted(files, key=lambda x: x['path']):
        digest.update(item['path'].encode('utf-8') + b'\0' + bytes.fromhex(item['sha256']) + b'\0')
    return digest.hexdigest()


def changed(before, after):
    old = {x['path']: x['sha256'] for x in before}
    new = {x['path']: x['sha256'] for x in after}
    return sorted(k for k in old.keys() | new.keys() if old.get(k) != new.get(k))


def validate_bundle(argument):
    path = Path(argument).resolve(strict=True)
    if not path.is_relative_to(ROOT / 'qa/artifacts') or path.is_symlink():
        raise ValueError('Paquete fuera de QA')
    bundle = json.loads(path.read_text(encoding='utf-8'))
    if (bundle['format'] != 'dimabug-qa-backend-bundle-v1' or bundle['connector_version'] != '9.7.0'
        or bundle['leaf_sha256'] != 'afee3b9f5f0b8ad162198bfe96b3b5f5be45499afa7f69acb1d89a9a812d7b9e'
        or set(bundle['files']) not in ({'backend','helper','ca','truststore','loader'},{'backend','helper','ca','truststore','loader','qa_otp'})):
        raise ValueError('Identidad del paquete incorrecta')
    for entry in bundle['files'].values():
        file = (ROOT / entry['path']).resolve(strict=True)
        if file.parent != path.parent or not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != entry['sha256']:
            raise ValueError('Archivo del paquete modificado o fuera del directorio')
    for name, digest in bundle['source_snapshot'].items():
        file = (ROOT/name).resolve(strict=True)
        if not file.is_relative_to(ROOT/'dima-bug-backend') or hashlib.sha256(file.read_bytes()).hexdigest() != digest:
            raise ValueError('Las fuentes ya no coinciden con la compilacion')
    helper_sources = bundle.get('helper_source_snapshot', {})
    if not helper_sources:
        raise ValueError('Faltan las fuentes del control JDBC')
    for name, digest in helper_sources.items():
        file = (ROOT/name).resolve(strict=True)
        if not file.is_relative_to(ROOT/'qa/backend-tls/src') or hashlib.sha256(file.read_bytes()).hexdigest() != digest:
            raise ValueError('El control JDBC no coincide con sus fuentes')
    if 'qa_otp' in bundle['files']:
        otp_sources=bundle.get('otp_source_snapshot',{})
        if not otp_sources:raise ValueError('Faltan fuentes del adaptador QA')
        for name,digest in otp_sources.items():
            file=(ROOT/name).resolve(strict=True)
            if not file.is_relative_to(ROOT/'qa/auth/src') or hashlib.sha256(file.read_bytes()).hexdigest()!=digest:
                raise ValueError('Adaptador QA modificado desde compilacion')
    return path, bundle


def register_backend(argument, source_hash, bundle_argument=None):
    artifact = Path(argument).resolve(strict=True)
    if not artifact.is_relative_to(ROOT / 'qa/artifacts') or artifact.is_symlink():
        raise ValueError('El JAR debe estar en qa/artifacts, nunca en staging ni custodia')
    profile = 'BOOT-INF/classes/application-qa-local.properties'
    with zipfile.ZipFile(artifact) as jar:
        if jar.read(profile) != (ROOT / 'dima-bug-backend/src/main/resources/application-qa-local.properties').read_bytes():
            raise ValueError('El JAR no contiene el perfil QA actual')
        for name in ['identidad/application/service/DespacharCodigosService', 'correo/infrastructure/gmail/CorreoGmailTransporte']:
            if 'BOOT-INF/classes/cl/casol/backend/' + name + '.class' not in jar.namelist():
                raise ValueError('No corresponde al flujo local nuevo')
    result = {'path': artifact.relative_to(ROOT).as_posix(),
            'sha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
            'source_sha256_at_registration': source_hash,
            'provenance': 'registration_after_separately_authorized_build; not proof of reproducible build'}
    if bundle_argument:
        path, bundle = validate_bundle(bundle_argument)
        if (ROOT/bundle['files']['backend']['path']).resolve() != artifact:
            raise ValueError('JAR y paquete no coinciden')
        result['bundle'] = {'path':path.relative_to(ROOT).as_posix(), 'sha256':hashlib.sha256(path.read_bytes()).hexdigest()}
    return result


def main():
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    parser.add_argument('--replace', action='store_true')
    parser.add_argument('--backend-artifact')
    parser.add_argument('--backend-bundle')
    args = parser.parse_args()
    if args.check and (args.replace or args.backend_artifact or args.backend_bundle):
        raise ValueError('La comprobación no modifica ni registra artefactos')
    files = records()
    source = fingerprint(files)
    head = git('rev-parse', 'HEAD').decode().strip()
    branch = git('branch', '--show-current').decode().strip()
    if args.check:
        saved = json.loads(MANIFEST.read_text(encoding='utf-8'))
        differences = changed(saved['files'], files)
        if differences or saved['source_sha256'] != source or saved['base_commit'] != head or saved['branch'] != branch:
            print(json.dumps({'status': 'CANDIDATE_CHANGED', 'paths': differences}))
            return 1
        backend = saved['artifacts']['backend']
        if backend:
            path = (ROOT / backend['path']).resolve(strict=True)
            if not path.is_relative_to(ROOT / 'qa/artifacts') or hashlib.sha256(path.read_bytes()).hexdigest() != backend['sha256'] or backend['source_sha256_at_registration'] != source:
                raise ValueError('Artefacto no coincide con el registro')
            if backend.get('bundle'):
                bundle_path = ROOT/backend['bundle']['path']
                if hashlib.sha256(bundle_path.read_bytes()).hexdigest() != backend['bundle']['sha256']:
                    raise ValueError('Paquete modificado')
                validate_bundle(bundle_path)
        print(json.dumps({'status': 'CANDIDATE_SOURCE_OK', 'candidate': saved['candidate_id'],
                          'backend_registered': bool(backend), 'does_not_authorize_start': True}))
        return 0
    if MANIFEST.exists() and not args.replace:
        raise FileExistsError('Manifiesto existente; revisar cambios antes de reemplazar')
    if args.backend_bundle and not args.backend_artifact:
        raise ValueError('Falta el JAR del paquete')
    backend = register_backend(args.backend_artifact, source, args.backend_bundle) if args.backend_artifact else None
    document = {'format': 'dimabug-qa-candidate-v1', 'branch': branch, 'base_commit': head,
        'candidate_id': 'QA-local-' + head[:7] + '-' + source[:12], 'source_sha256': source,
        'hash_rule': 'sorted repository-relative path + NUL + raw SHA256(file bytes) + NUL; manifest excluded',
        'files': files, 'baseline_classification': 'qa/baseline-pendientes.json',
        'artifacts': {'backend': backend, 'frontend': None},
        'toolchain': {'python_observed': platform.python_version(), 'java_required': '21',
                      'spring_boot_declared': '4.1.1', 'node_npm_mysql_observed': None},
        'pending': ['approve scope of baseline', 'isolated MySQL/schema/user', 'QA credentials',
                    'authorized builds and runtime tool versions', 'synthetic fixtures', 'runtime isolation validation',
                    'functional tests; no release approval']}
    if not MANIFEST.exists():
        with MANIFEST.open('x', encoding='utf-8') as stream:
            stream.write(json.dumps(document, ensure_ascii=False, indent=2) + '\n')
    else:
        MANIFEST.write_text(json.dumps(document, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': 'MANIFEST_WRITTEN', 'candidate': document['candidate_id'], 'files': len(files), 'applications_started': 0}))
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except Exception as exc:
        # No volcar configuración ni excepciones de proveedores externos.
        print(json.dumps({'status': 'MANIFEST_NOT_CONFIRMED', 'category': type(exc).__name__}))
        raise SystemExit(1)
